CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE driver
    ADD COLUMN location geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;

CREATE INDEX idx_driver_location ON driver USING GIST (location);