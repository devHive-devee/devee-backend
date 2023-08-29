package com.devee.devhive.domain.project.service;

import static com.devee.devhive.domain.project.type.ProjectStatus.COMPLETE;
import static com.devee.devhive.domain.project.type.ProjectStatus.RECRUITING;
import static com.devee.devhive.domain.project.type.ProjectStatus.RECRUITMENT_COMPLETE;
import static com.devee.devhive.domain.project.type.RecruitmentType.OFFLINE;
import static com.devee.devhive.global.exception.ErrorCode.NOT_FOUND_PROJECT;
import static com.devee.devhive.global.exception.ErrorCode.PROJECT_CANNOT_DELETED;
import static com.devee.devhive.global.exception.ErrorCode.UNAUTHORIZED;

import com.devee.devhive.domain.project.entity.Project;
import com.devee.devhive.domain.project.entity.dto.CreateProjectDto;
import com.devee.devhive.domain.project.entity.dto.SearchProjectDto;
import com.devee.devhive.domain.project.entity.dto.UpdateProjectDto;
import com.devee.devhive.domain.project.entity.dto.UpdateProjectStatusDto;
import com.devee.devhive.domain.project.member.entity.ProjectMember;
import com.devee.devhive.domain.project.repository.ProjectRepository;
import com.devee.devhive.domain.project.repository.custom.CustomProjectRepository;
import com.devee.devhive.domain.project.type.ProjectStatus;
import com.devee.devhive.domain.user.alarm.entity.form.AlarmForm;
import com.devee.devhive.domain.user.entity.User;
import com.devee.devhive.domain.user.type.AlarmContent;
import com.devee.devhive.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

  private final ApplicationEventPublisher eventPublisher;
  private final ProjectRepository projectRepository;
  private final CustomProjectRepository customProjectRepository;

  public Project findById(Long projectId) {
    return projectRepository.findById(projectId)
        .orElseThrow(() -> new CustomException(NOT_FOUND_PROJECT));
  }

  // 내가 생성한 프로젝트 목록 페이지
  public Page<Project> getWriteProjects(Long userId, Pageable pageable) {
    return projectRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable);
  }

  // 프로젝트 작성
  public Project createProject(CreateProjectDto createProjectDto, User user) {
    Project project = Project.builder()
        .user(user)
        .title(createProjectDto.getTitle())
        .name(createProjectDto.getProjectName())
        .content(createProjectDto.getContent())
        .teamSize(createProjectDto.getTeamSize())
        .recruitmentType(createProjectDto.getRecruitmentType())
        .developmentType(createProjectDto.getDevelopmentType())
        .deadline(createProjectDto.getDeadline())
        .status(RECRUITING)
        .build();

    if (createProjectDto.getRecruitmentType() == OFFLINE) {
      project.setRegion(createProjectDto.getRegion());
    }

    return projectRepository.save(project);
  }

  // 프로젝트 상태변경
  public void updateProjectStatus(User user, Long projectId,
      UpdateProjectStatusDto statusDto, List<ProjectMember> members) {
    Project project = findById(projectId);
    User writerUser = project.getUser();

    if (!Objects.equals(writerUser.getId(), user.getId())) {
      throw new CustomException(UNAUTHORIZED);
    }
    ProjectStatus status = statusDto.getStatus();

    if (status == RECRUITMENT_COMPLETE) {
      project.setStartDate(LocalDateTime.now());
    } else if (status == COMPLETE) {
      project.setEndDate(LocalDateTime.now());
    }

    project.setStatus(status);

    Project saveProject = projectRepository.save(project);

    if (saveProject.getStatus() == COMPLETE) {
      // 프로젝트 멤버들에게 팀원 평가 권유 알림 이벤트 발행
      for (ProjectMember projectMember : members) {
        alarmEventPub(projectMember.getUser(), saveProject);
      }
    }
  }

  // 프로젝트 수정
  @Transactional
  public Project updateProject(User user, Long projectId, UpdateProjectDto updateProjectDto) {
    Project project = findById(projectId);
    User writerUser = project.getUser();

    if (!Objects.equals(writerUser.getId(), user.getId())) {
      throw new CustomException(UNAUTHORIZED);
    }
    updateProjectFields(updateProjectDto);
    return projectRepository.save(project);
  }

  // 프로젝트 필드 업데이트
  private void updateProjectFields(UpdateProjectDto updateProjectDto) {
    Project project = Project.builder()
        .title(updateProjectDto.getTitle())
        .name(updateProjectDto.getProjectName())
        .teamSize(updateProjectDto.getTeamSize())
        .recruitmentType(updateProjectDto.getRecruitmentType())
        .developmentType(updateProjectDto.getDevelopmentType())
        .deadline(updateProjectDto.getDeadline())
        .build();

    if (updateProjectDto.getRecruitmentType() == OFFLINE) {
      project.setRegion(updateProjectDto.getRegion());
    }
  }

  // 프로젝트 삭제
  @Transactional
  public void deleteProject(Project project) {
    if (project.getStatus() != ProjectStatus.RECRUITING) {
      throw new CustomException(PROJECT_CANNOT_DELETED);
    }
    projectRepository.delete(project);
  }

  // 리더가 퇴출된 프로젝트는 예외 없이 삭제
  @Transactional
  public void deleteLeadersProject(Long projectId) {
    Project project = findById(projectId);
    projectRepository.delete(project);
  }

  private void alarmEventPub(User user, Project project) {
    AlarmForm alarmForm = AlarmForm.builder()
        .receiverUser(user)
        .project(project)
        .content(AlarmContent.REVIEW_REQUEST)
        .build();
    eventPublisher.publishEvent(alarmForm);
  }

  public Page<Project> getProject(SearchProjectDto searchRequest, String sort, Pageable pageable) {
    if (searchRequest == null) {
      return customProjectRepository.getProject(null, null, null, null, sort, pageable);
    }

    return customProjectRepository.getProject(
        searchRequest.getKeyword(),
        searchRequest.getDevelopment(),
        searchRequest.getRecruitment(),
        searchRequest.getTechStackIds(),
        sort, pageable);
  }
}














