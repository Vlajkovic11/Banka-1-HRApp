package com.example.integration;


import com.example.dto.CreateUpdateMemberDTO;
import com.example.dto.TeamMemberDTO;
import com.example.exception.HRAppException;
import com.example.exception.MemberNotFoundException;
import com.example.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link com.example.service.TeamMemberService}.
 * Uses a real in-memory SQLite database — no mocks.
 */
class TeamMemberServiceIntegrationTest extends IntegrationTestBase {

    // ── createMember ─────────────────────────────────────────────────────────

    @Test
    void createMember_persistsAndReturnsDTO() {
        TeamMemberDTO dto = memberService.createMember(CreateUpdateMemberDTO.of("Ana", "Jovic"));

        assertTrue(dto.getId() > 0);
        assertEquals("Ana",   dto.getName());
        assertEquals("Jovic", dto.getSurname());
        assertEquals(0.0,     dto.getAverageGrade());
        assertTrue(dto.getTasks().isEmpty());
        assertTrue(dto.getSkills().isEmpty());
        assertTrue(dto.getGrades().isEmpty());
    }

    @Test
    void createMember_withBlankName_throwsValidationException() {
        assertThrows(ValidationException.class,
                () -> memberService.createMember(CreateUpdateMemberDTO.of("", "Jovic")));
    }

    @Test
    void createMember_withBlankSurname_throwsValidationException() {
        assertThrows(ValidationException.class,
                () -> memberService.createMember(CreateUpdateMemberDTO.of("Ana", "  ")));
    }

    // ── getAllMembers ─────────────────────────────────────────────────────────

    @Test
    void getAllMembers_whenEmpty_returnsEmptyList() {
        assertTrue(memberService.getAllMembers().isEmpty());
    }

    @Test
    void getAllMembers_returnsSavedMembers() {
        memberService.createMember(CreateUpdateMemberDTO.of("Marko", "Petrovic"));
        memberService.createMember(CreateUpdateMemberDTO.of("Jelena", "Nikolic"));

        List<TeamMemberDTO> all = memberService.getAllMembers();

        assertEquals(2, all.size());
    }

    @Test
    void getAllMembers_orderedBySurnameThenName() {
        memberService.createMember(CreateUpdateMemberDTO.of("Zoran",  "Zoric"));
        memberService.createMember(CreateUpdateMemberDTO.of("Ana",    "Antic"));
        memberService.createMember(CreateUpdateMemberDTO.of("Milica", "Antic"));

        List<TeamMemberDTO> all = memberService.getAllMembers();

        assertEquals("Antic", all.get(0).getSurname());
        assertEquals("Ana",   all.get(0).getName());
        assertEquals("Antic", all.get(1).getSurname());
        assertEquals("Milica",all.get(1).getName());
        assertEquals("Zoric", all.get(2).getSurname());
    }

    // ── getMemberById ─────────────────────────────────────────────────────────

    @Test
    void getMemberById_returnsCorrectMember() {
        TeamMemberDTO created = memberService.createMember(CreateUpdateMemberDTO.of("Nikola", "Tesla"));

        TeamMemberDTO found = memberService.getMemberById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("Nikola", found.getName());
        assertEquals("Tesla",  found.getSurname());
    }

    @Test
    void getMemberById_withNonExistentId_throwsMemberNotFoundException() {
        assertThrows(MemberNotFoundException.class, () -> memberService.getMemberById(9999L));
    }

    // ── updateMember ─────────────────────────────────────────────────────────

    @Test
    void updateMember_changesNameAndSurname() {
        TeamMemberDTO created = memberService.createMember(CreateUpdateMemberDTO.of("Old", "Name"));

        TeamMemberDTO updated = memberService.updateMember(created.getId(),
                CreateUpdateMemberDTO.of("New", "Name"));

        assertEquals("New",  updated.getName());
        assertEquals("Name", updated.getSurname());
        // Verify the change is persisted
        TeamMemberDTO reloaded = memberService.getMemberById(created.getId());
        assertEquals("New", reloaded.getName());
    }

    @Test
    void updateMember_withNonExistentId_throwsMemberNotFoundException() {
        assertThrows(MemberNotFoundException.class,
                () -> memberService.updateMember(9999L, CreateUpdateMemberDTO.of("X", "Y")));
    }

    // ── deleteMember ─────────────────────────────────────────────────────────

    @Test
    void deleteMember_softDeletesAndHidesFromFindAll() {
        TeamMemberDTO created = memberService.createMember(CreateUpdateMemberDTO.of("Delete", "Me"));

        memberService.deleteMember(created.getId());

        assertTrue(memberService.getAllMembers().isEmpty());
    }

    @Test
    void deleteMember_alsoSoftDeletesTheirTasks() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Task", "Owner"));
        taskService.addTask(member.getId(),
                com.example.dto.CreateUpdateTaskDTO.of("My task", "", com.example.model.TaskStatus.PENDING));

