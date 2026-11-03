package vn.vnuis.library.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vnuis.library.domain.Enums.SeatType;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.seat.SeatQueryService;
import vn.vnuis.library.seat.SeatQueryService.SeatFilter;
import vn.vnuis.library.web.dto.Dtos.SeatRecommendation;
import vn.vnuis.library.web.dto.Dtos.SeatView;
import vn.vnuis.library.web.dto.Dtos.ZoneView;

/** Public read API for the live seat map. */
@RestController
@RequestMapping("/api/v1")
public class SeatController {

    private final SeatQueryService queries;

    public SeatController(SeatQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/zones")
    public List<ZoneView> zones() {
        return queries.zones();
    }

    @GetMapping("/seats")
    public List<SeatView> seats(@RequestParam(required = false) String zone,
                                @RequestParam(required = false) Boolean quiet,
                                @RequestParam(required = false) Boolean power,
                                @RequestParam(required = false) SeatType type,
                                @RequestParam(required = false) SeatStatus status) {
        return queries.seats(new SeatFilter(zone, quiet, power, type, status));
    }

    @GetMapping("/seats/recommendations")
    public List<SeatRecommendation> recommendations(@RequestParam(required = false) String zonePreference,
                                                    @RequestParam(required = false) Boolean isQuietZone,
                                                    @RequestParam(required = false) Boolean hasPowerOutlet,
                                                    @RequestParam(required = false) SeatType seatType,
                                                    @RequestParam(defaultValue = "5") int limit) {
        return queries.recommend(zonePreference, isQuietZone, hasPowerOutlet, seatType, limit);
    }

    @GetMapping("/seats/{seatCode}")
    public SeatView seat(@PathVariable String seatCode) {
        return queries.seat(seatCode);
    }
}
