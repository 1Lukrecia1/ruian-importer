package cz.trixi.ruian.model;

import java.util.List;

/**
 * Data extracted from a RÚIAN exchange format (VFR) file.
 */
public record RuianData(List<Obec> obce, List<CastObce> castiObci) {

    public RuianData {
        obce = List.copyOf(obce);
        castiObci = List.copyOf(castiObci);
    }
}