        memberService.deleteMember(member.getId());

        // Tasks should be gone along with the member
        assertTrue(taskService.getTasksForMember(member.getId()).isEmpty());
    }

    // ── addSkill / removeSkill ────────────────────────────────────────────────

    @Test
    void addSkill_normalisesAndPersists() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Skill", "User"));

        memberService.addSkill(member.getId(), "java");

        TeamMemberDTO reloaded = memberService.getMemberById(member.getId());
        assertTrue(reloaded.getSkills().contains("JAVA"));
    }

    @Test
    void addSkill_duplicateIsIgnored() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Dup", "Skill"));

        memberService.addSkill(member.getId(), "JAVA");
        memberService.addSkill(member.getId(), "java"); // duplicate

        TeamMemberDTO reloaded = memberService.getMemberById(member.getId());
        assertEquals(1, reloaded.getSkills().size());
    }

    @Test
    void addSkill_withBlankName_throwsValidationException() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("A", "B"));
        assertThrows(ValidationException.class, () -> memberService.addSkill(member.getId(), "  "));
    }

    @Test
    void removeSkill_removesPersistedSkill() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Remove", "Skill"));
        memberService.addSkill(member.getId(), "PYTHON");

        memberService.removeSkill(member.getId(), "PYTHON");

        TeamMemberDTO reloaded = memberService.getMemberById(member.getId());
        assertFalse(reloaded.getSkills().contains("PYTHON"));
    }

    // ── addGrade / removeGrade / updateGrade ──────────────────────────────────

    @Test
    void addGrade_persists_andAverageIsCorrect() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Grade", "User"));

        memberService.addGrade(member.getId(), 8);
        memberService.addGrade(member.getId(), 6);

        TeamMemberDTO reloaded = memberService.getMemberById(member.getId());
        assertEquals(2, reloaded.getGrades().size());
        assertEquals(7.0, reloaded.getAverageGrade(), 0.001);
    }

    @Test
    void addGrade_tooHigh_throwsValidationException() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("A", "B"));
        assertThrows(ValidationException.class, () -> memberService.addGrade(member.getId(), 11));
    }

    @Test
    void addGrade_tooLow_throwsValidationException() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("A", "B"));
        assertThrows(ValidationException.class, () -> memberService.addGrade(member.getId(), 0));
    }

    @Test
    void removeGrade_removesFromHistory() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Grade", "Remove"));
        memberService.addGrade(member.getId(), 9);

        TeamMemberDTO afterAdd = memberService.getMemberById(member.getId());
        int gradeId = afterAdd.getGradeEntries().get(0)[0];

        memberService.removeGrade(gradeId);

        TeamMemberDTO afterRemove = memberService.getMemberById(member.getId());
        assertTrue(afterRemove.getGrades().isEmpty());
    }

    @Test
    void updateGrade_changesValue() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Grade", "Update"));
        memberService.addGrade(member.getId(), 5);

        TeamMemberDTO afterAdd = memberService.getMemberById(member.getId());
        int gradeId = afterAdd.getGradeEntries().get(0)[0];

        memberService.updateGrade(gradeId, 9);

        TeamMemberDTO afterUpdate = memberService.getMemberById(member.getId());
        assertEquals(9, afterUpdate.getGrades().get(0));
    }

    @Test
    void updateGrade_outOfRange_throwsValidationException() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("A", "B"));
        memberService.addGrade(member.getId(), 5);
        TeamMemberDTO afterAdd = memberService.getMemberById(member.getId());
        int gradeId = afterAdd.getGradeEntries().get(0)[0];

        assertThrows(ValidationException.class, () -> memberService.updateGrade(gradeId, 0));
        assertThrows(ValidationException.class, () -> memberService.updateGrade(gradeId, 11));
    }

    // ── getAllMembers with full details (findAllWithDetails path) ─────────────

    @Test
    void getAllMembers_includesTasksSkillsAndGrades() {
        TeamMemberDTO member = memberService.createMember(CreateUpdateMemberDTO.of("Full", "Details"));
        taskService.addTask(member.getId(),
                com.example.dto.CreateUpdateTaskDTO.of("Implement feature", "PR #1", com.example.model.TaskStatus.PENDING));
        memberService.addSkill(member.getId(), "JAVA");
        memberService.addGrade(member.getId(), 7);

        List<TeamMemberDTO> all = memberService.getAllMembers();

        assertEquals(1, all.size());
        TeamMemberDTO dto = all.get(0);
        assertEquals(1, dto.getTasks().size());
        assertEquals("Implement feature", dto.getTasks().get(0).getTaskName());
        assertEquals(com.example.model.TaskStatus.PENDING, dto.getTasks().get(0).getStatus());
        assertTrue(dto.getSkills().contains("JAVA"));
        assertEquals(7.0, dto.getAverageGrade(), 0.001);
    }
}

