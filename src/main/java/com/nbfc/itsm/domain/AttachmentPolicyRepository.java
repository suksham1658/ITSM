package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttachmentPolicyRepository extends JpaRepository<AttachmentPolicy, Long> {
    Optional<AttachmentPolicy> findFirstByOrderByAttachmentPolicyIdAsc();
}
