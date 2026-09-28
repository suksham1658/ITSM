-- AD Account Unlock page: new permission for IT Service Desk and System Administrator.
-- Idempotent: inserts only what is missing.

IF NOT EXISTS (SELECT 1 FROM dbo.permission WHERE code = N'AD_ACCOUNT_UNLOCK')
    INSERT INTO dbo.permission (code, description)
    VALUES (N'AD_ACCOUNT_UNLOCK', N'Unlock locked Active Directory accounts');

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM dbo.role r
CROSS JOIN dbo.permission p
WHERE r.code IN (N'IT_SERVICE_DESK', N'SYSTEM_ADMINISTRATOR')
  AND p.code = N'AD_ACCOUNT_UNLOCK'
  AND NOT EXISTS (SELECT 1 FROM dbo.role_permission rp
                  WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);
