package com.careerbridge.assessment.repository;

import com.careerbridge.assessment.model.Option;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OptionRepository extends JpaRepository<Option, Long> {

    List<Option> findByQuestionIdOrderByOrderIndex(Long questionId);

    Optional<Option> findByIdAndQuestionId(Long id, Long questionId);

    /**
     * Bulk variant used by submitAttempt and getQuestions: loads every option for a whole category
     * in one query, so answer validation and DTO assembly stay flat instead of one query per
     * question.
     */
    List<Option> findByQuestionIdInOrderByOrderIndex(List<Long> questionIds);

    /**
     * ADMIN MODULE: editQuestion replaces a question's whole option set rather than diffing it.
     *
     * Needed because Question holds no @OneToMany to Option -- the link is a plain question_id
     * column -- so there is no cascade to lean on and the old rows must be removed explicitly.
     *
     * A derived delete, so it needs @Transactional at the call site; Spring Data issues a select
     * followed by per-row deletes rather than one bulk statement, which is what keeps the
     * persistence context consistent with the rows just removed.
     */
    void deleteByQuestionId(Long questionId);
}
