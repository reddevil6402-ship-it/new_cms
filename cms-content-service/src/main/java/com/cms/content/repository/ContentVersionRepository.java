package com.cms.content.repository;

import com.cms.content.domain.ContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContentVersionRepository extends JpaRepository<ContentVersion, UUID> {

    List<ContentVersion> findAllByContentItemIdOrderByVersionNumberDesc(UUID contentItemId);
}
