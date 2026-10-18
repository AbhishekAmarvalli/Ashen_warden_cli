-- ASHEN WARDEN: THE LAST EMBER
-- V1: initial schema. All mutable state is keyed by save_id so save slots stay independent.

CREATE TABLE IF NOT EXISTS save_slots (
  save_id      INT AUTO_INCREMENT PRIMARY KEY,
  slot_name    VARCHAR(40)  NOT NULL UNIQUE,
  player_name  VARCHAR(40)  NOT NULL,
  chapter      VARCHAR(60)  NOT NULL,
  location_code VARCHAR(60) NOT NULL,
  sanctuary_code VARCHAR(60) NULL,
  game_version VARCHAR(20)  NOT NULL,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS player_state (
  save_id       INT PRIMARY KEY,
  level         INT NOT NULL DEFAULT 1,
  xp            INT NOT NULL DEFAULT 0,
  hp            INT NOT NULL,
  max_hp        INT NOT NULL,
  stamina       INT NOT NULL,
  max_stamina   INT NOT NULL,
  ash_energy    INT NOT NULL,
  max_ash_energy INT NOT NULL,
  base_attack   INT NOT NULL,
  base_defense  INT NOT NULL,
  fragments     INT NOT NULL DEFAULT 0,
  lost_fragments INT NOT NULL DEFAULT 0,
  lost_fragment_location VARCHAR(60) NULL,
  rng_seed      BIGINT NOT NULL,
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_entries (
  entry_id  INT AUTO_INCREMENT PRIMARY KEY,
  save_id   INT NOT NULL,
  item_code VARCHAR(60) NOT NULL,
  quantity  INT NOT NULL DEFAULT 1 CHECK (quantity >= 0),
  UNIQUE KEY uq_inventory (save_id, item_code),
  KEY idx_inventory_save (save_id),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS equipped_items (
  save_id   INT NOT NULL,
  slot_name VARCHAR(20) NOT NULL,
  item_code VARCHAR(60) NOT NULL,
  PRIMARY KEY (save_id, slot_name),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS discovered_locations (
  save_id       INT NOT NULL,
  location_code VARCHAR(60) NOT NULL,
  PRIMARY KEY (save_id, location_code),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS unlocked_paths (
  save_id   INT NOT NULL,
  path_code VARCHAR(60) NOT NULL,
  PRIMARY KEY (save_id, path_code),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quest_progress (
  save_id         INT NOT NULL,
  quest_code      VARCHAR(60) NOT NULL,
  status          VARCHAR(20) NOT NULL,
  objectives_done TEXT NULL,
  counters        TEXT NULL,
  reward_claimed  TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (save_id, quest_code),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_flags (
  save_id   INT NOT NULL,
  flag_code VARCHAR(60) NOT NULL,
  flag_value VARCHAR(255) NOT NULL,
  PRIMARY KEY (save_id, flag_code),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS acquired_memories (
  save_id     INT NOT NULL,
  memory_code VARCHAR(60) NOT NULL,
  acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (save_id, memory_code),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS defeated_bosses (
  save_id     INT NOT NULL,
  boss_code   VARCHAR(60) NOT NULL,
  defeated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (save_id, boss_code),
  FOREIGN KEY (save_id) REFERENCES save_slots(save_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
