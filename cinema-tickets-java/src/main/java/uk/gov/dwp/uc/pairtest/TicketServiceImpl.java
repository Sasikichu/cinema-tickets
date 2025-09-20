package uk.gov.dwp.uc.pairtest;

// import uk.gov.dwp.uc.pairtest.domain.TicketType.;
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
    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    private static final int PRICE_ADULT = 25;
    private static final int PRICE_CHILD = 15;
    private static final int PRICE_INFANT = 0;


    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;
//     private enum TicketType {
// INFANT,
// CHILD,
// ADULT
// }


    public TicketServiceImpl(TicketPaymentService paymentService,
                            SeatReservationService reservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
                            }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be a positive number");
        }
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket type must be requested");
        }
        Map<TicketTypeRequest.Type, Integer> counts = new EnumMap<>(TicketTypeRequest.Type.class);
        counts.put(TicketTypeRequest.Type.ADULT, 0);
        counts.put(TicketTypeRequest.Type.CHILD, 0);
        counts.put(TicketTypeRequest.Type.INFANT, 0);


for (TicketTypeRequest req : ticketTypeRequests) {
if (req == null) {
throw new InvalidPurchaseException("Ticket request cannot be null");
}
if (req.getNoOfTickets() <= 0) {
throw new InvalidPurchaseException("Ticket quantities must be positive");
}
counts.compute(req.getTicketType(), (k, v) -> v + req.getNoOfTickets());
}


int totalRequested = counts.values().stream().mapToInt(Integer::intValue).sum();
if (totalRequested == 0) {
throw new InvalidPurchaseException("Total tickets cannot be zero");
}
if (totalRequested > MAX_TICKETS_PER_PURCHASE) {
throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets in one transaction");
}


int adults = counts.get(TicketTypeRequest.Type.ADULT);
int children = counts.get(TicketTypeRequest.Type.CHILD);
int infants = counts.get(TicketTypeRequest.Type.INFANT);


if (adults == 0 && (children > 0 || infants > 0)) {
throw new InvalidPurchaseException("Child/Infant tickets require at least one Adult ticket");
}


if (infants > adults) {
throw new InvalidPurchaseException("Each Infant must be accompanied by an Adult (infants <= adults)");
}


int seatsToReserve = adults + children;


int totalToPay =  adults * PRICE_ADULT
+  children * PRICE_CHILD
+  infants * PRICE_INFANT;


paymentService.makePayment(accountId, totalToPay);
reservationService.reserveSeat(accountId, seatsToReserve);
    }

}
