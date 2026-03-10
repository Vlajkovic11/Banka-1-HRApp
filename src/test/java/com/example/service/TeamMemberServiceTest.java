package com.example.service;

import com.example.dto.CreateUpdateMemberDTO;
import com.example.dto.TeamMemberDTO;
import com.example.exception.HRAppException;
import com.example.exception.MemberNotFoundException;
import com.example.exception.ValidationException;
import com.example.model.TeamMember;
import com.example.repository.GradeRepository;
import com.example.repository.SkillRepository;
import com.example.repository.TaskRepository;
import com.example.repository.TeamMemberRepository;
import com.example.repository.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TeamMemberService}.
 * All repository interactions are mocked — no real database is used.
 */
@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    @Mock private TeamMemberRepository memberRepo;
    @Mock private TaskRepository       taskRepo;
    @Mock private SkillRepository      skillRepo;
    @Mock private GradeRepository      gradeRepo;
    @Mock private TransactionManager   txManager;

    private TeamMemberService service;

    /** Sets up the service with mocked repositories before each test. */
    @BeforeEach
    void setUp() {
        service = new TeamMemberService(memberRepo, taskRepo, skillRepo, gradeRepo, txManager);
    }

    // ── createMember ──────────────────────────────────────────────────────────

    @Test
    void createMember_withValidData_persistsMemberAndReturnsDTO() throws SQLException {
        doAnswer(inv -> { ((TeamMember) inv.getArgument(0)).setId(1L); return null; })
                .when(memberRepo).save(any());
        when(taskRepo.findByMemberId(anyLong())).thenReturn(Collections.emptyList());
        when(skillRepo.findByMemberId(anyLong())).thenReturn(Collections.emptyList());
        when(gradeRepo.findByMemberId(anyLong())).thenReturn(Collections.emptyList());
        when(gradeRepo.findWithIdsByMemberId(anyLong())).thenReturn(Collections.emptyList());

        TeamMemberDTO result = service.createMember(CreateUpdateMemberDTO.of("Ana", "Jovic"));

        assertEquals("Ana",   result.getName());
        assertEquals("Jovic", result.getSurname());
        assertEquals(1L,      result.getId());
        verify(memberRepo).save(any(TeamMember.class));
    }

    @Test
    void createMember_withBlankName_throwsValidationException() {
        assertThrows(ValidationException.class,
                () -> service.createMember(CreateUpdateMemberDTO.of("", "Jovic")));
    }

    @Test
    void createMember_withBlankSurname_throwsValidationException() {
        assertThrows(ValidationException.class,
                () -> service.createMember(CreateUpdateMemberDTO.of("Ana", "  ")));
    }

    // ── getAllMembers ─────────────────────────────────────────────────────────

    @Test
    void getAllMembers_whenNoMembers_returnsEmptyList() throws SQLException {
        when(memberRepo.findAllWithDetails()).thenReturn(Collections.emptyList());

        List<TeamMemberDTO> result = service.getAllMembers();

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllMembers_returnsAllNonDeletedMembers() throws SQLException {
        TeamMemberDTO dto = new TeamMemberDTO(5L, "Marko", "Petrovic", 0.0,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        when(memberRepo.findAllWithDetails()).thenReturn(List.of(dto));

        List<TeamMemberDTO> result = service.getAllMembers();

        assertEquals(1, result.size());
        assertEquals("Marko", result.get(0).getName());
    }

    // ── updateMember ──────────────────────────────────────────────────────────

    @Test
    void updateMember_withValidData_updatesAndReturnsDTO() throws SQLException {
        TeamMember existing = new TeamMember("Old", "Name");
        existing.setId(2L);
        when(memberRepo.findById(2L)).thenReturn(Optional.of(existing));
        when(taskRepo.findByMemberId(2L)).thenReturn(Collections.emptyList());
        when(skillRepo.findByMemberId(2L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findByMemberId(2L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findWithIdsByMemberId(2L)).thenReturn(Collections.emptyList());

        TeamMemberDTO result = service.updateMember(2L, CreateUpdateMemberDTO.of("New", "Name"));

        assertEquals("New", result.getName());
        verify(memberRepo).update(existing);
    }

    // ── deleteMember ──────────────────────────────────────────────────────────

    @Test
    void deleteMember_softDeletesMemberAndTasksWithinTransaction() throws SQLException {
        service.deleteMember(3L);

        verify(txManager).beginTransaction();
        verify(taskRepo).softDeleteByMemberId(3L);
        verify(memberRepo).softDelete(3L);
        verify(txManager).commit();
        verify(txManager, never()).rollback();
    }

    @Test
    void deleteMember_onSQLException_rollsBackAndThrowsHRAppException() throws SQLException {
        doThrow(new SQLException("db error")).when(memberRepo).softDelete(anyLong());

        assertThrows(HRAppException.class, () -> service.deleteMember(1L));

        verify(txManager).beginTransaction();
        verify(txManager).rollback();
        verify(txManager, never()).commit();
    }

    // ── addGrade ──────────────────────────────────────────────────────────────

    @Test
    void addGrade_withValidGrade_savesGrade() throws SQLException {
        service.addGrade(1L, 8);

        verify(gradeRepo).save(8, 1L);
    }

    @Test
    void addGrade_withGradeTooHigh_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.addGrade(1L, 11));
    }

    @Test
    void addGrade_withGradeTooLow_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.addGrade(1L, 0));
    }

    // ── addSkill ──────────────────────────────────────────────────────────────

    @Test
    void addSkill_withBlankName_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.addSkill(1L, "  "));
    }

    @Test
    void addSkill_normalisesToUppercase() throws SQLException {
        service.addSkill(1L, "java");

        verify(skillRepo).save("JAVA", 1L);
    }

    // ── removeSkill ───────────────────────────────────────────────────────────

    @Test
    void removeSkill_delegatesToRepository() throws SQLException {
        service.removeSkill(1L, "JAVA");

        verify(skillRepo).delete("JAVA", 1L);
    }

    // ── removeGrade ───────────────────────────────────────────────────────────

    @Test
    void removeGrade_delegatesToRepository() throws SQLException {
        service.removeGrade(42);

        verify(gradeRepo).deleteById(42);
    }

    // ── updateGrade ───────────────────────────────────────────────────────────

    @Test
    void updateGrade_withValidGrade_updatesGrade() throws SQLException {
        service.updateGrade(42, 7);

        verify(gradeRepo).updateById(42, 7);
    }

    @Test
    void updateGrade_withMinGrade_updatesGrade() throws SQLException {
        service.updateGrade(1, 1);

        verify(gradeRepo).updateById(1, 1);
    }

    @Test
    void updateGrade_withMaxGrade_updatesGrade() throws SQLException {
        service.updateGrade(1, 10);

        verify(gradeRepo).updateById(1, 10);
    }

    @Test
    void updateGrade_withGradeTooHigh_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.updateGrade(1, 11));
    }

    @Test
    void updateGrade_withGradeTooLow_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.updateGrade(1, 0));
    }

    @Test
    void updateGrade_withTooHighGrade_doesNotCallRepository() throws SQLException {
        assertThrows(ValidationException.class, () -> service.updateGrade(1, 11));

        verify(gradeRepo, never()).updateById(anyInt(), anyInt());
    }

    @Test
    void updateGrade_withTooLowGrade_doesNotCallRepository() throws SQLException {
        assertThrows(ValidationException.class, () -> service.updateGrade(1, 0));

        verify(gradeRepo, never()).updateById(anyInt(), anyInt());
    }

    // ── getMemberById ─────────────────────────────────────────────────────────

    @Test
    void getMemberById_whenMemberExists_returnsDTO() throws SQLException {
        TeamMember member = new TeamMember("Luka", "Modric");
        member.setId(7L);
        when(memberRepo.findById(7L)).thenReturn(Optional.of(member));
        when(taskRepo.findByMemberId(7L)).thenReturn(Collections.emptyList());
        when(skillRepo.findByMemberId(7L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findByMemberId(7L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findWithIdsByMemberId(7L)).thenReturn(Collections.emptyList());

        TeamMemberDTO result = service.getMemberById(7L);

        assertEquals(7L,      result.getId());
        assertEquals("Luka",  result.getName());
        assertEquals("Modric", result.getSurname());
    }

    @Test
    void getMemberById_whenMemberNotFound_throwsMemberNotFoundException() throws SQLException {
        when(memberRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MemberNotFoundException.class, () -> service.getMemberById(99L));
    }

    @Test
    void getMemberById_onSQLException_throwsHRAppException() throws SQLException {
        when(memberRepo.findById(anyLong())).thenThrow(new SQLException("db error"));

        assertThrows(HRAppException.class, () -> service.getMemberById(1L));
    }

    // ── getAllMembers – SQLException ───────────────────────────────────────────

    @Test
    void getAllMembers_onSQLException_throwsHRAppException() throws SQLException {
        when(memberRepo.findAllWithDetails()).thenThrow(new SQLException("db error"));

        assertThrows(HRAppException.class, () -> service.getAllMembers());
    }

    // ── createMember – SQLException ───────────────────────────────────────────

    @Test
    void createMember_onSQLException_throwsHRAppException() throws SQLException {
        doThrow(new SQLException("db error")).when(memberRepo).save(any());

        assertThrows(HRAppException.class,
                () -> service.createMember(CreateUpdateMemberDTO.of("Ana", "Jovic")));
    }

    // ── updateMember – error paths ────────────────────────────────────────────

    @Test
    void updateMember_whenMemberNotFound_throwsMemberNotFoundException() throws SQLException {
        when(memberRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MemberNotFoundException.class,
                () -> service.updateMember(99L, CreateUpdateMemberDTO.of("New", "Name")));
    }

    @Test
    void updateMember_onSQLException_throwsHRAppException() throws SQLException {
        when(memberRepo.findById(anyLong())).thenThrow(new SQLException("db error"));

        assertThrows(HRAppException.class,
                () -> service.updateMember(1L, CreateUpdateMemberDTO.of("New", "Name")));
    }

    // ── addGrade ──────────────────────────────────────────────────────────────

    @Test
    void addGrade_withMinGrade_savesGrade() throws SQLException {
        service.addGrade(1L, 1);

        verify(gradeRepo).save(1, 1L);
    }

    @Test
    void addGrade_withMaxGrade_savesGrade() throws SQLException {
        service.addGrade(1L, 10);

        verify(gradeRepo).save(10, 1L);
    }

    @Test
    void addGrade_onSQLException_throwsHRAppException() throws SQLException {
        doThrow(new SQLException("db error")).when(gradeRepo).save(anyInt(), anyLong());

        assertThrows(HRAppException.class, () -> service.addGrade(1L, 5));
    }

    // ── addSkill – null & too-long & SQLException ─────────────────────────────

    @Test
    void addSkill_withNullName_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.addSkill(1L, null));
    }

    @Test
    void addSkill_withTooLongName_throwsValidationException() {
        String tooLong = "A".repeat(300);
        assertThrows(ValidationException.class, () -> service.addSkill(1L, tooLong));
    }

    @Test
    void addSkill_onSQLException_throwsHRAppException() throws SQLException {
        doThrow(new SQLException("db error")).when(skillRepo).save(any(), anyLong());

        assertThrows(HRAppException.class, () -> service.addSkill(1L, "java"));
    }

    // ── grade average calculation (via getMemberById / createMember) ──────────

    @Test
    void getMemberById_withNoGrades_returnsAverageZero() throws SQLException {
        TeamMember member = new TeamMember("Test", "User");
        member.setId(10L);
        when(memberRepo.findById(10L)).thenReturn(Optional.of(member));
        when(taskRepo.findByMemberId(10L)).thenReturn(Collections.emptyList());
        when(skillRepo.findByMemberId(10L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findByMemberId(10L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findWithIdsByMemberId(10L)).thenReturn(Collections.emptyList());

        TeamMemberDTO result = service.getMemberById(10L);

        assertEquals(0.0, result.getAverageGrade());
    }

    @Test
    void getMemberById_withSingleGrade_returnsCorrectAverage() throws SQLException {
        TeamMember member = new TeamMember("Test", "User");
        member.setId(11L);
        when(memberRepo.findById(11L)).thenReturn(Optional.of(member));
        when(taskRepo.findByMemberId(11L)).thenReturn(Collections.emptyList());
        when(skillRepo.findByMemberId(11L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findByMemberId(11L)).thenReturn(List.of(7));
        when(gradeRepo.findWithIdsByMemberId(11L)).thenReturn(Collections.emptyList());

        TeamMemberDTO result = service.getMemberById(11L);

        assertEquals(7.0, result.getAverageGrade());
    }

    @Test
    void getMemberById_withMultipleGrades_returnsCorrectAverage() throws SQLException {
        TeamMember member = new TeamMember("Test", "User");
        member.setId(12L);
        when(memberRepo.findById(12L)).thenReturn(Optional.of(member));
        when(taskRepo.findByMemberId(12L)).thenReturn(Collections.emptyList());
        when(skillRepo.findByMemberId(12L)).thenReturn(Collections.emptyList());
        when(gradeRepo.findByMemberId(12L)).thenReturn(List.of(4, 6, 10));
        when(gradeRepo.findWithIdsByMemberId(12L)).thenReturn(Collections.emptyList());

        TeamMemberDTO result = service.getMemberById(12L);

        assertEquals(20.0 / 3.0, result.getAverageGrade(), 0.0001);
    }
}
