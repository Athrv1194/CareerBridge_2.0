package com.careerbridge.aicoach.repository;

import com.careerbridge.aicoach.model.MilestoneResourceDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MilestoneResourceRepository extends MongoRepository<MilestoneResourceDocument, String> {

    boolean existsByCareerNameAndMilestoneTitle(String careerName, String milestoneTitle);

    List<MilestoneResourceDocument> findByCareerName(String careerName);
}
