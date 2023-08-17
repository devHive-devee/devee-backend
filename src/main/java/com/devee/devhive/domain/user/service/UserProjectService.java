package com.devee.devhive.domain.user.service;

import static com.devee.devhive.global.exception.ErrorCode.NOT_FOUND_PROJECT;

import com.devee.devhive.domain.project.entity.Project;
import com.devee.devhive.domain.project.entity.dto.MyProjectInfoDto;
import com.devee.devhive.domain.project.entity.dto.SimpleProjectDto;
import com.devee.devhive.domain.project.member.repository.ProjectMemberRepository;
import com.devee.devhive.domain.project.repository.ProjectRepository;
import com.devee.devhive.domain.project.review.repository.ProjectReviewRepository;
import com.devee.devhive.domain.user.entity.User;
import com.devee.devhive.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectReviewRepository projectReviewRepository;

    // 내가 생성한 프로젝트 목록 페이지
    public Page<SimpleProjectDto> getWriteProjects(Authentication authentication, Pageable pageable) {
        User user = (User) authentication.getPrincipal();

        return projectRepository.findByWriterUserOrderByCreatedDateDesc(user, pageable)
            .map(SimpleProjectDto::from);
    }

    // 내가 참여한 프로젝트 목록 페이지
    public Page<SimpleProjectDto> getParticipationProjects(Authentication authentication, Pageable pageable) {
        User user = (User) authentication.getPrincipal();

        return projectMemberRepository.findByUserOrderByCreatedDateDesc(user, pageable)
            .map(projectMember -> SimpleProjectDto.from(projectMember.getProject()));
    }

    // 내 프로젝트 정보 조회
    public MyProjectInfoDto getProjectInfo(Long projectId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new CustomException(NOT_FOUND_PROJECT));

        int totalScore = projectReviewRepository.getTotalScoreByUserAndProject(user, project);

        return MyProjectInfoDto.of(project, calculateTotalAverageScore(totalScore));
    }

    // 소수 첫째 자리까지 반올림
    private static double calculateTotalAverageScore(int score) {
        return Math.round(score / 5.0 * 10.0) / 10.0;
    }
}