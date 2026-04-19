# Process Classification & Intelligent Action Safety System

## Overview

Implemented a comprehensive process classification and action safety matrix to prevent automation failures on system-critical, security, and service processes.

## Problem Statement

Previous automation failures:

- 14 failed processes with "RESOURCE_NOT_FOUND" and "OPERATION_FAILED" errors
- System attempting inappropriate actions (restart) on protected processes
- No process-type awareness in action selection
- MCP tools failing silently without recovery

Example failures:

- WmiPrvSE, MsMpEng, SearchIndexer: System-critical processes targeted for restart
- OneDrive, PcConnectionService: Service-managed processes requiring SC commands
- Antigravity.tmp: Ephemeral processes (likely already terminated)

---

## Solution Architecture

### 1. ProcessClassifier Service

**File**: `backend/src/main/java/com/aios/backend/service/ProcessClassifier.java`

**Classification Categories**:

- **SYSTEM_CRITICAL**: Core OS processes (WmiPrvSE, WmiApSrv, SearchIndexer, mscorsvw, csrss, winlogon, services, lsass, dwm)
- **SECURITY_PROCESS**: Antivirus/security (MsMpEng, WinDefend, wscsvc)
- **WINDOWS_SERVICE**: Service-managed processes (OneDrive, PcConnectionService, InstantTransfer, spoolsv.exe, w3wp.exe)
- **USER_PROCESS**: Normal user applications
- **TEMP_PROCESS**: Ephemeral processes (\*.tmp files, Antigravity.tmp)
- **UNKNOWN**: Unable to classify

**Usage**:

```java
ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);
String description = processClassifier.getDescription(classification);
```

### 2. ActionSafetyMatrix Service

**File**: `backend/src/main/java/com/aios/backend/service/ActionSafetyMatrix.java`

**Action Safety Decisions by Process Class**:

| Process Class        | Actions                           | Rationale                                     |
| -------------------- | --------------------------------- | --------------------------------------------- |
| **SYSTEM_CRITICAL**  | NONE                              | Core OS function - no automation allowed      |
| **SECURITY_PROCESS** | TRIM_WORKING_SET, REDUCE_PRIORITY | Never restart; use memory/priority management |
| **WINDOWS_SERVICE**  | TRIM_WORKING_SET, REDUCE_PRIORITY | Process restart fails; require SC commands    |
| **USER_PROCESS**     | All actions available             | Full remediation chain enabled                |
| **TEMP_PROCESS**     | NONE                              | Already gone or harmless                      |
| **UNKNOWN**          | TRIM_WORKING_SET, REDUCE_PRIORITY | Conservative non-destructive actions          |

**Recommended Action Chains**:

- **MEMORY_LEAK on Security Process**: TRIM_WORKING_SET → REDUCE_PRIORITY
- **MEMORY_LEAK on Service**: TRIM_WORKING_SET → REDUCE_PRIORITY
- **MEMORY_LEAK on User App**: TRIM_WORKING_SET → REDUCE_PRIORITY → RESTART_PROCESS
- **RESOURCE_HOG**: REDUCE_PRIORITY → TRIM_WORKING_SET → (RESTART if user app)
- **HUNG_PROCESS**: RESTART_PROCESS → REDUCE_PRIORITY → TRIM_WORKING_SET

**Methods**:

```java
// Check if action is safe for process class
ActionDecision canExecute(String actionType, ProcessClass processClass)

// Get recommended action plan for issue type + process class
ActionRecommendation getRecommendedActions(String issueType, ProcessClass processClass)

// Get first safe action from a list
String getFirstSafeAction(List<String> actions, ProcessClass processClass)
```

### 3. Enhanced RuleEngineService

**File**: `backend/src/main/java/com/aios/backend/service/RuleEngineService.java`

**Key Changes**:

#### A. Process Classification in automateAllSafeActiveIssues()

```java
// Classify each issue before automation
ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);

// Skip system-critical processes entirely
if (classification == ProcessClassifier.ProcessClass.SYSTEM_CRITICAL) {
    // Mark as SKIPPED_PROTECTED
    continue;
}

// Skip ephemeral processes
if (classification == ProcessClassifier.ProcessClass.TEMP_PROCESS) {
    // Mark as SKIPPED_PROTECTED
    continue;
}
```

