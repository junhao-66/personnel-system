package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.AdminUserCreateReqDTO;
import com.personnel.dto.req.AssessmentAnswersReqDTO;
import com.personnel.dto.req.AssessmentStartReqDTO;
import com.personnel.service.*;
import com.personnel.service.impl.AssessmentPdfServiceImpl;
import com.personnel.vo.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE) @Transactional
class AssessmentPdfDatabaseIT {
    @Autowired UserService users;
    @Autowired AssessmentService assessments;
    @Autowired AssessmentScaleService scales;
    @Autowired KnowledgeGraphService graphs;
    @Autowired AssessmentPdfService pdf;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    UserVO user(String role) { return users.createUser(new AdminUserCreateReqDTO("pdf" + UUID.randomUUID().toString().replace("-", "").substring(0, 16), "Fixture123", "PDF测试", null, role, null, null)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode(), user.authVersion()); }
    AssessmentResultVO result(UserVO user) {
        var scale = scales.listPublished().stream().filter(s -> "OEJTS_OPENJUNG_32_V1".equals(s.definition().scoringVersion())).findFirst().orElseThrow();
        var answers = new LinkedHashMap<String, Integer>();
        for (var question : scale.definition().questions()) {
            int dimension = List.of("EI", "SN", "TF", "JP").indexOf(question.dimension());
            answers.put(question.key(), "INTJ".substring(dimension, dimension + 1).equals(question.highPole()) ? 5 : 1);
        }
        var session = assessments.startAssessment(user.id(), new AssessmentStartReqDTO(scale.id(), true));
        return assessments.submitAssessment(user.id(), session.id(), new AssessmentAnswersReqDTO(0, answers));
    }
    String text(byte[] bytes) throws Exception { try (var document = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(document); } }

    @Test void pdfIncludesChineseFrozenEvidenceSourcesAndPageNumbers() throws Exception {
        var employee = user("EMPLOYEE"); var result = result(employee);
        byte[] bytes = mvc.perform(get("/api/assessment/results/" + result.id() + "/pdf").header("Authorization", bearer(employee)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"assessment-report-" + result.id() + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(bytes);
        assertThat(text).contains("人格偏好报告", "INTJ", "四维偏好得分", "成长行动计划", "知识图谱解释路径", "CC BY-NC-SA 4.0", "OpenJung", "测评局限与数据使用", result.report().reportVersion());
        assertThat(text).doesNotContain("null", "NaN");
        for (var plan : result.report().growthPlans()) assertThat(text.replaceAll("\\s", "")).contains(plan.action().replaceAll("\\s", ""));
        for (var match : result.report().graphExplanation().matches()) assertThat(text).contains(match.ruleId());
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for (int index = 1; index <= document.getNumberOfPages(); index++) assertThat(text).contains("第 " + index + " / " + document.getNumberOfPages() + " 页");
        }
        Path sample = Path.of("../output/pdf/assessment-report-example.pdf"); Files.createDirectories(sample.getParent()); Files.write(sample, bytes);
    }

    @Test void ownerAdminOnlyAndCurrentGraphChangesDoNotRewritePdf() throws Exception {
        var owner = user("EMPLOYEE"); var other = user("EMPLOYEE"); var manager = user("DEPARTMENT_MANAGER"); var admin = user("ADMIN");
        var result = result(owner); String path = "/api/assessment/results/" + result.id() + "/pdf";
        String frozen = text(pdf.exportReport(owner.id(), result.id()));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (var stranger : List.of(other, manager)) mvc.perform(get(path).header("Authorization", bearer(stranger))).andExpect(jsonPath("$.code").value(400));
        var adminBytes = mvc.perform(get(path).header("Authorization", bearer(admin))).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        assertThat(text(adminBytes)).isEqualTo(frozen);
        var current = graphs.getCurrent(); graphs.archiveVersion(admin.id(), current.id());
        assertThat(text(pdf.exportReport(owner.id(), result.id()))).isEqualTo(frozen);
    }

    @Test void legacyReportsAndLongChineseTextPaginateWithoutSynthesizingNewFields() throws Exception {
        var owner = user("EMPLOYEE"); var current = result(owner);
        var report = new AssessmentReportVO("LEGACY_V1", current.report().disclaimer(), List.of("中文长段落测试。".repeat(600)),
                List.of("旧报告优势"), List.of(), List.of(), List.of(), List.of("旧版成长建议"), "原始来源依据", "原始局限", null, null);
        var legacy = new AssessmentResultVO(current.id(), current.userId(), current.scaleName(), current.scaleVersion(), current.scoringVersion(), current.contentHash(), current.typeCode(), current.submittedAt(), current.dimensions(), report, current.scale());
        jdbc.update("UPDATE assessment_session SET result_json=? WHERE id=?", json.writeValueAsString(legacy), current.id());
        byte[] bytes = pdf.exportReport(owner.id(), current.id());
        String text = text(bytes);
        assertThat(text).contains("LEGACY_V1", "旧版成长建议", "原始来源依据", "原始局限");
        assertThat(text).doesNotContain("成长行动计划", "当次建议模板", "null");
        try (var document = Loader.loadPDF(bytes)) { assertThat(document.getNumberOfPages()).isGreaterThan(3); }
    }

    @Test void missingFontHasActionableErrorAndPermissionIsCheckedFirst() {
        var owner = user("EMPLOYEE"); var other = user("EMPLOYEE"); var result = result(owner);
        var exporter = new AssessmentPdfServiceImpl(assessments, "missing-test-font.ttf");
        assertThatThrownBy(() -> exporter.exportReport(owner.id(), result.id())).hasMessageContaining("PDF中文字体不可用");
        assertThatThrownBy(() -> exporter.exportReport(other.id(), result.id())).hasMessageContaining("无权访问");
    }

    @Test void exportHasDedicatedRateLimitAndDraftsHaveNoPdf() throws Exception {
        var owner = user("EMPLOYEE");
        var scale = scales.listPublished().get(0);
        var draft = assessments.startAssessment(owner.id(), new AssessmentStartReqDTO(scale.id(), true));
        String path = "/api/assessment/results/" + draft.id() + "/pdf";
        for (int index = 0; index < 10; index++) mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(get("/api/assessment/current").header("Authorization", bearer(owner))).andExpect(jsonPath("$.data.id").value(draft.id()));
    }
}
