package org.ai4j.chatbot.repository;

import org.ai4j.chatbot.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
}
