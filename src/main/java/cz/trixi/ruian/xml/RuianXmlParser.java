package cz.trixi.ruian.xml;

import cz.trixi.ruian.model.CastObce;
import cz.trixi.ruian.model.Obec;
import cz.trixi.ruian.model.RuianData;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Streaming (StAX) parser of the RÚIAN exchange format (VFR).
 * <p>
 * Only {@code vf:Obec} and {@code vf:CastObce} elements are read, everything else
 * (addresses, parcels, buildings, ...) is skipped without being kept in memory.
 * Geometries of those two elements are converted to WKT, see {@link GmlGeometryReader}.
 */
@Component
public class RuianXmlParser {

    static final String NS_VF = "urn:cz:isvs:ruian:schemas:VymennyFormatTypy:v1";
    static final String NS_OBI = "urn:cz:isvs:ruian:schemas:ObecIntTypy:v1";
    static final String NS_COI = "urn:cz:isvs:ruian:schemas:CastObceIntTypy:v1";

    private static final QName VF_OBEC = new QName(NS_VF, "Obec");
    private static final QName VF_CAST_OBCE = new QName(NS_VF, "CastObce");
    private static final QName OBI_KOD = new QName(NS_OBI, "Kod");
    private static final QName OBI_NAZEV = new QName(NS_OBI, "Nazev");
    private static final QName OBI_GEOMETRIE = new QName(NS_OBI, "Geometrie");
    private static final QName OBI_DEFINICNI_BOD = new QName(NS_OBI, "DefinicniBod");
    private static final QName OBI_ORIGINALNI_HRANICE = new QName(NS_OBI, "OriginalniHranice");
    private static final QName COI_KOD = new QName(NS_COI, "Kod");
    private static final QName COI_NAZEV = new QName(NS_COI, "Nazev");
    private static final QName COI_OBEC = new QName(NS_COI, "Obec");
    private static final QName COI_GEOMETRIE = new QName(NS_COI, "Geometrie");
    private static final QName COI_DEFINICNI_BOD = new QName(NS_COI, "DefinicniBod");

    private final XMLInputFactory factory;

    public RuianXmlParser() {
        factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        // protection against XXE
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    public RuianData parse(InputStream in) {
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(in);
            List<Obec> obce = new ArrayList<>();
            List<CastObce> castiObci = new ArrayList<>();
            while (reader.hasNext()) {
                if (reader.next() != START_ELEMENT) {
                    continue;
                }
                QName name = reader.getName();
                if (VF_OBEC.equals(name)) {
                    obce.add(readObec(reader));
                } else if (VF_CAST_OBCE.equals(name)) {
                    castiObci.add(readCastObce(reader));
                }
            }
            return new RuianData(obce, castiObci);
        } catch (XMLStreamException e) {
            throw new RuianParseException("Invalid RÚIAN XML: " + e.getMessage(), e);
        } finally {
            closeQuietly(reader);
        }
    }

    private Obec readObec(XMLStreamReader reader) throws XMLStreamException {
        var fields = new Fields();
        readChildren(reader, name -> {
            if (OBI_KOD.equals(name)) {
                fields.kod = readInt(reader);
            } else if (OBI_NAZEV.equals(name)) {
                fields.nazev = reader.getElementText().trim();
            } else if (OBI_GEOMETRIE.equals(name)) {
                readChildren(reader, geometry -> {
                    if (OBI_DEFINICNI_BOD.equals(geometry)) {
                        fields.definicniBodWkt = GmlGeometryReader.readPointWkt(reader);
                    } else if (OBI_ORIGINALNI_HRANICE.equals(geometry)) {
                        fields.hraniceWkt = GmlGeometryReader.readMultiPolygonWkt(reader);
                    } else {
                        return false;
                    }
                    return true;
                });
            } else {
                return false;
            }
            return true;
        });
        return new Obec(
                required(fields.kod, "obi:Kod", "vf:Obec"),
                required(fields.nazev, "obi:Nazev", "vf:Obec"),
                fields.definicniBodWkt,
                fields.hraniceWkt);
    }

    private CastObce readCastObce(XMLStreamReader reader) throws XMLStreamException {
        var fields = new Fields();
        readChildren(reader, name -> {
            if (COI_KOD.equals(name)) {
                fields.kod = readInt(reader);
            } else if (COI_NAZEV.equals(name)) {
                fields.nazev = reader.getElementText().trim();
            } else if (COI_OBEC.equals(name)) {
                readChildren(reader, obecChild -> {
                    if (!OBI_KOD.equals(obecChild)) {
                        return false;
                    }
                    fields.kodObce = readInt(reader);
                    return true;
                });
            } else if (COI_GEOMETRIE.equals(name)) {
                readChildren(reader, geometry -> {
                    if (!COI_DEFINICNI_BOD.equals(geometry)) {
                        return false;
                    }
                    fields.definicniBodWkt = GmlGeometryReader.readPointWkt(reader);
                    return true;
                });
            } else {
                return false;
            }
            return true;
        });
        String context = "vf:CastObce" + (fields.kod != null ? " " + fields.kod : "");
        return new CastObce(
                required(fields.kod, "coi:Kod", context),
                required(fields.nazev, "coi:Nazev", context),
                required(fields.kodObce, "coi:Obec/obi:Kod", context),
                fields.definicniBodWkt);
    }

    /**
     * Iterates over the direct children of the element the reader is positioned on and
     * returns once its end tag is reached.
     * <p>
     * The handler is called for every direct child. When it returns {@code true} it must have
     * consumed the whole child (including its end tag); when it returns {@code false} it must
     * not have moved the reader and the child is skipped.
     */
    private static void readChildren(XMLStreamReader reader, ChildHandler handler) throws XMLStreamException {
        int depth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == START_ELEMENT) {
                if (depth > 0 || !handler.handle(reader.getName())) {
                    depth++;
                }
            } else if (event == END_ELEMENT) {
                if (depth == 0) {
                    return;
                }
                depth--;
            }
        }
        throw new XMLStreamException("Unexpected end of document");
    }

    private static int readInt(XMLStreamReader reader) throws XMLStreamException {
        String text = reader.getElementText().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new XMLStreamException("Invalid code '" + text + "'", reader.getLocation(), e);
        }
    }

    private static <T> T required(T value, String element, String context) {
        if (value == null || value instanceof String s && s.isEmpty()) {
            throw new RuianParseException("Missing " + element + " in " + context);
        }
        return value;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // nothing useful to do
        }
    }

    @FunctionalInterface
    private interface ChildHandler {
        boolean handle(QName name) throws XMLStreamException;
    }

    private static final class Fields {
        Integer kod;
        String nazev;
        Integer kodObce;
        String definicniBodWkt;
        String hraniceWkt;
    }
}
