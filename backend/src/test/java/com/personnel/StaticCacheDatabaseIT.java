package com.personnel;
import com.personnel.common.util.StaticCachePolicy;
import com.personnel.config.StaticCacheConfig;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties={"personnel.static-cache.enabled=true"})
class StaticCacheDatabaseIT {
    static final String PREFIX="personnel:cachetest:"+UUID.randomUUID()+":";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("personnel.static-cache.namespace",()->PREFIX);}
    @Autowired StringRedisTemplate redis;@Autowired CacheManager manager;@Autowired StaticCachePolicy policy;
    @Autowired KnowledgeGraphService graphs;@Autowired AssessmentScaleService scales;@Autowired GrowthTemplateService growth;
    @Autowired JobProfileCatalogService jobs;@Autowired StaticReferenceService references;@Autowired StaticCacheService maintenance;
    @Autowired TransactionTemplate transaction;
    @MockBean UserService users;
    @BeforeEach void setup(){when(users.getById(1L)).thenReturn(new UserVO(1L,"test","缓存测试",null,"ADMIN","APPROVED","ENABLED",null,null));}
    @AfterEach void clean(){var keys=redis.keys(PREFIX+"*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);}
    @Test void realRedisCachesOnlyTypedStaticCatalogsWithBoundedTtl() {
        var graph=graphs.getCurrent();assertThat(graph).isNotNull();var key=PREFIX+StaticCacheConfig.GRAPH+":"+policy.key("current");
        assertThat(redis.opsForValue().get(key)).contains(graph.version()).doesNotContain("password","answersJson","resultJson");
        assertThat(redis.getExpire(key)).isBetween(1L,300L);
        var cached=new KnowledgeGraphVO(999999L,graph.version(),"缓存命中测试","PUBLISHED",graph.contentHash(),graph.sources(),graph.nodes(),graph.edges(),graph.rules());
        manager.getCache(StaticCacheConfig.GRAPH).put(policy.key("current"),cached);
        assertThat(graphs.getCurrent().id()).isEqualTo(999999L);
        assertThat(scales.listPublished()).isNotEmpty();assertThat(scales.listPublished().get(0).definition().questions()).isNotEmpty();
        assertThat(growth.getEffective()).isEqualTo(growth.getEffective());assertThat(jobs.published()).isEqualTo(jobs.published());
        assertThat(references.prompts()).containsKey("instruction");assertThat(references.prompts()).containsKey("version");assertThat(references.dictionary()).containsEntry("ADMIN","管理员");
        assertThat(maintenance.refresh(1L).state()).isEqualTo("READY");assertThat(graphs.getCurrent().id()).isEqualTo(graph.id());
    }
    @Test void transactionalReadsBypassCachedContentAndCorruptCacheFallsBack() {
        var graph=graphs.getCurrent();manager.getCache(StaticCacheConfig.GRAPH).put(policy.key("current"),new KnowledgeGraphVO(999999L,graph.version(),graph.title(),graph.status(),graph.contentHash(),graph.sources(),graph.nodes(),graph.edges(),graph.rules()));
        transaction.executeWithoutResult(status->{assertThat(graphs.getCurrent().id()).isEqualTo(graph.id());status.setRollbackOnly();});
        String oldKey=policy.key("current");redis.opsForValue().set(PREFIX+StaticCacheConfig.GRAPH+":"+oldKey,"invalid-json");
        assertThat(graphs.getCurrent().id()).isEqualTo(graph.id());assertThat(policy.circuitOpen()).isTrue();assertThat(policy.key("current")).isNotEqualTo(oldKey);
        assertThat(maintenance.refresh(1L).state()).isEqualTo("READY");assertThat(graphs.getCurrent().id()).isEqualTo(graph.id());
    }
    @Test void nonAdminCannotReadOrRefreshMaintenance() {
        when(users.getById(2L)).thenReturn(new UserVO(2L,"test","普通员工",null,"EMPLOYEE","APPROVED","ENABLED",null,null));
        assertThatThrownBy(()->maintenance.status(2L)).hasMessageContaining("管理员");assertThatThrownBy(()->maintenance.refresh(2L)).hasMessageContaining("管理员");
    }
    @Test void graphMutationActivelyEvictsAndRolledBackChangesNeverReachCache(){
        var graph=graphs.getCurrent();String oldKey=policy.key("current");assertThat(manager.getCache(StaticCacheConfig.GRAPH).get(oldKey)).isNotNull();
        transaction.executeWithoutResult(status->{graphs.archiveVersion(1L,graph.id());status.setRollbackOnly();});
        assertThat(manager.getCache(StaticCacheConfig.GRAPH).get(policy.key("current"))).isNull();
        // Simulate a slow pre-invalidation reader writing its old generation after the clear.
        manager.getCache(StaticCacheConfig.GRAPH).put(oldKey,new KnowledgeGraphVO(999999L,graph.version(),graph.title(),graph.status(),graph.contentHash(),graph.sources(),graph.nodes(),graph.edges(),graph.rules()));
        assertThat(graphs.getCurrent().id()).isEqualTo(graph.id());
    }
}
