-- ASHEN WARDEN: THE LAST EMBER
-- V2: weapon tempering. One row per save + item code; the tier is the number
-- of times that weapon has been tempered at a sanctuary (see WeaponUpgrader).

CREATE TABLE IF NOT EXISTS weapon_upgrades (
  save_id      INT NOT NULL,
  item_code    VARCHAR(60) NOT NULL,
  upgrade_tier INT NOT NULL DEFAULT 1 CHECK (upgrade_tier >= 1 AND upgrade_tier <= 99),
  PRIMARY KEY (save_id, item_code),
  KEY idx_upgrades_save (save_id),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
