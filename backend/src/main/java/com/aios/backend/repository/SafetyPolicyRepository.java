package com.aios.backend.repository;

import com.aios.backend.model.SafetyPolicyEntity;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SafetyPolicyEntity database operations.
 * 
 * <p>
 * Provides CRUD operations and custom queries for safety policies.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Repository
public interface SafetyPolicyRepository extends JpaRepository<SafetyPolicyEntity, Long> {

    /**
     * Find a policy by its unique name.
     * 
     * @param name the policy name
     * @return the policy if found
     */
    Optional<SafetyPolicyEntity> findByName(String name);

    /**
     * Find all enabled policies.
     * 
     * @return list of enabled policies ordered by priority
     */
    List<SafetyPolicyEntity> findByEnabledTrueOrderByPriorityAsc();

    /**
     * Find all policies for a specific action type.
     * 
     * @param actionType the action type
     * @return list of policies that apply to this action type
     */
    List<SafetyPolicyEntity> findByActionTypeAndEnabledTrueOrderByPriorityAsc(ActionType actionType);

    /**
     * Find policies by safety level.
     * 
     * @param safetyLevel the safety level
     * @return list of policies for this safety level
     */
    List<SafetyPolicyEntity> findBySafetyLevelAndEnabledTrueOrderByPriorityAsc(SafetyLevel safetyLevel);

    /**
     * Find policies that require approval.
     * 
     * @return list of policies requiring approval
     */
    List<SafetyPolicyEntity> findByRequiresApprovalTrueAndEnabledTrueOrderByPriorityAsc();

    /**
     * Find policies that apply to an action type (including global policies).
     * Global policies have null actionType and apply to all actions.
     * 
     * @param actionType the action type to check
     * @return list of applicable policies
     */
    @Query("SELECT p FROM SafetyPolicyEntity p WHERE p.enabled = true " +
            "AND (p.actionType = :actionType OR p.actionType IS NULL) " +
            "ORDER BY p.priority ASC")
    List<SafetyPolicyEntity> findApplicablePolicies(@Param("actionType") ActionType actionType);

    /**
     * Find policies that block production execution.
     * 
     * @return list of policies that disallow production execution
     */
    List<SafetyPolicyEntity> findByAllowedInProductionFalseAndEnabledTrueOrderByPriorityAsc();

    /**
     * Check if any policy exists for an action type.
     * 
     * @param actionType the action type
     * @return true if at least one policy exists
     */
    boolean existsByActionTypeAndEnabledTrue(ActionType actionType);

    /**
     * Count enabled policies.
     * 
     * @return count of enabled policies
     */
    long countByEnabledTrue();

    /**
     * Find policies with a minimum confidence threshold above a value.
     * 
     * @param threshold the threshold value
     * @return list of high-threshold policies
     */
    List<SafetyPolicyEntity> findByMinConfidenceThresholdGreaterThanAndEnabledTrueOrderByPriorityAsc(
            Double threshold);

    /**
     * Find policies that have execution limits set.
     * 
     * @return list of rate-limited policies
     */
    @Query("SELECT p FROM SafetyPolicyEntity p WHERE p.enabled = true " +
            "AND p.maxExecutionsPerHour IS NOT NULL ORDER BY p.priority ASC")
    List<SafetyPolicyEntity> findRateLimitedPolicies();

    /**
     * Delete all policies for a specific action type.
     * 
     * @param actionType the action type
     * @return number of deleted policies
     */
    long deleteByActionType(ActionType actionType);
}
