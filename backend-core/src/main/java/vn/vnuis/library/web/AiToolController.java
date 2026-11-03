package vn.vnuis.library.web;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vnuis.library.domain.Enums.BookingSource;
import vn.vnuis.library.domain.Enums.SeatType;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.repository.HourlyOccupancyStatRepository;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatQueryService;
import vn.vnuis.library.seat.SeatStateMachine;
import vn.vnuis.library.seat.ZoneInfo;
import vn.vnuis.library.security.AuthUser;
import vn.vnuis.library.web.dto.Dtos.AiReserveRequest;
import vn.vnuis.library.web.dto.Dtos.HourlyStatView;
import vn.vnuis.library.web.dto.Dtos.OccupancyView;
import vn.vnuis.library.web.dto.Dtos.ReservationView;
import vn.vnuis.library.web.dto.Dtos.SeatRecommendation;

/**
 * Endpoints behind the chatbot's tools. ai-service forwards the student's own JWT,
 * so a booking is always made for the signed-in student - never for a user id the
 * LLM picked.
 * <ul>
 *   <li>{@code find_optimal_seats} -> GET /optimal-seats</li>
 *   <li>{@code get_occupancy_forecast} -> GET /occupancy (live + last 24 h; the forecast itself comes from ai-service)</li>
 *   <li>{@code reserve_seat_for_user} -> POST /reservations</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai-tools")
public class AiToolController {

    private final SeatQueryService queries;
    private final SeatStateMachine stateMachine;
    private final SeatCatalog catalog;
    private final HourlyOccupancyStatRepository stats;

    public AiToolController(SeatQueryService queries, SeatStateMachine stateMachine, SeatCatalog catalog,
                            HourlyOccupancyStatRepository stats) {
        this.queries = queries;
        this.stateMachine = stateMachine;
        this.catalog = catalog;
        this.stats = stats;
    }

    @GetMapping("/optimal-seats")
    public List<SeatRecommendation> optimalSeats(@RequestParam(required = false) String zonePreference,
                                                 @RequestParam(required = false) Boolean isQuietZone,
                                                 @RequestParam(required = false) Boolean hasPowerOutlet,
                                                 @RequestParam(required = false) SeatType seatType,
                                                 @RequestParam(defaultValue = "5") int limit) {
        return queries.recommend(zonePreference, isQuietZone, hasPowerOutlet, seatType, limit);
    }

    @GetMapping("/occupancy")
    public List<OccupancyView> occupancy(@RequestParam(required = false) String zoneCode) {
        Instant to = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant from = to.minus(24, ChronoUnit.HOURS);
        List<ZoneInfo> zones = zoneCode == null
                ? List.copyOf(catalog.zones())
                : List.of(catalog.zone(zoneCode)
                        .orElseThrow(() -> ApiException.notFound("ZONE_NOT_FOUND", "Zone " + zoneCode + " does not exist")));
        return zones.stream().map(z -> new OccupancyView(
                queries.zone(z.code()).orElseThrow(),
                stats.findByZoneIdAndBucketHourBetweenOrderByBucketHour(z.zoneId(), from, to).stream()
                        .map(s -> new HourlyStatView(z.code(), s.getBucketHour(), s.getAvgOccupancyRate().doubleValue(),
                                s.getDayOfWeek(), s.isWeekend(), s.isExamPeriod()))
                        .toList()))
                .toList();
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationView> reserve(@AuthenticationPrincipal AuthUser auth,
                                                   @Valid @RequestBody AiReserveRequest request) {
        Reservation r = stateMachine.reserve(auth.id(), request.seatCode(), BookingSource.AI_CHATBOT);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationView.of(r, catalog.requireById(r.getSeatId())));
    }
}
