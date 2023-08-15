package com.devee.devhive.domain.project.review.repository;

import com.devee.devhive.domain.project.review.entity.ProjectReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectReviewRepository extends JpaRepository<ProjectReview, Long> {

}