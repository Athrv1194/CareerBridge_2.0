package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.CareerPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerPathRepository extends JpaRepository<CareerPath, Long> {

    @Override
    List<CareerPath> findAll();
}
