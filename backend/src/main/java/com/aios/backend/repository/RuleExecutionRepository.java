package com.aios.backend.repository;

import com.aios.backend.model.RuleExecutionEntity;
import com.aios.shared.dto.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RuleExecutionRepository extends JpaRepository<RuleExecutionEntity, Long> {

    List<RuleExecutionEntity> findByIssueId(Long issueId);

    List<RuleExecutionEntity> findByStatus(ExecutionStatus status);

    List<RuleExecutionEntity> findByExecutedBy(String executedBy);

    List<RuleExecutionEntity> findByCreatedAtBetween(Instant start, Instant end);

    List<RuleExecutionEntity> findTop100ByOrderByCreatedAtDesc();

    Optional<RuleExecutionEntity> findFirstByIssueIdAndActionTypeOrderByCreatedAtDesc(Long issueId, String actionType);
}