#### B. Action Safety Validation

```java
// Validate recommended action is safe for this process class
if (!actionSafetyMatrix.isActionSafeFor(recommendedAction, classification)) {
    // Get safer alternative from action matrix
    ActionSafetyMatrix.ActionRecommendation safer =
        actionSafetyMatrix.getRecommendedActions(issueType, classification);

    if (safer.primaryAction() == null) {
        // Mark as NEEDS_MANUAL_REVIEW
        continue;
    }

    recommendedAction = safer.primaryAction();
}
```

#### C. Intelligent Adaptive Action Planning

```java
private List<String> buildAdaptiveActionPlan(IssueEntity issue, String primaryAction) {
    // Get process classification
    ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);

    // Use ActionSafetyMatrix to determine safe actions for this process type
    ActionSafetyMatrix.ActionRecommendation recommendation =
        actionSafetyMatrix.getRecommendedActions(issueTypeStr, classification);

    // Build plan with only safe actions
    List<String> plan = new ArrayList<>();
    if (recommendation.primaryAction() != null) {
        plan.add(recommendation.primaryAction());
    }

    // Add fallback actions that are safe for this process class
    for (String action : recommendation.fallbackChain()) {
        if (actionSafetyMatrix.isActionSafeFor(action, classification)) {
            plan.add(action);
        }
    }

    return plan;
}
```

#### D. Process-Aware Fallback Mechanism

```java
// Try fallback actions based on process classification
ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);
List<String> actionPlan = buildAdaptiveActionPlan(issue, execution.getActionType());

String fallbackAction = null;
for (String candidateAction : actionPlan) {
    if (!candidateAction.equals(execution.getActionType())
            && actionSafetyMatrix.isActionSafeFor(candidateAction, classification)) {
        fallbackAction = candidateAction;
        break;
    }
}

if (fallbackAction != null) {
    // Attempt fallback action
}
```

### 4. Updated Outcome Categories (5 Categories)

**Updated BulkAutomationResult**:

```java
private int totalActive;
private int resolved;           // Issue resolved via automation
private int automated;          // Action executed (attempted)
private int needsManualReview;  // Process type incompatible with automation
private int skippedProtected;   // System-critical or user-protected
private int failed;             // Unrecoverable failures
```

**Outcome Status Values**:

- `RESOLVED`: Issue successfully remediated and verified
- `AUTOMATED`: Action executed but verification pending
- `NEEDS_MANUAL_REVIEW`: Process classification incompatible with automation (redirect to manual review)
- `SKIPPED_PROTECTED`: System-critical or user-protected process
- `FAILED`: Action execution failed and no safe fallback available
- `NEEDS_ATTENTION`: (legacy, replaced by NEEDS_MANUAL_REVIEW)

### 5. Frontend Update

**Files**:

- `frontend/src/api/rules.ts`: Updated BulkAutomationOutcome and BulkAutomationResult types
- `frontend/src/pages/IssuesPage.tsx`: Updated UI to display all 5 outcome categories

**UI Improvements**:

- Separate cards for Protected, Automated, Needs Manual Review, Failures
- Icons for each status (✓ Resolved, ⚙ Automated, 🔍 Needs Review, ❌ Failed)
- Summary counts include needsManualReview field

---

## Error Recovery Strategy

### RESOURCE_NOT_FOUND (Process Exited)

**Response**: Try TRIM_WORKING_SET or REDUCE_PRIORITY
**Rationale**: Process likely terminated; non-destructive actions safer

### OPERATION_FAILED (OS Rejected)

**Response**:

- For Service/Security processes: Use only TRIM_WORKING_SET, REDUCE_PRIORITY
- For User apps: Retry with RESTART_PROCESS if other actions failed

### SERVICE PROCESS RESTART FAILURE

**Response**: Skip RESTART_PROCESS; recommend manual SC restart in UI

---

## Automation Flow Diagram

