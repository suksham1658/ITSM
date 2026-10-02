# Workflows — hierarchy & flow (Service Request / Incident / Security)

The portal is **engine-driven**, not hard-coded `if/else`. When a ticket is submitted, `WorkflowEngine.startOnSubmit()`
asks `WorkflowMatcherService` which **workflow definition** applies (the *rule matrix*), copies that definition's
**stage templates** into per-ticket **instance stages**, and walks them one at a time. The whole thing lives in
`com.nbfc.itsm.workflow.WorkflowEngine`.

---

## 1. The building blocks

### Stage types (`workflow_stage.stage_type`)
| Type | Meaning | Who acts | Allowed actions |
|---|---|---|---|
| `APPROVAL` | A person must approve | the resolved approver (manager/HOD/CISO) or their delegate | `APPROVE`, `REJECT`, `SEND_BACK` (all need remarks) |
| `ASSIGNMENT` | Service-desk / team triage; pick implementor(s) | IT Service Desk / named group | `ASSIGN`, `REASSIGN` |
| `FULFILMENT` | The actual work | the assigned implementor | `ACCEPT`/`START`, `HOLD`, `RESOLVE`, `REASSIGN` |
| `CONFIRMATION` | Requester confirms the fix | the requester only | `APPROVE` ("Resolved"), `SEND_BACK` ("Not resolved", needs reason) |
| `CLOSURE` | System closes the ticket | system (automatic) | `COMPLETE` |

### Actor strategies (`actor_strategy`) — how a stage finds its person
`DYNAMIC_HIERARCHY_TO_HOD` (expands to one approval step per manager up to the HOD) · `NAMED_ROLE` (e.g. CISO) ·
`SERVICE_DESK` / `ASSIGNMENT_GROUP` / `IMPLEMENTOR` (a group) · `REQUESTER` · `SYSTEM` · `LDAP_MANAGER` / `LDAP_HOD`.

### Ticket statuses (`ticket.status_code`)
`Draft` → `Pending Approval` → `Approved` → `Assigned` → `In Progress` → (`On Hold`) → `Resolved` → `Closed`.
Side exits: `Rejected` (approver or admin reject). The current step label is kept in `ticket.progress_code`.

---

## 2. The rule matrix — which workflow a ticket gets

`WorkflowMatcherService.match()` scans active `workflow_rule` rows **lowest `priority` number first** and takes the
first whose `condition_json` matches the ticket (type / category / sub-category / confidentiality). Seeded rules
(`db/migration/V4__master_data.sql`):

| priority | rule | matches when | workflow definition |
|---:|---|---|---|
| 10 | Privileged access requests | sub-category = *Privileged Access Request* | `PRIVILEGED_ACCESS` |
| 20 | Cyber Security category | category = *Cyber Security* | `SECURITY` |
| 21 | Security Incident ticket type | ticket type = *Security Incident* | `SECURITY` |
| 22 | Highly Confidential requests | confidentiality = *Highly Confidential* | `SECURITY` |
| 30 | Incident | ticket type = *Incident* | `INCIDENT_SD_THEN_IMPL` |
| 40 | Service Request default | ticket type = *Service Request* | `SR_CHAIN_TO_HOD_CISO_IMPL` |
| 999 | Catch-all fallback | anything | `SR_CHAIN_TO_HOD_CISO_IMPL` |

> Lower number wins, so a *Security Incident* (21) is routed to `SECURITY` even though rule 30/40 would also match.

---

## 3. The six workflow definitions (seeded stages)

### `SR_CHAIN_TO_HOD_CISO_IMPL` — Service Request (default)
```
10 Reporting hierarchy through HOD   APPROVAL     DYNAMIC_HIERARCHY_TO_HOD
20 CISO approval                     APPROVAL     NAMED_ROLE (CISO)
30 Implementor                       FULFILMENT   IMPLEMENTOR (IT Implementors)
40 Requester confirmation            CONFIRMATION REQUESTER
50 Closed                            CLOSURE      SYSTEM
```

### `SR_CHAIN_TO_HOD_IMPL` — Service Request without CISO
```
10 Reporting hierarchy through HOD   APPROVAL     DYNAMIC_HIERARCHY_TO_HOD
20 Implementor                       FULFILMENT   IMPLEMENTOR
30 Requester confirmation            CONFIRMATION REQUESTER
40 Closed                            CLOSURE      SYSTEM
```

### `INCIDENT_SD_THEN_IMPL` — Incident (default)
```
10 IT Service Desk triage            ASSIGNMENT   SERVICE_DESK (IT Service Desk)
20 Implementor                       FULFILMENT   IMPLEMENTOR (IT Implementors)
30 Requester confirmation            CONFIRMATION REQUESTER
40 Closed                            CLOSURE      SYSTEM
```
> Incidents **skip manager approval** — they go straight to the service desk, who assign an implementor.

### `INCIDENT_DIRECT_IMPL` — Incident straight to implementor (**Inactive**)
```
10 Implementor                       FULFILMENT   IMPLEMENTOR
20 Requester confirmation            CONFIRMATION REQUESTER
30 Closed                            CLOSURE      SYSTEM
```
> Has **no rule** pointing at it, so it is never selected. To use it, an admin activates a rule for it in
> Admin → Workflow. (This is why "Incident — direct to Implementor" shows as not in use.)

### `SECURITY` — Security / highly confidential
Same shape as the default SR: hierarchy → CISO → Implementor → confirmation → closed.

