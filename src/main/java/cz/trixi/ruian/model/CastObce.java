package cz.trixi.ruian.model;

/**
 * @param definicniBodWkt definition point as WKT in EPSG:5514, {@code null} when the XML has no geometry
 */
public record CastObce(int kod, String nazev, int kodObce, String definicniBodWkt) {

    public CastObce(int kod, String nazev, int kodObce) {
        this(kod, nazev, kodObce, null);
    }
}
