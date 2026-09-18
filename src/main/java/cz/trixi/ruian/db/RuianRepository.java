package cz.trixi.ruian.db;

import cz.trixi.ruian.model.CastObce;
import cz.trixi.ruian.model.Obec;
import cz.trixi.ruian.model.RuianData;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Stores and reads obce and casti obci. Existing rows (matched by kod) are updated on save,
 * so the import can be run repeatedly.
 * <p>
 * Geometries are stored as delivered by RÚIAN (EPSG:5514) and read back as GeoJSON
 * transformed to WGS 84 (EPSG:4326), which is what map clients expect.
 */
@Repository
public class RuianRepository {

    private static final int SRID_RUIAN = 5514;
    private static final int SRID_WGS84 = 4326;

    private static final RowMapper<Obec> OBEC_MAPPER =
            (rs, rowNum) -> new Obec(rs.getInt("kod"), rs.getString("nazev"));
    private static final RowMapper<CastObce> CAST_OBCE_MAPPER =
            (rs, rowNum) -> new CastObce(rs.getInt("kod"), rs.getString("nazev"), rs.getInt("kod_obce"));

    private final JdbcClient jdbc;

    public RuianRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void save(RuianData data) {
        // obce first, casti obci reference them via a foreign key
        data.obce().forEach(this::save);
        data.castiObci().forEach(this::save);
    }

    public List<Obec> findAllObce() {
        return jdbc.sql("SELECT kod, nazev FROM obec ORDER BY nazev, kod")
                .query(OBEC_MAPPER)
                .list();
    }

    public Optional<Obec> findObec(int kod) {
        return jdbc.sql("SELECT kod, nazev FROM obec WHERE kod = :kod")
                .param("kod", kod)
                .query(OBEC_MAPPER)
                .optional();
    }

    public List<CastObce> findAllCastiObci() {
        return jdbc.sql("SELECT kod, nazev, kod_obce FROM cast_obce ORDER BY nazev, kod")
                .query(CAST_OBCE_MAPPER)
                .list();
    }

    public List<CastObce> findCastiObciByObec(int kodObce) {
        return jdbc.sql("SELECT kod, nazev, kod_obce FROM cast_obce WHERE kod_obce = :kodObce ORDER BY nazev, kod")
                .param("kodObce", kodObce)
                .query(CAST_OBCE_MAPPER)
                .list();
    }

    public Optional<CastObce> findCastObce(int kod) {
        return jdbc.sql("SELECT kod, nazev, kod_obce FROM cast_obce WHERE kod = :kod")
                .param("kod", kod)
                .query(CAST_OBCE_MAPPER)
                .optional();
    }

    /**
     * @return definition point of the obec as GeoJSON in WGS 84, empty when the obec does not exist
     * or has no geometry
     */
    public Optional<String> findObecDefinicniBodGeoJson(int kod) {
        return findGeoJson("obec", "definicni_bod", kod);
    }

    public Optional<String> findObecHraniceGeoJson(int kod) {
        return findGeoJson("obec", "hranice", kod);
    }

    public Optional<String> findCastObceDefinicniBodGeoJson(int kod) {
        return findGeoJson("cast_obce", "definicni_bod", kod);
    }

    /**
     * @return GeoJSON FeatureCollection with the definition points of all casti obci of the given obec
     */
    public String findCastiObciGeoJson(int kodObce) {
        return jdbc.sql("""
                        SELECT json_build_object(
                                   'type', 'FeatureCollection',
                                   'features', COALESCE(json_agg(
                                       json_build_object(
                                           'type', 'Feature',
                                           'properties', json_build_object('kod', kod, 'nazev', nazev, 'kodObce', kod_obce),
                                           'geometry', ST_AsGeoJSON(ST_Transform(definicni_bod, :sridWgs84))::json)
                                       ) FILTER (WHERE definicni_bod IS NOT NULL), '[]'::json)
                               )::text
                          FROM cast_obce
                         WHERE kod_obce = :kodObce
                        """)
                .param("kodObce", kodObce)
                .param("sridWgs84", SRID_WGS84)
                .query(String.class)
                .single();
    }

    private Optional<String> findGeoJson(String table, String column, int kod) {
        // table and column are constants from this class, never user input
        return jdbc.sql("SELECT ST_AsGeoJSON(ST_Transform(" + column + ", :sridWgs84)) FROM " + table
                        + " WHERE kod = :kod AND " + column + " IS NOT NULL")
                .param("kod", kod)
                .param("sridWgs84", SRID_WGS84)
                .query(String.class)
                .optional();
    }

    private void save(Obec obec) {
        int updated = jdbc.sql("""
                        UPDATE obec
                           SET nazev = :nazev,
                               definicni_bod = ST_GeomFromText(:definicniBod, :srid),
                               hranice = ST_GeomFromText(:hranice, :srid)
                         WHERE kod = :kod
                        """)
                .param("kod", obec.kod())
                .param("nazev", obec.nazev())
                .param("definicniBod", obec.definicniBodWkt())
                .param("hranice", obec.hraniceWkt())
                .param("srid", SRID_RUIAN)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO obec (kod, nazev, definicni_bod, hranice)
                            VALUES (:kod, :nazev, ST_GeomFromText(:definicniBod, :srid), ST_GeomFromText(:hranice, :srid))
                            """)
                    .param("kod", obec.kod())
                    .param("nazev", obec.nazev())
                    .param("definicniBod", obec.definicniBodWkt())
                    .param("hranice", obec.hraniceWkt())
                    .param("srid", SRID_RUIAN)
                    .update();
        }
    }

    private void save(CastObce castObce) {
        int updated = jdbc.sql("""
                        UPDATE cast_obce
                           SET nazev = :nazev,
                               kod_obce = :kodObce,
                               definicni_bod = ST_GeomFromText(:definicniBod, :srid)
                         WHERE kod = :kod
                        """)
                .param("kod", castObce.kod())
                .param("nazev", castObce.nazev())
                .param("kodObce", castObce.kodObce())
                .param("definicniBod", castObce.definicniBodWkt())
                .param("srid", SRID_RUIAN)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO cast_obce (kod, nazev, kod_obce, definicni_bod)
                            VALUES (:kod, :nazev, :kodObce, ST_GeomFromText(:definicniBod, :srid))
                            """)
                    .param("kod", castObce.kod())
                    .param("nazev", castObce.nazev())
                    .param("kodObce", castObce.kodObce())
                    .param("definicniBod", castObce.definicniBodWkt())
                    .param("srid", SRID_RUIAN)
                    .update();
        }
    }
}
