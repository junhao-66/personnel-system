package com.personnel;

import com.personnel.dto.req.*;
import com.personnel.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises the actual bundled demo and server scoring. Account/answers roll back; bundled seed remains. */
@SpringBootTest @Transactional
class DemoAssessmentDatabaseIT {
    @Autowired AssessmentScaleService scales;
    @Autowired AssessmentService assessments;
    @Autowired UserService users;
    @Autowired DataSource dataSource;
    @Test void bundledDemoCanCompleteAndRetainsDemoWarningInHistory() {
        var scale = scales.listPublished().stream().filter(s -> s.definition().license().equals("PROJECT-DEMO-ONLY")).findFirst().orElseThrow();
        assertThat(scale.definition().questions()).hasSize(16);
        var employee = users.createUser(new AdminUserCreateReqDTO("dm" + UUID.randomUUID().toString().replace("-", "").substring(0, 16), "TestOnly123", "演示验收", null, "EMPLOYEE", null, null));
        var draft = assessments.startAssessment(employee.id(), new AssessmentStartReqDTO(scale.id(), true));
        var answers = new LinkedHashMap<String, Integer>();
        scale.definition().questions().forEach(q -> answers.put(q.key(), q.highPole().equals(q.dimension().substring(0, 1)) ? 5 : 1));
        var saved = assessments.saveAnswers(employee.id(), draft.id(), new AssessmentAnswersReqDTO(0, answers));
        var result = assessments.submitAssessment(employee.id(), draft.id(), new AssessmentAnswersReqDTO(saved.revision(), answers));
        assertThat(result.typeCode()).isEqualTo("ESTJ");
        assertThat(result.report().disclaimer()).contains("毕设演示报告", "未经信效度验证");
        assertThat(assessments.getResult(employee.id(), draft.id())).isEqualTo(result);
    }
    @Test void seedIsIdempotentAndDoesNotRepublishArchivedDemo() {
        var admin = users.createUser(new AdminUserCreateReqDTO("dm" + UUID.randomUUID().toString().replace("-", "").substring(0, 16), "TestOnly123", "演示验收", null, "ADMIN", null, null));
        var demo = scales.listPublished().stream().filter(s -> s.definition().license().equals("PROJECT-DEMO-ONLY")).findFirst().orElseThrow();
        var populator = new ResourceDatabasePopulator(); populator.setSqlScriptEncoding("UTF-8"); populator.addScript(new ClassPathResource("data.sql"));
        populator.execute(dataSource); populator.execute(dataSource);
        assertThat(scales.listAll(admin.id()).stream().filter(s -> s.definition().license().equals("PROJECT-DEMO-ONLY")).count()).isEqualTo(1);
        scales.archiveScale(admin.id(), demo.id()); populator.execute(dataSource);
        assertThat(scales.listPublished()).noneMatch(s -> s.id().equals(demo.id()));
    }
}
