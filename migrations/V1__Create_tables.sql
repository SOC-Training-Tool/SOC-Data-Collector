
-- holds game information for games in progress
CREATE TABLE IF NOT EXISTS games_in_progress
(
    game_id     SERIAL      PRIMARY KEY,
    platform    TEXT                          NOT NULL,
    player_ids  TEXT[]                        NOT NULL,
    board       BYTEA                         NOT NULL,
    num_moves   INT,
    created_at  TIMESTAMPTZ  DEFAULT NOW()    NOT NULL
);

CREATE TABLE IF NOT EXISTS moves
(
    game_id INT NOT NULL,
    move_number INT,
    move_data BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS completed_games
(
     game_id INT PRIMARY KEY,
     platform TEXT,
     board BYTEA NOT NULL,
     num_moves INT,
     created_at TIMESTAMPTZ NOT NULL,
     completed_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE IF NOT EXISTS player_game_results
(
    game_id INT,
    player_id TEXT,
    player_number INT
--    points INT,
--    winner BOOL
);

