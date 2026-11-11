package com.personnel.service.impl;

import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.ReportPdfDocument;
import com.personnel.service.AssessmentPdfService;
import com.personnel.service.AssessmentService;
import com.personnel.vo.KnowledgeGraphVO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Export only the authorized, persisted snapshot. No graph/template/score recomputation. */
@Service
public class AssessmentPdfServiceImpl implements AssessmentPdfService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentPdfServiceImpl.class);
    private final AssessmentService assessments;
    private final String fontFile;

    public AssessmentPdfServiceImpl(
        AssessmentService assessments,
        @Value("${personnel.report.pdf-font:}") String fontFile
    ) {
        this.assessments = assessments;
        this.fontFile = fontFile;
    }

    private Path font() {
        if (!fontFile.isBlank()) {
            var path = Path.of(fontFile);
            if (Files.isRegularFile(path)) return path;
            throw new BusinessException("PDF中文字体不可用，请联系管理员检查字体配置");
        }
        for (String candidate : List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansCJKsc-Regular.ttf"
        )) {
            if (Files.isRegularFile(Path.of(candidate))) return Path.of(candidate);
        }
        throw new BusinessException(
            "尚未配置PDF中文字体，请设置PERSONNEL_REPORT_PDF_FONT为可嵌入的中文TTF字体路径"
        );
    }

    private void section(ReportPdfDocument pdf, String heading, List<String> values)
        throws IOException {
        if (values == null || values.isEmpty()) return;
        pdf.heading(heading);
        for (String value : values) pdf.body("- " + value);
    }

    @Override
    public byte[] exportReport(Long actorId, Long assessmentId) {
        var result = assessments.getResult(actorId, assessmentId); // Same owner/admin checks as the on-screen report.
        var report = result.report();
        try (var pdf = new ReportPdfDocument(font())) {
            pdf.title("人格偏好报告 / " + result.typeCode());
            pdf.body(result.scaleName() + " / " + result.scaleVersion());
            pdf.note(
                "记录编号：" +
                result.id() +
                "；用户编号：" +
                result.userId() +
                "；提交时间：" +
                result.submittedAt().toString().replace('T', ' ')
            );
            pdf.heading("使用说明");
            pdf.body(report.disclaimer());
            pdf.heading("四维偏好得分");
            pdf.note("两端合计100，仅为归一化偏好刻度，不是能力、概率或优劣。接近50时倾向较弱。");
            for (var dimension : result.dimensions()) {
                pdf.dimension(
                    dimension.dimension().substring(0, 1) + " " + dimension.firstPercent() + "%",
                    dimension.dimension().substring(1) + " " + dimension.secondPercent() + "%",
                    dimension.firstPercent().floatValue(),
                    dimension.balanced()
                        ? "恰好均衡，类型字母仅按约定编码"
                        : dimension.weakPreference()
                            ? "倾向较弱，请结合情境理解"
                            : "本次可能更偏好 " + dimension.preferredPole()
                );
            }
            section(pdf, "人格偏好解释", report.preferenceSummary());
            section(pdf, "优势倾向", report.strengths());
            section(pdf, "待发展方向", report.developmentDirections());
            section(pdf, "工作方式偏好", report.workPreferences());
            section(pdf, "职业发展建议", report.careerSuggestions());
            if (report.growthPlans() == null || report.growthPlans().isEmpty()) section(
                pdf,
                "成长行动建议",
                report.growthSuggestions()
            );
            else {
                pdf.heading("成长行动计划");
                for (var plan : report.growthPlans()) {
                    pdf.body("情境：" + plan.context());
                    pdf.body("尝试行动：" + plan.action());
                    pdf.body("建议周期：" + plan.suggestedCycle());
                    pdf.body("观察目标：" + plan.observationTarget());
                    pdf.body("复盘提示：" + plan.reviewPrompt());
                    pdf.note(plan.basis());
                    pdf.note("依据级别：" + plan.evidenceLevel());
                    if (plan.ruleId() != null) pdf.note(
                        "规则：" + plan.ruleId() + "；图谱版本：" + plan.graphVersion()
                    );
                    if (plan.templateVersion() != null) pdf.note(
                        "当次建议模板：" +
                        plan.templateVersion() +
                        (plan.templateContentHash() == null
                                ? ""
                                : "；摘要：" + plan.templateContentHash())
                    );
                }
            }
            pdf.heading("依据与来源");
            pdf.body(report.sourceBasis());
            var graph = report.graphExplanation();
            if (graph != null) {
                pdf.heading("知识图谱解释路径");
                pdf.body(graph.notice());
                pdf.note(
                    "当次图谱版本：" +
                    (graph.graphVersion() == null ? "无可用版本" : graph.graphVersion())
                );
                if (graph.contentHash() != null) pdf.note(
                    "当次图谱内容摘要：" + graph.contentHash()
                );
                if (graph.matches().isEmpty()) pdf.body(
                    "本次未命中多维规则，不据此判断能力、优势或发展潜力。"
                );
                var graphSources = new LinkedHashMap<String, KnowledgeGraphVO.Source>();
                for (var match : graph.matches()) {
                    pdf.heading("如果处于“" + match.context() + "”情境");
                    pdf.body(match.action());
                    pdf.body("命中前提：" + String.join(" 且 ", match.matchedConditions()));
                    pdf.body(match.explanation());
                    pdf.note("规则编号：" + match.ruleId());
                    for (var edge : match.edges()) {
                        String from = match
                            .nodes()
                            .stream()
                            .filter(n -> n.id().equals(edge.from()))
                            .map(n -> n.label())
                            .findFirst()
                            .orElse(edge.from());
                        String to = match
                            .nodes()
                            .stream()
                            .filter(n -> n.id().equals(edge.to()))
                            .map(n -> n.label())
                            .findFirst()
                            .orElse(edge.to());
                        pdf.note(
                            from +
                            " -> " +
                            edge.relation() +
                            " -> " +
                            to +
                            "：" +
                            edge.explanation()
                        );
                    }
                    pdf.note(
                        "本条规则来源标识：" +
                        String.join(
                            " / ",
                            match
                                .sources()
                                .stream()
                                .map(source -> source.id())
                                .toList()
                        )
                    );
                    match
                        .sources()
                        .forEach(source -> graphSources.putIfAbsent(source.id(), source));
                }
                if (!graphSources.isEmpty()) pdf.heading("当次图谱来源");
                for (var source : graphSources.values()) {
                    pdf.note(
                        source.id() +
                        "：" +
                        source.title() +
                        " / " +
                        source.author() +
                        " / " +
                        source.license() +
                        " / " +
                        source.contentVersion() +
                        " / " +
                        source.accessedDate()
                    );
                    pdf.note("证据级别：" + source.evidenceLevel() + "；" + source.attribution());
                    pdf.note(source.url());
                }
            }
            var scale = result.scale();
            pdf.heading("量表来源与许可");
            pdf.body(scale.sourceTitle() + " / " + scale.author());
            pdf.note(scale.sourceUrl());
            pdf.body(
                "许可：" +
                scale.license() +
                "；中文版本：" +
                scale.chineseVersion() +
                "；访问日期：" +
                scale.accessedDate()
            );
            pdf.note(scale.licenseEvidence());
            pdf.note(scale.translationEvidence());
            pdf.note(scale.scoringEvidence());
            if ("OEJTS_OPENJUNG_32_V1".equals(result.scoringVersion())) pdf.note(
                "题目及中文适配署名：Eric Jorgenson / Open Psychometrics；OpenJung contributors。CC BY-NC-SA 4.0，仅限本项目非商业毕设使用；中文译本未经独立验证。https://creativecommons.org/licenses/by-nc-sa/4.0/"
            );
            pdf.heading("测评局限与数据使用");
            pdf.body(report.limitations());
            pdf.note("保存期限：" + scale.retentionPolicy() + "；联系方式：" + scale.contact());
            pdf.note(
                "评分版本：" + result.scoringVersion() + "；报告版本：" + report.reportVersion()
            );
            pdf.note("量表内容摘要：" + result.contentHash());
            pdf.note("以上均为当次保存快照；导出不使用当前图谱或建议模板重新生成结论。");
            return pdf.finish();
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("PDF export failed type:{}", exception.getClass().getSimpleName());
            throw new BusinessException("PDF报告生成失败，请联系管理员检查中文字体或稍后重试");
        }
    }
}
