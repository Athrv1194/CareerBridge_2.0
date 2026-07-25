package com.careerbridge.student.repository;

import com.careerbridge.student.model.SocialLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SocialLinkRepository extends JpaRepository<SocialLink, Long> {

    List<SocialLink> findByStudentProfileId(Long studentProfileId);
}
