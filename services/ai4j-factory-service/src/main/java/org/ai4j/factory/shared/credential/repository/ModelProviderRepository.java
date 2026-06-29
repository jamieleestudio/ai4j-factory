package org.ai4j.factory.shared.credential.repository;

import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    boolean existsByName(String name);
}
