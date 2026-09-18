package cz.trixi.ruian.xml;

import cz.trixi.ruian.model.CastObce;
import cz.trixi.ruian.model.Obec;
import cz.trixi.ruian.model.RuianData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuianXmlParserTest {

    private static final String POINT_WKT = "POINT(-679188.00 -1024096.00)";
    private static final String BOUNDARY_WKT = "MULTIPOLYGON(((-679200.00 -1024100.00,-679100.00 -1024100.00,"
            + "-679100.00 -1024000.00,-679200.00 -1024000.00,-679200.00 -1024100.00)))";

    private final RuianXmlParser parser = new RuianXmlParser();

    @Test
    void parsesObecAndCastiObciFromDirectChildrenOnly() throws IOException {
        RuianData data;
        try (InputStream in = getClass().getResourceAsStream("/kopidlno-sample.xml")) {
            data = parser.parse(in);
        }

        assertThat(data.obce()).containsExactly(new Obec(573060, "Kopidlno", POINT_WKT, BOUNDARY_WKT));
        assertThat(data.castiObci()).containsExactly(
                new CastObce(69299, "Kopidlno", 573060, POINT_WKT),
                // the second cast obce has no geometry in the sample
                new CastObce(69302, "Ledkov", 573060, null));
    }

    @Test
    void parsesObecWithoutGeometry() {
        String xml = """
                <vf:VymennyFormat xmlns:vf="urn:cz:isvs:ruian:schemas:VymennyFormatTypy:v1"
                                  xmlns:obi="urn:cz:isvs:ruian:schemas:ObecIntTypy:v1">
                    <vf:Obec><obi:Kod>573060</obi:Kod><obi:Nazev>Kopidlno</obi:Nazev></vf:Obec>
                </vf:VymennyFormat>
                """;

        RuianData data = parser.parse(stream(xml));

        assertThat(data.obce()).containsExactly(new Obec(573060, "Kopidlno", null, null));
    }

    @Test
    void skipsBoundaryWithCircularArcs() {
        String xml = """
                <vf:VymennyFormat xmlns:vf="urn:cz:isvs:ruian:schemas:VymennyFormatTypy:v1"
                                  xmlns:obi="urn:cz:isvs:ruian:schemas:ObecIntTypy:v1"
                                  xmlns:gml="http://www.opengis.net/gml/3.2">
                    <vf:Obec>
                        <obi:Kod>573060</obi:Kod>
                        <obi:Nazev>Kopidlno</obi:Nazev>
                        <obi:Geometrie>
                            <obi:OriginalniHranice>
                                <gml:MultiSurface>
                                    <gml:surfaceMember>
                                        <gml:Polygon>
                                            <gml:exterior>
                                                <gml:Ring>
                                                    <gml:curveMember>
                                                        <gml:Curve>
                                                            <gml:segments>
                                                                <gml:ArcString>
                                                                    <gml:posList>0 0 1 1 2 0</gml:posList>
                                                                </gml:ArcString>
                                                            </gml:segments>
                                                        </gml:Curve>
                                                    </gml:curveMember>
                                                </gml:Ring>
                                            </gml:exterior>
                                        </gml:Polygon>
                                    </gml:surfaceMember>
                                </gml:MultiSurface>
                            </obi:OriginalniHranice>
                        </obi:Geometrie>
                    </vf:Obec>
                </vf:VymennyFormat>
                """;

        RuianData data = parser.parse(stream(xml));

        // WKT cannot express arcs, the boundary is skipped instead of being distorted
        assertThat(data.obce()).containsExactly(new Obec(573060, "Kopidlno", null, null));
    }

    @Test
    void failsWhenRequiredElementIsMissing() {
        String xml = """
                <vf:VymennyFormat xmlns:vf="urn:cz:isvs:ruian:schemas:VymennyFormatTypy:v1"
                                  xmlns:coi="urn:cz:isvs:ruian:schemas:CastObceIntTypy:v1">
                    <vf:CastObce><coi:Kod>69299</coi:Kod><coi:Nazev>Kopidlno</coi:Nazev></vf:CastObce>
                </vf:VymennyFormat>
                """;

        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(RuianParseException.class)
                .hasMessageContaining("coi:Obec/obi:Kod");
    }

    @Test
    void failsOnInvalidCode() {
        String xml = """
                <vf:VymennyFormat xmlns:vf="urn:cz:isvs:ruian:schemas:VymennyFormatTypy:v1"
                                  xmlns:obi="urn:cz:isvs:ruian:schemas:ObecIntTypy:v1">
                    <vf:Obec><obi:Kod>abc</obi:Kod><obi:Nazev>Kopidlno</obi:Nazev></vf:Obec>
                </vf:VymennyFormat>
                """;

        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(RuianParseException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void ignoresElementsWithSameLocalNameInOtherNamespace() {
        String xml = """
                <vf:VymennyFormat xmlns:vf="urn:cz:isvs:ruian:schemas:VymennyFormatTypy:v1"
                                  xmlns:x="urn:example">
                    <x:Obec><x:Kod>1</x:Kod><x:Nazev>Other</x:Nazev></x:Obec>
                </vf:VymennyFormat>
                """;

        RuianData data = parser.parse(stream(xml));

        assertThat(data.obce()).isEmpty();
        assertThat(data.castiObci()).isEmpty();
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
