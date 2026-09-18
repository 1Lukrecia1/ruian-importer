-- Database schema for the RÚIAN importer.
-- Created manually (or automatically by the PostGIS container in compose.yaml);
-- the application itself does not create it.

CREATE EXTENSION IF NOT EXISTS postgis;

-- geometries are stored as delivered by RÚIAN: S-JTSK / Krovak East North (EPSG:5514),
-- the API transforms them to WGS 84 (EPSG:4326)
CREATE TABLE obec (
    kod           INTEGER      NOT NULL PRIMARY KEY,
    nazev         VARCHAR(255) NOT NULL,
    definicni_bod geometry(Point, 5514),
    hranice       geometry(MultiPolygon, 5514)
);

CREATE TABLE cast_obce (
    kod           INTEGER      NOT NULL PRIMARY KEY,
    nazev         VARCHAR(255) NOT NULL,
    kod_obce      INTEGER      NOT NULL REFERENCES obec (kod),
    definicni_bod geometry(Point, 5514)
);

CREATE INDEX ix_cast_obce_kod_obce ON cast_obce (kod_obce);
CREATE INDEX ix_obec_definicni_bod ON obec USING GIST (definicni_bod);
CREATE INDEX ix_cast_obce_definicni_bod ON cast_obce USING GIST (definicni_bod);
