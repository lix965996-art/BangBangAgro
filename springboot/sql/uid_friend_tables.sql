ALTER TABLE `sys_user`
  ADD COLUMN IF NOT EXISTS `uid` varchar(20) DEFAULT NULL UNIQUE;

ALTER TABLE `chat_group`
  ADD COLUMN IF NOT EXISTS `group_number` varchar(10) DEFAULT NULL UNIQUE;

CREATE TABLE IF NOT EXISTS `friendship` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `friend_id` int NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS fill_user_uid;
DELIMITER $$
CREATE PROCEDURE fill_user_uid()
BEGIN
  DECLARE done int DEFAULT 0;
  DECLARE uid_val varchar(20);
  DECLARE v_id int;
  DECLARE cur CURSOR FOR SELECT id FROM sys_user WHERE uid IS NULL OR uid = '';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_id;
    IF done THEN
      LEAVE read_loop;
    END IF;
    REPEAT
      SET uid_val = LPAD(FLOOR(100000 + RAND() * 900000), 6, '0');
    UNTIL (SELECT COUNT(*) FROM sys_user WHERE uid = uid_val) = 0 END REPEAT;
    UPDATE sys_user SET uid = uid_val WHERE id = v_id;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL fill_user_uid();
DROP PROCEDURE IF EXISTS fill_user_uid;

DROP PROCEDURE IF EXISTS fill_group_number;
DELIMITER $$
CREATE PROCEDURE fill_group_number()
BEGIN
  DECLARE done int DEFAULT 0;
  DECLARE group_num varchar(10);
  DECLARE v_id int;
  DECLARE cur CURSOR FOR SELECT id FROM chat_group WHERE group_number IS NULL OR group_number = '';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_id;
    IF done THEN
      LEAVE read_loop;
    END IF;
    REPEAT
      SET group_num = LPAD(FLOOR(1000000000 + RAND() * 9000000000), 10, '0');
    UNTIL (SELECT COUNT(*) FROM chat_group WHERE group_number = group_num) = 0 END REPEAT;
    UPDATE chat_group SET group_number = group_num WHERE id = v_id;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL fill_group_number();
DROP PROCEDURE IF EXISTS fill_group_number;
