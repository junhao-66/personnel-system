package com.personnel;
import com.personnel.common.util.StaticCachePolicy;
import com.personnel.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"personnel.static-cache.enabled=true","spring.data.redis.port=1","spring.data.redis.connect-timeout=100ms","spring.data.redis.timeout=100ms"})
class StaticCacheUnavailableIT {
    @Autowired KnowledgeGraphService graphs;@Autowired AssessmentScaleService scales;@Autowired StaticCachePolicy policy;
    @Test void failedRedisReturnsMysqlThenSkipsRepeatedConnectionsDuringCooldown(){
        assertThat(graphs.getCurrent()).isNotNull();assertThat(policy.circuitOpen()).isTrue();long failures=policy.failures();assertThat(failures).isPositive();
        assertThat(scales.listPublished()).isNotEmpty();assertThat(graphs.getCurrent()).isNotNull();assertThat(policy.failures()).isEqualTo(failures);
    }
}
