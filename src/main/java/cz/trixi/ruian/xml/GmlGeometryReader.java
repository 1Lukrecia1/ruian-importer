package cz.trixi.ruian.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Converts the GML geometry of RÚIAN elements to WKT, which PostGIS can read directly
 * ({@code ST_GeomFromText}). Coordinates are kept as they are in the file, i.e. in EPSG:5514.
 * <p>
 * Every method is positioned on the start element of a geometry container (e.g. {@code obi:DefinicniBod})
 * and consumes it including its end tag.
 */
final class GmlGeometryReader {

    static final String NS_GML = "http://www.opengis.net/gml/3.2";

    private static final QName GML_POS = new QName(NS_GML, "pos");
    private static final QName GML_POS_LIST = new QName(NS_GML, "posList");
    private static final QName GML_POLYGON = new QName(NS_GML, "Polygon");
    private static final QName GML_ARC_STRING = new QName(NS_GML, "ArcString");
    private static final QName GML_CURVE = new QName(NS_GML, "Curve");

    private GmlGeometryReader() {
    }

    /**
     * Reads the first point of the element, both {@code gml:Point} and {@code gml:MultiPoint} are supported
     * (obce use a multipoint, casti obci a plain point).
     *
     * @return WKT of the point or {@code null} when the element contains no coordinates
     */
    static String readPointWkt(XMLStreamReader reader) throws XMLStreamException {
        String position = null;
        int depth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == START_ELEMENT) {
                if (position == null && GML_POS.equals(reader.getName())) {
                    position = reader.getElementText().trim();
                    continue;
                }
                depth++;
            } else if (event == END_ELEMENT) {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
        }
        return position == null ? null : "POINT(" + position + ")";
    }

    /**
     * Reads {@code gml:MultiSurface} / {@code gml:Polygon} rings.
     *
     * @return WKT of the multipolygon, or {@code null} when there is no polygon or when the boundary
     * uses circular arcs, which WKT cannot express
     */
    static String readMultiPolygonWkt(XMLStreamReader reader) throws XMLStreamException {
        List<String> polygons = new ArrayList<>();
        List<String> rings = null;
        boolean unsupported = false;
        int depth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == START_ELEMENT) {
                QName name = reader.getName();
                if (GML_POS_LIST.equals(name)) {
                    if (rings != null) {
                        rings.add("(" + toCoordinateList(reader.getElementText()) + ")");
                    }
                    continue;
                }
                if (GML_POLYGON.equals(name)) {
                    rings = new ArrayList<>();
                } else if (GML_ARC_STRING.equals(name) || GML_CURVE.equals(name)) {
                    unsupported = true;
                }
                depth++;
            } else if (event == END_ELEMENT) {
                if (depth == 0) {
                    break;
                }
                if (GML_POLYGON.equals(reader.getName()) && rings != null) {
                    // in GML the exterior ring comes first, which is also what WKT expects
                    polygons.add("(" + String.join(",", rings) + ")");
                    rings = null;
                }
                depth--;
            }
        }
        if (unsupported || polygons.isEmpty()) {
            return null;
        }
        return "MULTIPOLYGON(" + String.join(",", polygons) + ")";
    }

    /**
     * {@code "x1 y1 x2 y2"} (GML) to {@code "x1 y1,x2 y2"} (WKT).
     */
    private static String toCoordinateList(String posList) {
        String[] values = posList.trim().split("\\s+");
        if (values.length % 2 != 0) {
            throw new RuianParseException("Odd number of coordinates in gml:posList");
        }
        StringJoiner coordinates = new StringJoiner(",");
        for (int i = 0; i < values.length; i += 2) {
            coordinates.add(values[i] + " " + values[i + 1]);
        }
        return coordinates.toString();
    }
}
