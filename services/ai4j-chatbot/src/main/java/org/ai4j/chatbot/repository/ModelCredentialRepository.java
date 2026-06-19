package org.ai4j.chatbot.repository;

import org.ai4j.chatbot.entity.ModelCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelCredentialRepository extends JpaRepository<ModelCredential, Long> {
    List<ModelCredential> findByUserId(String userId);
}
