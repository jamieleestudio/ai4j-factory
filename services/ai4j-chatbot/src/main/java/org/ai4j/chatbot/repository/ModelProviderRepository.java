package org.ai4j.chatbot.repository;

import org.ai4j.chatbot.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    boolean existsByName(String name);
}
