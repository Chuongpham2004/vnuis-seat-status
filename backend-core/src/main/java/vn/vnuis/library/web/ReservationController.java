package vn.vnuis.library.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vnuis.library.domain.Enums.BookingSource;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.repository.ReservationRepository;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatStateMachine;
import vn.vnuis.library.security.AuthUser;
import vn.vnuis.library.web.dto.Dtos.CheckInRequest;
import vn.vnuis.library.web.dto.Dtos.CreateReservationRequest;
import vn.vnuis.library.web.dto.Dtos.ReservationView;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final SeatStateMachine stateMachine;
    private final ReservationRepository reservations;
    private final SeatCatalog catalog;

    public ReservationController(SeatStateMachine stateMachine, ReservationRepository reservations, SeatCatalog catalog) {
        this.stateMachine = stateMachine;
        this.reservations = reservations;
        this.catalog = catalog;
    }

    /** 201 / 409 SEAT_NOT_AVAILABLE|SEAT_BUSY|USER_HAS_ACTIVE_RESERVATION / 403 USER_BANNED|INSUFFICIENT_CREDIT */
    @PostMapping
    public ResponseEntity<ReservationView> create(@AuthenticationPrincipal AuthUser auth,
                                                  @Valid @RequestBody CreateReservationRequest request) {
        BookingSource source = request.source() == null ? BookingSource.APP_MAP : request.source();
        Reservation r = stateMachine.reserve(auth.id(), request.seatCode(), source);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(r));
    }

    /** 200 with the open reservation, or 204 when there is none. */
    @GetMapping("/me/active")
    public ResponseEntity<ReservationView> active(@AuthenticationPrincipal AuthUser auth) {
        return reservations.findFirstByUserIdAndStatusIn(auth.id(), ReservationStatus.OPEN)
                .map(r -> ResponseEntity.ok(view(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/me")
    public List<ReservationView> history(@AuthenticationPrincipal AuthUser auth,
                                         @RequestParam(defaultValue = "20") int limit) {
        return reservations.findByUserIdOrderByReservedAtDesc(auth.id(), PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(this::view).toList();
    }

    /** Dual check-in. 200 / 400 INVALID_QR_CODE / 409 NO_PHYSICAL_PRESENCE / 410 RESERVATION_EXPIRED */
    @PostMapping("/{id}/check-in")
    public ReservationView checkIn(@AuthenticationPrincipal AuthUser auth, @PathVariable UUID id,
                                   @Valid @RequestBody CheckInRequest request) {
        return view(stateMachine.checkIn(auth.id(), id, request.qrCodeToken()));
    }

    @PostMapping("/{id}/check-out")
    public ReservationView checkOut(@AuthenticationPrincipal AuthUser auth, @PathVariable UUID id) {
        return view(stateMachine.checkOut(auth.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public ReservationView cancel(@AuthenticationPrincipal AuthUser auth, @PathVariable UUID id) {
        return view(stateMachine.cancel(auth.id(), id));
    }

    private ReservationView view(Reservation r) {
        return ReservationView.of(r, catalog.requireById(r.getSeatId()));
    }
}
