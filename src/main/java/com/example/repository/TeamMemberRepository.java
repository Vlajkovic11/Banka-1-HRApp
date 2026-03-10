package com.example.repository;

import com.example.dto.TaskDTO;
import com.example.dto.TeamMemberDTO;
import com.example.model.Task;
import com.example.model.TaskStatus;
import com.example.model.TeamMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository responsible for all CRUD operations on the {@code team_members} table.
 * Uses soft-delete: rows are never physically removed; {@code is_deleted = 1} marks them hidden.
 */
public class TeamMemberRepository {

    private static final Logger log = LoggerFactory.getLogger(TeamMemberRepository.class);

    private final DatabaseManager dbManager;

    /**
     * Constructs the repository with the required database manager (constructor injection).
     *
     * @param dbManager the shared database manager
     */
    public TeamMemberRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Inserts a new team member and populates the generated {@code id} on the entity.
     *
     * @param member the member to insert (id will be set after save)
     * @throws SQLException on database error
     */
    public void save(TeamMember member) throws SQLException {
        String sql = "INSERT INTO team_members (name, surname) VALUES (?, ?)";
        try (PreparedStatement ps = dbManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, member.getName());
            ps.setString(2, member.getSurname());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    member.setId(keys.getLong(1));
                }
            }
        }
        log.info("Saved team member id={} ({} {})", member.getId(), member.getName(), member.getSurname());
    }

    /**
     * Updates an existing team member's name and surname.
     *
     * @param member the member with updated values (must have a valid id)
     * @throws SQLException on database error
     */
    public void update(TeamMember member) throws SQLException {
        String sql = "UPDATE team_members SET name = ?, surname = ? WHERE id = ? AND is_deleted = 0";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, member.getName());
            ps.setString(2, member.getSurname());
            ps.setLong(3, member.getId());
            ps.executeUpdate();
        }
        log.info("Updated team member id={}", member.getId());
    }

    /**
     * Soft-deletes a team member by setting {@code is_deleted = 1}.
     * The row remains in the database; cascade soft-delete of tasks is handled separately.
     *
     * @param id the member's database ID
     * @throws SQLException on database error
     */
    public void softDelete(long id) throws SQLException {
        String sql = "UPDATE team_members SET is_deleted = 1 WHERE id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        log.info("Soft-deleted team member id={}", id);
    }

    /**
     * Returns all non-deleted team members ordered by surname then name.
     *
     * @return list of team members (without their related tasks/skills/grades)
     * @throws SQLException on database error
     */
    public List<TeamMember> findAll() throws SQLException {
        String sql = "SELECT id, name, surname FROM team_members WHERE is_deleted = 0 ORDER BY surname, name";
        List<TeamMember> members = new ArrayList<>();
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TeamMember m = new TeamMember(rs.getString("name"), rs.getString("surname"));
                m.setId(rs.getLong("id"));
                members.add(m);
            }
        }
        return members;
    }

    /**
     * Finds a single non-deleted team member by ID.
     *
     * @param id the member's database ID
     * @return an {@link Optional} containing the member, or empty if not found
     * @throws SQLException on database error
     */
    public Optional<TeamMember> findById(long id) throws SQLException {
        String sql = "SELECT id, name, surname FROM team_members WHERE id = ? AND is_deleted = 0";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    TeamMember m = new TeamMember(rs.getString("name"), rs.getString("surname"));
                    m.setId(rs.getLong("id"));
                    return Optional.of(m);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns all non-deleted team members with their full related data (tasks, skills, grades).
     * Uses JOINs to fetch everything in a single query, avoiding N+1 problem.
     * Processes results to group tasks, skills, and grades per member.
     *
     * @return list of team member DTOs fully populated with related data
     * @throws SQLException on database error
     */
    public List<TeamMemberDTO> findAllWithDetails() throws SQLException {
        // SQL with LEFT JOINs to get all data in one query
        String sql = "SELECT tm.id, tm.name, tm.surname, " +
                "       t.id AS task_id, t.task_name, t.comment, t.status, " +
                "       s.skill_name, " +
                "       g.id AS grade_id, g.grade " +
                "FROM team_members tm " +
                "LEFT JOIN tasks t ON tm.id = t.member_id AND t.is_deleted = 0 " +
                "LEFT JOIN skills s ON tm.id = s.member_id " +
                "LEFT JOIN grades g ON tm.id = g.member_id " +
                "WHERE tm.is_deleted = 0 " +
                "ORDER BY tm.surname, tm.name, t.id, s.skill_name, g.id";

        // Map to collect data per member
        Map<Long, MemberData> memberDataMap = new LinkedHashMap<>();

        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                long memberId = rs.getLong("id");
                String name = rs.getString("name");
                String surname = rs.getString("surname");

                // Get or create member data holder
                MemberData data = memberDataMap.computeIfAbsent(memberId, k ->
                        new MemberData(memberId, name, surname));

                // Add task if present (not null when no tasks exist)
                long taskId = rs.getLong("task_id");
                if (taskId != 0) {
                    String taskName = rs.getString("task_name");
                    if (data.tasks.stream().noneMatch(t -> t.getId() == taskId)) {
                        Task task = new Task(taskName);
                        task.setId(taskId);
                        task.setComment(rs.getString("comment"));
                        task.setStatus(TaskStatus.valueOf(rs.getString("status")));
                        data.tasks.add(task);
                    }
                }

                // Add skill if present
                String skillName = rs.getString("skill_name");
                if (skillName != null && !data.skills.contains(skillName)) {
                    data.skills.add(skillName);
                }

                int gradeId = rs.getInt("grade_id");
                int grade = rs.getInt("grade");

                // Check if gradeId is valid and grade is not null
                if (gradeId != 0 && !rs.wasNull()) {

                    data.grades.add(grade);

                    // Add to gradeEntries if not already added
                    boolean alreadyExists = data.gradeEntries.stream()
                            .anyMatch(e -> e[0] == gradeId);
                    if (!alreadyExists) {
                        data.gradeEntries.add(new int[]{gradeId, grade});
                    }
                }
            }
        }

        // Convert to DTOs
        return memberDataMap.values().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converts aggregated member data to a DTO.
     */
    private TeamMemberDTO convertToDTO(MemberData data) {
        double avg = data.grades.isEmpty()
                ? 0
                : data.grades.stream().mapToInt(Integer::intValue).average().orElse(0);

        List<TaskDTO> taskDTOs = data.tasks.stream()
                .map(t -> new TaskDTO(t.getId(), t.getTaskName(), t.getStatus(), t.getComment()))
                .collect(Collectors.toList());

        return new TeamMemberDTO(data.id, data.name, data.surname, avg, taskDTOs, data.skills, data.grades, data.gradeEntries);
    }

    /**
     * Helper class to aggregate member data from JOIN results.
     */
    private static class MemberData {
        long id;
        String name;
        String surname;
        List<Task> tasks = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        List<Integer> grades = new ArrayList<>();
        List<int[]> gradeEntries = new ArrayList<>();

        MemberData(long id, String name, String surname) {
            this.id = id;
            this.name = name;
            this.surname = surname;
        }
    }
}
