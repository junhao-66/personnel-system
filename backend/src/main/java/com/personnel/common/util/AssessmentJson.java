package com.personnel.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.exception.BusinessException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AssessmentJson {

    private static final Logger log = LoggerFactory.getLogger(AssessmentJson.class);
    private final ObjectMapper mapper;

    public AssessmentJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.error("Assessment serialization failed type:{}", value.getClass().getSimpleName());
            throw new BusinessException("测评数据保存失败，请联系管理员");
        }
    }

    public <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            log.error("Assessment snapshot decoding failed type:{}", type.getSimpleName());
            throw new BusinessException("测评快照读取失败，请联系管理员");
        }
    }

    public Map<String, Integer> readAnswers(String value) {
        try {
            return mapper.readValue(value, new TypeReference<Map<String, Integer>>() {});
        } catch (JsonProcessingException exception) {
            log.error("Assessment answer snapshot decoding failed");
            throw new BusinessException("答题快照读取失败，请联系管理员");
        }
    }
}
