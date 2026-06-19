package org.ai4j.chatbot.repository;

import org.ai4j.chatbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {
    List<UserConfig> findByUserId(String userId);
    Optional<UserConfig> findByUserIdAndAlias(String userId, String alias);
}
