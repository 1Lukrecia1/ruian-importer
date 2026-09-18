package cz.trixi.ruian.model;

/**
 * @param definicniBodWkt definition point as WKT in EPSG:5514, {@code null} when the XML has no geometry
 * @param hraniceWkt      boundary as WKT in EPSG:5514, {@code null} when the XML has no boundary
 *                        (only the "SH" files from ČÚZK contain one)
 */
public record Obec(int kod, String nazev, String definicniBodWkt, String hraniceWkt) {

    public Obec(int kod, String nazev) {
        this(kod, nazev, null, null);
    }
}
