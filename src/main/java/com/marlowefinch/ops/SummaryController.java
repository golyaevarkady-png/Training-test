package com.marlowefinch.ops;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** TODO-233: built from the existing dashboard queries; no SQL of its own. */
@RestController
public class SummaryController {

    private final DashboardRepository repository;
    private final Clock clock;

    public SummaryController(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/api/summary")
    public Summary summary(@RequestParam(required = false) String from,
                           @RequestParam(required = false) String to) {
        DateRange range = DateRange.resolve(from, to, clock);
        Kpis kpis = repository.kpis(range);
        return new Summary(kpis.from(), kpis.to(), kpis.onTimeRate(), kpis.openTickets(),
                kpis.revenue(), kpis.orders(),
                worstCarrier(repository.onTimeByCarrier(range)),
                busiestTicketCategory(repository.ticketsByCategory(range)));
    }

    /** Lowest rate among carriers that delivered something; ties go to the first name alphabetically. */
    static String worstCarrier(List<CarrierOnTime> carriers) {
        return carriers.stream()
                .filter(c -> c.delivered() > 0 && c.rate() != null)
                .min(Comparator.comparing(CarrierOnTime::rate).thenComparing(CarrierOnTime::carrier))
                .map(CarrierOnTime::carrier)
                .orElse(null);
    }

    /** Most tickets opened in the range; ties go to the first category alphabetically. */
    static String busiestTicketCategory(List<TicketCategoryCount> categories) {
        return categories.stream()
                .filter(c -> c.total() > 0)
                .min(Comparator.comparingLong(TicketCategoryCount::total).reversed()
                        .thenComparing(TicketCategoryCount::category))
                .map(TicketCategoryCount::category)
                .orElse(null);
    }
}
