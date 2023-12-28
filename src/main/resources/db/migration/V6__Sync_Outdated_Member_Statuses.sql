UPDATE members
SET member_status = 'AUTO_LEFT'
WHERE
    member_status = 'ACTIVE'
  AND EXISTS (
    SELECT 1
    FROM groups
    WHERE
        groups.id = members.group_id
      AND groups.status != 'ACTIVE'
);
