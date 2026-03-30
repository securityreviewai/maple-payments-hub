package com.maple.repository;

import com.maple.model.SavedSearchFilter;
import com.maple.model.SavedSearchVisibility;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedSearchFilterRepository extends JpaRepository<SavedSearchFilter, UUID> {

    List<SavedSearchFilter> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<SavedSearchFilter> findByVisibilityAndSharedRoleIn(
            SavedSearchVisibility visibility, Collection<String> sharedRoles);

    Optional<SavedSearchFilter> findByUserIdAndId(UUID userId, UUID id);

    Optional<SavedSearchFilter> findByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndName(UUID userId, String name);

    long countByUserId(UUID userId);
}
