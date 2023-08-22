package com.devee.devhive.domain.project.apply.service;

import static com.devee.devhive.global.exception.ErrorCode.APPLICATION_ALREADY_ACCEPT;
import static com.devee.devhive.global.exception.ErrorCode.APPLICATION_ALREADY_REJECT;
import static com.devee.devhive.global.exception.ErrorCode.APPLICATION_STATUS_NOT_PENDING;
import static com.devee.devhive.global.exception.ErrorCode.NOT_FOUND_APPLICATION;
import static com.devee.devhive.global.exception.ErrorCode.PROJECT_ALREADY_APPLIED;
import static com.devee.devhive.global.exception.ErrorCode.RECRUITMENT_ALREADY_COMPLETED;
import static com.devee.devhive.global.exception.ErrorCode.UNAUTHORIZED;

import com.devee.devhive.domain.project.apply.entity.ProjectApply;
import com.devee.devhive.domain.project.apply.repository.ProjectApplyRepository;
import com.devee.devhive.domain.project.entity.Project;
import com.devee.devhive.domain.project.type.ApplyStatus;
import com.devee.devhive.domain.project.type.ProjectStatus;
import com.devee.devhive.domain.user.entity.User;
import com.devee.devhive.global.exception.CustomException;
import com.devee.devhive.global.redis.RedisService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectApplyService {

    private final ProjectApplyRepository projectApplyRepository;
    private final RedisService redisService;

    // 신청 상태
    public ApplyStatus getApplicationStatus(User user, Project project) {
        Optional<ProjectApply> projectApplyOptional =
            projectApplyRepository.findByUserIdAndProjectId(user.getId(), project.getId());

        return projectApplyOptional.map(ProjectApply::getStatus).orElse(null);
    }

    public ProjectApply getProjectApplyById(Long applicationId) {
        return projectApplyRepository.findById(applicationId)
            .orElseThrow(() -> new CustomException(NOT_FOUND_APPLICATION));
    }

    // 신청
    @Transactional
    public void projectApply(User user, Project project) {
        // 자기가 작성한 프로젝트에 신청하는 경우
        Long userId = user.getId();
        if (Objects.equals(project.getWriterUser().getId(), userId)) {
            throw new CustomException(UNAUTHORIZED);
        }
        // 프로젝트 모집이 완료된 상태
        ProjectStatus projectStatus = project.getStatus();
        if (projectStatus == ProjectStatus.RECRUITMENT_COMPLETE || projectStatus == ProjectStatus.COMPLETE) {
            throw new CustomException(RECRUITMENT_ALREADY_COMPLETED);
        }
        Long projectId = project.getId();
        // 이미 신청한 적이 있는 경우
        projectApplyRepository.findByUserIdAndProjectId(userId, projectId)
            .ifPresent(apply -> {
                ApplyStatus status = apply.getStatus();
                if (status == ApplyStatus.PENDING) {       // 이미 신청 중
                    throw new CustomException(PROJECT_ALREADY_APPLIED);
                } else if (status == ApplyStatus.ACCEPT) { // 신청 승인된 상태
                    throw new CustomException(APPLICATION_ALREADY_ACCEPT);
                } else {                                   // 신청 거절된 상태
                    throw new CustomException(APPLICATION_ALREADY_REJECT);
                }
            });
        // 동시성 이슈를 방지하기 위해 레디스락 사용, 락 획득할때까지 시도
        String KEY = "PROJECT_" + projectId;
        int retryDelayMilliseconds = 200; // 재시도 간격 (예: 200ms)

        while (true) {
            boolean locked = redisService.getLock(KEY, 5);
            if (locked) {
                try {
                    projectApplyRepository.save(ProjectApply.builder()
                        .project(project)
                        .user(user)
                        .status(ApplyStatus.PENDING)
                        .build());
                    break; // 락을 획득하고 데이터 처리 후 루프 종료
                } finally {
                    redisService.unLock(KEY);
                }
            } else {
                // 락을 획득하지 못한 경우, 일정 시간 동안 대기한 후 재시도
                try {
                    Thread.sleep(retryDelayMilliseconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // 신청 취소
    public void deleteApplication(Long userId, Long projectId) {
        projectApplyRepository.findByUserIdAndProjectId(userId, projectId)
            .ifPresent(projectApply -> {
                // 자신의 프로젝트 신청건이 아닌 경우
                if (!Objects.equals(projectApply.getUser().getId(), userId)) {
                    throw new CustomException(UNAUTHORIZED);
                }
                // 신청 상태가 대기상태가 아닌 경우
                if (projectApply.getStatus() != ApplyStatus.PENDING) {
                    throw new CustomException(APPLICATION_STATUS_NOT_PENDING);
                }
                projectApplyRepository.delete(projectApply);
            });
    }

    // 프로젝트 신청 목록
    public List<ProjectApply> getProjectApplies(Long projectId) {
        return projectApplyRepository.findAllByProjectId(projectId);
    }

    // 신청 승인
    public void accept(ProjectApply projectApply) {
        // 신청 대기 상태가 아닌 경우 (이미 승인/거절된 경우)
        if (projectApply.getStatus() != ApplyStatus.PENDING) {
            throw new CustomException(APPLICATION_STATUS_NOT_PENDING);
        }
        projectApply.setStatus(ApplyStatus.ACCEPT);
        projectApplyRepository.save(projectApply);
    }

    // 신청 거절
    public void reject(User user, Long applicationId) {
        ProjectApply projectApply = getProjectApplyById(applicationId);
        // 프로젝트 작성자가 아닌 경우
        if (!Objects.equals(projectApply.getProject().getWriterUser().getId(), user.getId())) {
            throw new CustomException(UNAUTHORIZED);
        }
        // 신청 대기 상태가 아닌 경우 (이미 승인/거절된 경우)
        if (projectApply.getStatus() != ApplyStatus.PENDING) {
            throw new CustomException(APPLICATION_STATUS_NOT_PENDING);
        }

        projectApply.setStatus(ApplyStatus.REJECT);
        projectApplyRepository.save(projectApply);
    }
}