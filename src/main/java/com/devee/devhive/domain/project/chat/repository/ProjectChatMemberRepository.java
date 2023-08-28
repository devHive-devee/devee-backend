package com.devee.devhive.domain.project.chat.repository;

import com.devee.devhive.domain.project.chat.entity.ProjectChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectChatMemberRepository
    extends JpaRepository<ProjectChatMember, Long> {

  boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
