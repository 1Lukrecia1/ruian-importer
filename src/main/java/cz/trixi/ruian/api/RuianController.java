package cz.trixi.ruian.api;

import cz.trixi.ruian.db.RuianRepository;
import cz.trixi.ruian.model.CastObce;
import cz.trixi.ruian.model.Obec;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to the imported obce and casti obci, including their geometry as GeoJSON
 * (WGS 84, ready for map clients like Leaflet or OpenLayers).
 */
@RestController
@RequestMapping("/api")
public class RuianController {

    private static final MediaType GEO_JSON = MediaType.valueOf("application/geo+json");

    private final RuianRepository repository;

    public RuianController(RuianRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/obce")
    public List<Obec> obce() {
        return repository.findAllObce();
    }

    @GetMapping("/obce/{kod}")
    public Obec obec(@PathVariable int kod) {
        return repository.findObec(kod).orElseThrow(() -> obecNotFound(kod));
    }

    @GetMapping("/obce/{kod}/casti-obci")
    public List<CastObce> castiObciOfObec(@PathVariable int kod) {
        if (repository.findObec(kod).isEmpty()) {
            throw obecNotFound(kod);
        }
        return repository.findCastiObciByObec(kod);
    }

    @GetMapping("/casti-obci")
    public List<CastObce> castiObci() {
        return repository.findAllCastiObci();
    }

    @GetMapping("/casti-obci/{kod}")
    public CastObce castObce(@PathVariable int kod) {
        return repository.findCastObce(kod).orElseThrow(() -> castObceNotFound(kod));
    }

    @GetMapping("/obce/{kod}/definicni-bod")
    public ResponseEntity<String> obecDefinicniBod(@PathVariable int kod) {
        return geoJson(repository.findObecDefinicniBodGeoJson(kod), "Obec " + kod + " has no definicni bod");
    }

    @GetMapping("/obce/{kod}/hranice")
    public ResponseEntity<String> obecHranice(@PathVariable int kod) {
        return geoJson(repository.findObecHraniceGeoJson(kod), "Obec " + kod + " has no hranice");
    }

    @GetMapping("/casti-obci/{kod}/definicni-bod")
    public ResponseEntity<String> castObceDefinicniBod(@PathVariable int kod) {
        return geoJson(repository.findCastObceDefinicniBodGeoJson(kod), "Cast obce " + kod + " has no definicni bod");
    }

    /**
     * Definition points of all casti obci of the obec as a GeoJSON FeatureCollection.
     */
    @GetMapping("/obce/{kod}/casti-obci/geojson")
    public ResponseEntity<String> castiObciGeoJson(@PathVariable int kod) {
        if (repository.findObec(kod).isEmpty()) {
            throw obecNotFound(kod);
        }
        return ResponseEntity.ok().contentType(GEO_JSON).body(repository.findCastiObciGeoJson(kod));
    }

    private static ResponseEntity<String> geoJson(Optional<String> geoJson, String notFoundMessage) {
        String body = geoJson.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage));
        return ResponseEntity.ok().contentType(GEO_JSON).body(body);
    }

    private static ResponseStatusException obecNotFound(int kod) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Obec " + kod + " not found");
    }

    private static ResponseStatusException castObceNotFound(int kod) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cast obce " + kod + " not found");
    }
}
