# IT Nexa — Documentation index

Reference docs for the IT Nexa ITSM portal (Spring Boot 2.7 / Java 8 WAR on Tomcat 9, SQL Server, LDAP/AD).

| Doc | What's in it |
|---|---|
| [WORKFLOWS.md](WORKFLOWS.md) | Full ticket hierarchy/flow — Service Request vs Incident vs Security, the 6 workflow templates, stages, actions, rule matrix, confirmation / auto-close / re-open |
| [DATABASE.md](DATABASE.md) | Every table and what lives where ("to find X, look in table Y"), entity→table map, key columns of the central tables |
| [ENDPOINTS.md](ENDPOINTS.md) | Every HTTP endpoint by controller, method, purpose and the authority that guards it — for debugging |
| [SLA.md](SLA.md) | SLA flow and timings, the policies per priority, business calendar, states, the 5-minute monitor, and every SLA setting |
| [CONFIGURATION.md](CONFIGURATION.md) | Versions of everything, config files, env vars, `system_setting` keys, background jobs, deployment layout |
| [LICENSING.md](LICENSING.md) | The 1-year offline signed license — how it behaves, vendor key workflow, renewal |

All endpoint paths in these docs are **relative to the application context root**. In production that root is
`http://<server>:8090/itsm-portal` (standalone Tomcat 9); for local/embedded runs it is `http://localhost:8092`
(see [CONFIGURATION.md](CONFIGURATION.md)).

> These docs describe `main` as of the license-hardening merge. When the schema or flows change, update the
> matching doc in the same commit.