### `PRIVILEGED_ACCESS`
```
10 Reporting hierarchy through HOD   APPROVAL     DYNAMIC_HIERARCHY_TO_HOD
20 CISO approval                     APPROVAL     NAMED_ROLE (CISO)
30 IT Security Team                  ASSIGNMENT   ASSIGNMENT_GROUP (IT Security Team)
40 Implementor                       FULFILMENT   IMPLEMENTOR
50 Requester confirmation            CONFIRMATION REQUESTER
60 Closed                            CLOSURE      SYSTEM
```

---

## 4. The reporting-hierarchy step (the "dynamic" part)

A `DYNAMIC_HIERARCHY_TO_HOD` template expands at submit time into **one approval step per manager** in the
requester's reporting line, climbing until the HOD (`WorkflowEngine.managerHops()`):

- Starts at the requester's **manager** and follows each person's manager upward.
- Stops at the **HOD** — either the HOD set on Admin → Users, or the first person whose role is `HOD`.
- If the requester *is* the HOD, there are **no** hierarchy approval steps.
- Capped at `workflow.max-manager-hops` (default 12) to stop loops.
- Every approver in the chain must be portal-active, or submit is blocked with a clear message. If no manager is
  set at all, submit is refused (`NO_MANAGER_CHAIN`) and the requester is told to ask the admin / fix AD.

So two people in different parts of the org get a **different number of approval steps** from the *same* template.

---

## 5. How a ticket moves (actions)

`WorkflowEngine.applyAction()` validates the actor against the current step (`assertCanAct`), then:

- **APPROVE** → step `Completed`, advance to the next step.
- **REJECT** → ticket `Rejected`, remaining steps `Skipped`, requester notified.
- **SEND_BACK** → step goes back to the previous completed step (or the requester if the stage's
  `send_back_target = REQUESTER`). On a confirmation step this also restarts the resolution SLA clock.
- **ASSIGN** (service desk) → choose one or more implementors; the desk step completes and the implementation step
  is handed to them. One person → they own it; several → all see it and the first to ACCEPT/START owns it. Records
  a `ticket_assignment_log` row (who → whom, by whom, with comment). Marks SLA first-response.
- **REASSIGN** (implementor) → hand the work to one other implementor on the same step; requires a comment; logged.
- **ACCEPT / START** → status `In Progress`, SLA clock resumes, first-response marked.
- **HOLD** → status `On Hold`, SLA clock **pauses**.
- **RESOLVE** → step completes, advance; SLA marked resolved (met or breached).
- **COMPLETE** (CLOSURE/SYSTEM) → ticket `Closed`.

**Remarks** are mandatory for `APPROVE`, `REJECT`, `SEND_BACK`, `REASSIGN` and any transition flagged
`remarks_required` — minimum length from `approval.remarks-min-length` (default 10 chars).

**System Administrator override:** `adminReject()` can reject any open ticket at any step (with remarks); it is
audited as `ADMIN_REJECT`. No approving/assigning on anyone else's behalf.

**Delegate (backup approver):** on an `APPROVAL` step only, the approver's delegate (set in Admin → Users) may act;
the action is recorded as `DELEGATE_ACTION` and the remark is prefixed `[On behalf of …]`.

---

## 6. Requester confirmation, auto-close and re-open

After the work is resolved, the `CONFIRMATION` step waits for the requester:

- Controlled by setting `workflow.requester-confirmation` (default **true**). If turned **off**, confirmation steps
  are skipped and the ticket closes as soon as it is resolved.
- If **on**, the ticket sits `Resolved` waiting for the requester. If they don't answer within
  `workflow.confirmation-hours` (default **48h = 2 days**), the `ConfirmationAutoCloseJob` closes it automatically
  (action `AUTO_CLOSE`) and emails the requester a re-open link.
- The requester may **re-open** for `workflow.reopen-hours` (default **48h**) after an auto-close:
  - **"Resolved"** (`confirmAutoClose`) → stays closed for good (`CONFIRMED`).
  - **"Not resolved"** (`reopenAfterAutoClose`) → ticket goes back to the implementation step, a fresh resolution
    SLA window starts, implementor notified.
- If the requester clicks **Confirm "Resolved"** before auto-close, it closes immediately; **"Not resolved"** sends
  it back to the implementor and restarts the resolution clock.

```
Resolved ──(requester: Resolved)──▶ Closed
   │
   ├──(no answer in 48h)──▶ Auto-closed ──(re-open ≤48h: Resolved)──▶ Closed (confirmed)
   │                                     └(re-open ≤48h: Not resolved)▶ back to Implementor
   └──(requester: Not resolved)──▶ back to Implementor (SLA resolution clock restarts)
```

Timings come from `WorkflowEngine.confirmationHours()` / `reopenHours()` and are editable in
Admin → System Configuration (see [CONFIGURATION.md](CONFIGURATION.md)).

---

## 7. Where this is stored at runtime

| Table | Holds |
|---|---|
| `workflow_definition` | the templates (the 6 above) |
| `workflow_stage` | template stages |
| `workflow_stage_transition` | which actions a stage allows + whether remarks are required |
| `workflow_rule` | the rule matrix (condition_json → definition) |
| `workflow_instance` | one row per ticket: which definition/rule, current stage, status |
| `workflow_instance_stage` | the expanded per-ticket steps, each with its resolved approver/group and status |
| `workflow_instance_stage_assignee` | the implementors a desk step was sent to (before one picks it up) |
| `ticket_assignment_log` | every hand-over (service-desk ASSIGN, implementor REASSIGN): from → to, by whom, remark |

See [DATABASE.md](DATABASE.md) for the full table map.
