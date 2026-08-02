package com.careerbridge.aicoach.repository;

import com.careerbridge.aicoach.model.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    /**
     * Excludes the messages field from the projection -- a session list is a metadata table, not a
     * transcript viewer, and pulling every embedded message just to render a list of titles is the
     * same needless-eager-load shape resume-service's ResumeSummary projection avoids for PDF bytes.
     */
    @Query(value = "{ 'studentId': ?0 }", fields = "{ 'messages': 0 }", sort = "{ 'updatedAt': -1 }")
    List<ChatSession> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    Optional<ChatSession> findByIdAndStudentId(String id, Long studentId);

    /**
     * Returns long, never List -- a derived delete returning List<T> runs findAllAndRemove, loading
     * every match into memory first. Here it is always exactly 0 or 1 document. 0 means either the
     * id does not exist or belongs to another student; both collapse to 404 in the service layer,
     * the same "no legitimate reason to distinguish" shape resume-service uses for resume lookups.
     */
    long deleteByIdAndStudentId(String id, Long studentId);
}
