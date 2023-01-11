
-- holds game information for games in progress
CREATE TABLE IF NOT EXISTS games_in_progress
(
    game_id     SERIAL      PRIMARY KEY,
    platform    TEXT,
    player_ids  TEXT[]                        NOT NULL,
    board       BYTEA                         NOT NULL,
    created_at  TIMESTAMPTZ  DEFAULT NOW()    NOT NULL
);

CREATE TABLE IF NOT EXISTS completed_games
(
     game_id INT PRIMARY KEY,
     platform TEXT,
     board BYTEA NOT NULL,
     num_moves INT,
     created_at TIMESTAMPTZ,
     completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS player_game_results
(
    game_id INT,
    player_id TEXT,
    player_number INT
--    points INT,
--    winner BOOL
);