```
Issue Detected
    ↓
Classify Process (ProcessClassifier)
    ↓
┌─────────────────────────────────────────────┐
│ SYSTEM_CRITICAL / TEMP_PROCESS?            │─→ SKIP (Protected)
└─────────────────────────────────────────────┘
    ↓ NO
Evaluate Issue (RuleEvaluationService)
    ↓
Get Recommended Action
    ↓
Check Action Safety (ActionSafetyMatrix)
    ↓
┌──────────────────────────┐
│ Safe for this process?   │
├──────────────────────────┤
│ YES → Execute             │
│ NO  → NEEDS_MANUAL_REVIEW │
└──────────────────────────┘
    ↓ YES
Execute Action
    ↓
┌──────────────────────────┐
│ Success?                 │
├──────────────────────────┤
│ YES → RESOLVED            │
│ NO  → Try Fallback        │
└──────────────────────────┘
    ↓ NO
Build Adaptive Action Plan (process-aware)
    ↓
Try Next Safe Action in Plan
    ↓
┌──────────────────────────┐
│ Fallback Success?        │
├──────────────────────────┤
│ YES → AUTOMATED           │
│ NO  → FAILED              │
└──────────────────────────┘
```

---

## Configuration & Customization

### Adding Protected Processes

Edit `ProcessClassifier.java`:

```java
private static final String[] SYSTEM_CRITICAL_PROCESSES = {
    "csrss.exe",
    "services.exe",
    // Add new entries here
};
```

### Customizing Action Chains

Edit `ActionSafetyMatrix.java`:

```java
private ActionRecommendation buildServiceActionPlan(String issueType) {
    if ("MEMORY_LEAK".equals(issueType)) {
        return new ActionRecommendation(
            "TRIM_WORKING_SET",  // Primary
            List.of("REDUCE_PRIORITY"),  // Fallbacks
            "Service memory leak strategy"
        );
    }
    // Add custom logic here
}
```

---

## Testing Recommendations

### Unit Tests

1. Process classification correctness
   - Verify all known processes classified correctly
   - Verify unknown processes classified conservatively

2. Action safety validation
   - Verify SYSTEM_CRITICAL processes block all actions
   - Verify SECURITY_PROCESS allows only non-destructive actions
   - Verify USER_PROCESS allows all actions

3. Adaptive action planning
   - Verify fallback chain respects process class constraints
   - Verify primary action preferred when safe

### Integration Tests

1. Bulk automation workflow
   - Verify system-critical processes skipped
   - Verify security processes use safe actions
   - Verify service processes avoid restart

2. Error recovery
   - Verify fallback actions attempted on failure
   - Verify NEEDS_MANUAL_REVIEW assigned when no safe action available

3. Outcome reporting
   - Verify all 5 outcome categories properly populated
   - Verify summary counts accurate

---

## Success Metrics

**Expected Improvements**:

- ✅ Failure rate reduced from 73% (14/19) to < 10%
- ✅ Zero automation failures on system-critical processes
- ✅ Security processes remediated safely (non-destructive actions only)
- ✅ Service processes routed to manual review instead of attempting restart
- ✅ Intelligent fallback reduces hard failures
- ✅ Clear distinction between "skipped" and "failed" outcomes

---

## Deployment Checklist

- [x] ProcessClassifier service created
- [x] ActionSafetyMatrix service created
- [x] RuleEngineService refactored to use classification
- [x] BulkAutomationResult DTO updated with needsManualReview field
- [x] Frontend types updated (rules.ts)
- [x] Frontend UI updated (IssuesPage.tsx) to show 5 outcome categories
- [x] Backend compilation successful
- [x] Frontend build successful
- [ ] Deploy to staging environment
- [ ] Run integration tests
- [ ] Monitor automation success rates in production
- [ ] Gather user feedback on NEEDS_MANUAL_REVIEW outcomes

---

## Future Enhancements

1. **Process Family Customization**: Add configuration for process-specific fallback chains
2. **MCP Tool Enhancement**: Implement Windows service restart via SC commands
3. **Machine Learning**: Learn optimal action sequences from historical outcomes
4. **Approval Workflow**: Require approval for system-critical process monitoring
5. **Audit Trail**: Detailed logging of classification decisions and action chains
