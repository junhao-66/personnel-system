package com.personnel.service;

import com.personnel.dto.req.JobProfileCreateReqDTO;
import com.personnel.vo.*;
import java.util.List;

public interface JobPreferenceService {
    List<JobProfileVO> listProfiles(Long actor);
    JobProfileVO createProfile(Long actor, JobProfileCreateReqDTO request);
    void publishProfile(Long actor, Long id);
    void archiveProfile(Long actor, Long id);
    JobExplorationVO explore(Long actor, Long owner);
}
