package com.personnel.service;

import com.personnel.vo.StaticCacheStatusVO;

public interface StaticCacheService {
    StaticCacheStatusVO status(Long actor);
    StaticCacheStatusVO refresh(Long actor);
}
