# SLA — flow, timings and configuration

SLA clocks live in `com.nbfc.itsm.sla.SlaService` (the maths) and `SlaMonitorService` (the every-5-minutes sweep).
Every open ticket has one `ticket_sla` row. All stored times are **UTC**; business-time is calculated in **IST
(Asia/Kolkata)**.

---

## 1. Two clocks per ticket

| Clock | Starts | Target | Marked late when |
|---|---|---|---|
| **Response** | at submit | `response_minutes` for the priority | first desk/implementor action after the response-due time → `response_breached = true` |
| **Resolution** | at submit | `resolution_minutes` for the priority | resolved after the resolve-due time → state `BREACHED` (else `MET`) |

Targets are in **business minutes** unless the policy is 24×7 (see the calendar below). Due time =
start + policy minutes + any minutes the ticket spent on hold.

---

## 2. The policies (per priority)

Seeded in `db/migration/V4__master_data.sql`, table `sla_policy`, **editable** in Admin → SLA:

| Priority | Response | Resolution | 24×7? |
|---|---|---|---|
| Critical | 15 min | 240 min (4 h) | no (business hours) |
| High | 30 min | 480 min (8 h) | no |
| Medium | 120 min (2 h) | 1440 min (24 business-h) | no |
| Low | 240 min (4 h) | 4320 min (72 business-h) | no |

A policy with `is_24x7 = 1` ignores the calendar and counts wall-clock minutes.

## 3. Business calendar & holidays

`business_calendar` (editable in Admin → SLA), seeded as:

| Day | Hours | Working? |
|---|---|---|
| Mon–Fri | 09:00–18:00 IST | yes |
| Sat, Sun | — | no |

Holidays (`holiday`) are skipped too. Seeded: 2026-01-26 Republic Day, 2026-08-15 Independence Day,
2026-10-02 Gandhi Jayanti. `SlaService.businessMinutesBetween()` / `addMinutes()` only count minutes that fall
inside working windows on working days.

---

## 4. Clock lifecycle (what happens on each action)

| Workflow action | SLA effect (`SlaService`) |
|---|---|
| Submit | `startClocks()` — set start, compute response & resolve due, state `WITHIN` |
| First desk/implementor response (ASSIGN / ACCEPT / START / HOLD) | `markFirstResponse()` — record it; flag `response_breached` if late |
| HOLD | `pause(true)` — clock stops; `paused_at_utc` set |
| ACCEPT / START (resume) | `pause(false)` — business minutes on hold added to the due times |
| RESOLVE / auto-close | `markResolved()` — resolve time recorded; state `MET` or `BREACHED` |
| "Not resolved" / re-open | `reopen()` — **fresh resolution window from now** (first response stays recorded) |
| Priority change | `recalculate()` — switch to the new policy, recompute due times from the start (hold time kept) |

## 5. States (`ticket_sla.state_code`)

```
WITHIN ──(≤ near-% of the window left)──▶ NEAR ──(past resolve due)──▶ BREACHED
                                                           │
                                            resolve ──▶ MET (on time) / BREACHED (late)
```
- **NEAR** = at most `sla.near-percent` (default **20%**) of the business-time window remains.
- A paused or resolved clock is left untouched by the refresh.

---

## 6. The monitor (alerts) — `SlaMonitorService`

Runs **every 5 minutes** (`fixedDelay` `itsm.jobs.sla-interval-ms`, default 300000 ms; first run after
`sla-initial-delay-ms`, default 90000 ms). For each open clock it:

1. Refreshes the state (so the dashboard, Escalations and reports are always current) and flags a late first response.
2. Alerts **once per level**:
   - **NEAR** → the people on the current step + the assigned implementor.
   - **BREACHED** → the same people **plus** the IT Service Desk group.
3. Skips tickets that are `Closed`, `Rejected` or `Draft`.

Alerts can be switched off entirely with setting `sla.alerts = false`. `near_alerted_utc` / `breach_alerted_utc`
on `ticket_sla` make sure each alert is sent only once.

---

## 7. Settings (Admin → System Configuration, table `system_setting`)

| Key | Default | Meaning |
|---|---|---|
| `sla.near-percent` | `20` | % of the window left that flips a ticket to NEAR |
| `sla.alerts` | `true` | master on/off for NEAR/BREACHED emails |

SLA **policies, business hours and holidays** are edited on the **Admin → SLA** page, not here (they live in
`sla_policy` / `business_calendar` / `holiday`). See [ENDPOINTS.md](ENDPOINTS.md) → `/admin/sla`.

Related timing settings that interact with the resolution clock live in
[CONFIGURATION.md](CONFIGURATION.md) (`workflow.confirmation-hours`, `workflow.reopen-hours`).

---

## 8. What to change when…

- **"Give Critical tickets 6 hours to resolve"** → Admin → SLA → edit the Critical policy (`resolution_minutes` 360).
- **"We now work Saturdays"** → Admin → SLA → set Saturday as a working day with hours.
- **"Add Diwali as a holiday"** → Admin → SLA → add the holiday date.
- **"Warn us earlier"** → raise `sla.near-percent` (e.g. 30) in System Configuration.
- **"Stop the SLA emails"** → set `sla.alerts = false`.
- **Clocks look stale** → the monitor runs every 5 min; it only runs when `itsm.jobs.enabled = true` (it is in prod;
  off in tests).
