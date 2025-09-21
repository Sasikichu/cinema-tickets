package uk.gov.dwp.uc.pairtest;

import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    // Constraints & prices
    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    private static final int PRICE_ADULT = 25;
    private static final int PRICE_CHILD = 15;
    private static final int PRICE_INFANT = 0;

    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;

    public TicketServiceImpl(TicketPaymentService paymentService,
                            SeatReservationService reservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... requests) throws InvalidPurchaseException {
        validateAccount(accountId);
        Map<TicketTypeRequest.Type, Integer> counts = aggregateRequests(requests);
        validateBusinessRules(counts);

        long totalToPay = calculateAmount(counts);
        int seatsToReserve = calculateSeats(counts);

        // All valid accounts have sufficient funds; payment always succeeds per spec.
        paymentService.makePayment(accountId, (int) totalToPay);
        reservationService.reserveSeat(accountId, seatsToReserve);
    }

    // ------- helpers (private) -------

    private static void validateAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be a positive number");
        }
    }

    private static Map<TicketTypeRequest.Type, Integer> aggregateRequests(TicketTypeRequest... requests) {
        if (requests == null || requests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket type must be requested");
        }
        Map<TicketTypeRequest.Type, Integer> counts = new EnumMap<>(TicketTypeRequest.Type.class);
        counts.put(TicketTypeRequest.Type.ADULT, 0);
        counts.put(TicketTypeRequest.Type.CHILD, 0);
        counts.put(TicketTypeRequest.Type.INFANT, 0);

        for (TicketTypeRequest req : requests) {
            if (req == null) {
                throw new InvalidPurchaseException("Ticket request cannot be null");
            }
            if (req.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException("Ticket quantities must be positive");
            }
            counts.compute(req.getTicketType(), (k, v) -> v + req.getNoOfTickets());
        }

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            throw new InvalidPurchaseException("Total tickets cannot be zero");
        }
        if (total > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets in one transaction");
        }
        return counts;
    }

    private static void validateBusinessRules(Map<TicketTypeRequest.Type, Integer> counts) {
        int adults = counts.get(TicketTypeRequest.Type.ADULT);
        int children = counts.get(TicketTypeRequest.Type.CHILD);
        int infants = counts.get(TicketTypeRequest.Type.INFANT);

        // Dependents must have at least one adult
        if (adults == 0 && (children > 0 || infants > 0)) {
            throw new InvalidPurchaseException("Child/Infant tickets require at least one Adult ticket");
        }
        // Each infant must sit on an adult's lap
        if (infants > adults) {
            throw new InvalidPurchaseException("Each Infant must be accompanied by an Adult (infants <= adults)");
        }
    }

    private static long calculateAmount(Map<TicketTypeRequest.Type, Integer> counts) {
        long adults = counts.get(TicketTypeRequest.Type.ADULT);
        long children = counts.get(TicketTypeRequest.Type.CHILD);
        long infants = counts.get(TicketTypeRequest.Type.INFANT);
        return adults * PRICE_ADULT + children * PRICE_CHILD + infants * PRICE_INFANT;
    }

    private static int calculateSeats(Map<TicketTypeRequest.Type, Integer> counts) {
        return counts.get(TicketTypeRequest.Type.ADULT) + counts.get(TicketTypeRequest.Type.CHILD);
    }
}