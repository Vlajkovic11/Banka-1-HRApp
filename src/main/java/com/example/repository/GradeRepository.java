package com.example.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsible for all CRUD operations on the {@code grades} table.
 * Grades are append-only; individual grades are never modified or deleted.
 */
public class GradeRepository {

    private static final Logger log = LoggerFactory.getLogger(GradeRepository.class);

    private final DatabaseManager dbManager;

    /**
     * Constructs the repository with the required database manager (constructor injection).
     *
     * @param dbManager the shared database manager
     */
    public GradeRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Inserts a new grade for the given member.
     *
     * @param grade    the grade value (must be within the configured min/max range)
     * @param memberId the owning member's database ID
     * @throws SQLException on database error
     */
    public void save(int grade, long memberId) throws SQLException {
        String sql = "INSERT INTO grades (grade, member_id) VALUES (?, ?)";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, grade);
            ps.setLong(2, memberId);
            ps.executeUpdate();
        }
        log.info("Saved grade={} for member id={}", grade, memberId);
    }

    /**
     * Returns all grades for the given member in insertion order.
     *
     * @param memberId the owning member's database ID
     * @return list of grade values
     * @throws SQLException on database error
     */
    public List<Integer> findByMemberId(long memberId) throws SQLException {
        String sql = "SELECT grade FROM grades WHERE member_id = ? ORDER BY id";
        List<Integer> grades = new ArrayList<>();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    grades.add(rs.getInt("grade"));
                }
            }
        }
        return grades;
    }

    /**
     * Returns all grades for the given member as {@code int[]{id, grade}} pairs, in insertion order.
     *
     * @param memberId the owning member's database ID
     * @return list of two-element arrays where index 0 is the row id and index 1 is the grade value
     * @throws SQLException on database error
     */
    public List<int[]> findWithIdsByMemberId(long memberId) throws SQLException {
        String sql = "SELECT id, grade FROM grades WHERE member_id = ? ORDER BY id";
        List<int[]> result = new ArrayList<>();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new int[]{rs.getInt("id"), rs.getInt("grade")});
                }
            }
        }
        return result;
    }

    /**
     * Deletes a grade row by its primary key.
     *
     * @param id the grade row id
     * @throws SQLException on database error
     */
    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM grades WHERE id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        log.info("Deleted grade id={}", id);
    }

    /**
     * Updates the value of an existing grade row.
     *
     * @param id    the grade row id
     * @param grade the new grade value
     * @throws SQLException on database error
     */
    public void updateById(int id, int grade) throws SQLException {
        String sql = "UPDATE grades SET grade = ? WHERE id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, grade);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
        log.info("Updated grade id={} to value={}", id, grade);
    }
}
