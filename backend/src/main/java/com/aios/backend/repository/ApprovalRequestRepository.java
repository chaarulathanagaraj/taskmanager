package com.aios.backend.repository;

import com.aios.backend.model.ApprovalRequestEntity;
import com.aios.shared.dto.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, Long> {

    Optional<ApprovalRequestEntity> findByExecutionId(Long executionId);

    List<ApprovalRequestEntity> findByStatus(ExecutionStatus status);

    List<ApprovalRequestEntity> findByIssueId(Long issueId);

    List<ApprovalRequestEntity> findByRequestedBy(String requestedBy);

    List<ApprovalRequestEntity> findByStatusOrderByRequestedAtDesc(ExecutionStatus status);
}
