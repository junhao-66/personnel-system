package com.personnel.convert;

import com.personnel.dto.req.GrowthTemplateCreateReqDTO;
import com.personnel.entity.GrowthTemplateDO;
import com.personnel.vo.GrowthTemplateVO;
import java.time.LocalDateTime;

public final class GrowthTemplateConvert {

    private GrowthTemplateConvert() {}

    public static GrowthTemplateCreateReqDTO normalize(GrowthTemplateCreateReqDTO value) {
        return new GrowthTemplateCreateReqDTO(
            value.version(),
            value.title().trim(),
            value.suggestedCycle().trim(),
            value.observationTarget().trim(),
            value.reviewPrompt().trim(),
            value.fallbackContext().trim(),
            value.fallbackAction().trim()
        );
    }

    public static GrowthTemplateDO toDO(
        GrowthTemplateCreateReqDTO value,
        String hash,
        Long actor,
        LocalDateTime now
    ) {
        var row = new GrowthTemplateDO();
        row.setVersion(value.version());
        row.setTitle(value.title());
        row.setStatus("DRAFT");
        row.setContentHash(hash);
        row.setSuggestedCycle(value.suggestedCycle());
        row.setObservationTarget(value.observationTarget());
        row.setReviewPrompt(value.reviewPrompt());
        row.setFallbackContext(value.fallbackContext());
        row.setFallbackAction(value.fallbackAction());
        row.setCreateBy(actor);
        row.setCreateTime(now);
        return row;
    }

    public static GrowthTemplateVO toVO(GrowthTemplateDO row) {
        return new GrowthTemplateVO(
            row.getId(),
            row.getVersion(),
            row.getTitle(),
            row.getStatus(),
            row.getContentHash(),
            row.getSuggestedCycle(),
            row.getObservationTarget(),
            row.getReviewPrompt(),
            row.getFallbackContext(),
            row.getFallbackAction()
        );
    }

    public static GrowthTemplateVO builtIn() {
        return new GrowthTemplateVO(
            null,
            "BUILTIN_V1",
            "内置通用行动模板",
            "PUBLISHED",
            null,
            "建议1周，在2次真实任务中自愿尝试；可按实际安排调整，不要求固定完成率。",
            "每次简记采用的做法、自己的舒适程度，以及收到的具体反馈；比较情境差异，不据此评价能力或绩效。",
            "一周后回看：这种做法在什么情境有帮助？何时不适合？下次希望保留或调整哪一步？",
            "自行选择的日常学习或工作任务",
            "记录两次真实任务中的信息处理、沟通和安排方式，自愿尝试一种替代做法。"
        );
    }
}
