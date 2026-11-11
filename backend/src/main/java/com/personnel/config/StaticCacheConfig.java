package com.personnel.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.StaticCachePolicy;
import com.personnel.vo.*;
import java.time.Duration;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;

@Configuration
@EnableCaching(order = org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class StaticCacheConfig implements CachingConfigurer {

    public static final String SCALES = "catalog-scales",
        GRAPH = "catalog-graph",
        GROWTH = "catalog-growth",
        JOBS = "catalog-jobs",
        PROMPTS = "catalog-prompts",
        DICTIONARY = "catalog-dictionary";
    public static final List<String> NAMES = List.of(
        SCALES,
        GRAPH,
        GROWTH,
        JOBS,
        PROMPTS,
        DICTIONARY
    );
    private final StaticCachePolicy policy;

    public StaticCacheConfig(StaticCachePolicy policy) {
        this.policy = policy;
    }

    @Bean
    public CacheManager cacheManager(
        RedisConnectionFactory connection,
        ObjectMapper mapper,
        StaticCacheProperties properties
    ) {
        var types = mapper.getTypeFactory();
        var definitions = new LinkedHashMap<String, RedisCacheConfiguration>();
        Map<String, JavaType> schemas = Map.of(
            SCALES,
            types.constructCollectionType(List.class, AssessmentScaleVO.class),
            GRAPH,
            types.constructType(KnowledgeGraphVO.class),
            GROWTH,
            types.constructType(GrowthTemplateVO.class),
            JOBS,
            types.constructCollectionType(List.class, JobProfileVO.class),
            PROMPTS,
            types.constructMapType(Map.class, String.class, String.class),
            DICTIONARY,
            types.constructMapType(Map.class, String.class, String.class)
        );
        schemas.forEach((name, type) ->
            definitions.put(
                name,
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(properties.getTtlSeconds()))
                    .disableCachingNullValues()
                    .computePrefixWith(cache -> properties.getNamespace() + cache + ":")
                    .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                            new Jackson2JsonRedisSerializer<>(mapper, type)
                        )
                    )
            )
        );
        CacheManager delegate = RedisCacheManager.builder(connection)
            .withInitialCacheConfigurations(definitions)
            .disableCreateOnMissingCache()
            .build();
        ((RedisCacheManager) delegate).initializeCaches();
        return new CacheManager() {
            private final Map<String, Cache> wrapped =
                new java.util.concurrent.ConcurrentHashMap<>();

            public Collection<String> getCacheNames() {
                return delegate.getCacheNames();
            }

            public Cache getCache(String name) {
                return wrapped.computeIfAbsent(name, key -> {
                    var cache = delegate.getCache(key);
                    return cache == null ? null : new GenerationCache(cache, policy);
                });
            }
        };
    }

    /** Advance before clearing so a concurrent, older DB read cannot repopulate an active key. */
    private record GenerationCache(Cache delegate, StaticCachePolicy policy) implements Cache {
        public String getName() {
            return delegate.getName();
        }

        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        public ValueWrapper get(Object key) {
            return delegate.get(key);
        }

        public <T> T get(Object key, Class<T> type) {
            return delegate.get(key, type);
        }

        public <T> T get(Object key, java.util.concurrent.Callable<T> loader) {
            return delegate.get(key, loader);
        }

        public void put(Object key, Object value) {
            delegate.put(key, value);
        }

        public ValueWrapper putIfAbsent(Object key, Object value) {
            return delegate.putIfAbsent(key, value);
        }

        public void evict(Object key) {
            delegate.evict(key);
        }

        public void clear() {
            policy.advance();
            delegate.clear();
        }
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private void failure(RuntimeException exception, Cache cache) {
                policy.failed();
                LoggerFactory.getLogger(StaticCacheConfig.class).warn(
                    "Static cache unavailable catalog:{} exception:{}; falling back to MySQL",
                    cache.getName(),
                    exception.getClass().getSimpleName()
                );
            }

            public void handleCacheGetError(RuntimeException e, Cache c, Object key) {
                failure(e, c);
            }

            public void handleCachePutError(RuntimeException e, Cache c, Object key, Object value) {
                failure(e, c);
            }

            public void handleCacheEvictError(RuntimeException e, Cache c, Object key) {
                failure(e, c);
            }

            public void handleCacheClearError(RuntimeException e, Cache c) {
                failure(e, c);
            }
        };
    }
}
