package org.ai4j.factory.shared.credential.repository;

import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelCredentialRepository extends JpaRepository<ModelCredential, Long> {
    List<ModelCredential> findByUserId(String userId);

    @EntityGraph(attributePaths = "provider")
    Optional<ModelCredential> findWithProviderById(Long id);
}
