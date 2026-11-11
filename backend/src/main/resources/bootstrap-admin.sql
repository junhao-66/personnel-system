-- Register through the public registration page first, then replace the username below and execute once.
-- Never expose this statement as an unauthenticated application endpoint.
UPDATE sys_user
SET role_code = 'ADMIN', audit_status = 'APPROVED', user_status = 'ENABLED'
WHERE user_name = 'REPLACE_WITH_YOUR_USERNAME';
