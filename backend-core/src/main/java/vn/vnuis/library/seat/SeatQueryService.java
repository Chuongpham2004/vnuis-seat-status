package vn.vnuis.library.seat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.vnuis.library.domain.Enums.SeatType;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.web.dto.Dtos.SeatRecommendation;
import vn.vnuis.library.web.dto.Dtos.SeatView;
import vn.vnuis.library.web.dto.Dtos.ZoneView;

/** Read side: merges the static catalog with live Redis state. */
@Service
public class SeatQueryService {

    private final SeatCatalog catalog;
    private final SeatStateStore store;

    public SeatQueryService(SeatCatalog catalog, SeatStateStore store) {
        this.catalog = catalog;
        this.store = store;
    }

    public record SeatFilter(String zone, Boolean quiet, Boolean power, SeatType type, SeatStatus status) {
    }

    public List<SeatView> seats(SeatFilter f) {
        List<SeatInfo> candidates = catalog.all().stream()
                .filter(s -> f.zone() == null || s.zoneCode().equalsIgnoreCase(f.zone()))
                .filter(s -> f.quiet() == null || s.quietZone() == f.quiet())
                .filter(s -> f.power() == null || s.hasPowerOutlet() == f.power())
                .filter(s -> f.type() == null || s.seatType() == f.type())
                .toList();
        Map<String, SeatState> live = store.findAll(candidates.stream().map(SeatInfo::seatCode).toList());
        return candidates.stream()
                .map(s -> SeatView.of(s, live.get(s.seatCode())))
                .filter(v -> f.status() == null || v.status() == f.status())
                .toList();
    }

    public SeatView seat(String seatCode) {
        SeatInfo info = catalog.require(seatCode);
        return SeatView.of(info, store.find(info.seatCode()).orElse(null));
    }

    public List<ZoneView> zones() {
        return catalog.zones().stream().map(z -> zone(z.code()).orElseThrow()).toList();
    }

    public Optional<ZoneView> zone(String zoneCode) {
        return catalog.zone(zoneCode).map(z -> {
            List<String> codes = catalog.inZone(z.code()).stream().map(SeatInfo::seatCode).toList();
            Map<SeatStatus, Long> counts = store.findAll(codes).values().stream()
                    .collect(Collectors.groupingBy(SeatState::status, Collectors.counting()));
            int reserved = counts.getOrDefault(SeatStatus.RESERVED, 0L).intValue();
            int occupied = counts.getOrDefault(SeatStatus.OCCUPIED, 0L).intValue();
            int away = counts.getOrDefault(SeatStatus.AWAY, 0L).intValue();
            int total = codes.size();
            int available = total - reserved - occupied - away;
            double rate = total == 0 ? 0 : round((occupied + away) / (double) total);
            return new ZoneView(z.code(), z.name(), z.floorNumber(), z.quietZone(), total,
                    available, reserved, occupied, away, rate);
        });
    }

    /**
     * Ranks AVAILABLE seats for the AI tool {@code find_optimal_seats}.
     * Score: matches of the requested features, plus a bonus for emptier zones (quieter, more choice).
     * Phase 4 adds the forecast for the target hour to this score.
     */
    public List<SeatRecommendation> recommend(String zonePreference, Boolean quiet, Boolean power, SeatType type,
                                              int limit) {
        Map<String, ZoneView> zoneViews = zones().stream().collect(Collectors.toMap(ZoneView::code, Function.identity()));
        List<SeatView> available = seats(new SeatFilter(null, null, null, null, SeatStatus.AVAILABLE));

        List<SeatRecommendation> ranked = new ArrayList<>();
        for (SeatView s : available) {
            // Hard filters: explicit "true" requirements must be met.
            if (Boolean.TRUE.equals(power) && !s.hasPowerOutlet()) {
                continue;
            }
            if (Boolean.TRUE.equals(quiet) && !s.quietZone()) {
                continue;
            }
            if (type != null && s.seatType() != type) {
                continue;
            }
            double zoneRate = zoneViews.get(s.zoneCode()).occupancyRate();
            List<String> reasons = new ArrayList<>();
            double score = 0;
            if (zonePreference != null && s.zoneCode().equalsIgnoreCase(zonePreference)) {
                score += 3;
                reasons.add("in preferred zone " + s.zoneCode());
            }
            if (s.hasPowerOutlet()) {
                score += Boolean.TRUE.equals(power) ? 1 : 0.3;
                reasons.add("has power outlet");
            }
            if (s.quietZone()) {
                reasons.add("quiet zone");
            }
            if (!s.physicalOccupied()) {
                score += 0.5; // sensor confirms the seat is physically empty
            } else {
                reasons.add("sensor detects something on the seat");
                score -= 1;
            }
            score += 2 * (1 - zoneRate);
            reasons.add("zone " + s.zoneCode() + " is " + Math.round(zoneRate * 100) + "% occupied");
            ranked.add(new SeatRecommendation(s, round(score), zoneRate, reasons));
        }
        return ranked.stream()
                .sorted(Comparator.comparingDouble(SeatRecommendation::score).reversed()
                        .thenComparing(r -> r.seat().seatCode()))
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
