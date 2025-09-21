package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServiceImplTest {

private final TicketPaymentService payment = mock(TicketPaymentService.class);
private final SeatReservationService seats = mock(SeatReservationService.class);
private final TicketService service = new TicketServiceImpl(payment, seats);

@Test
@DisplayName("Charges and reserves correctly for mixed purchase (order: pay then reserve)")
void happyPath() {

    Long accountId = 42L;
    service.purchaseTickets(
        accountId,
        new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2),
        new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3),
        new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1)
    );
    // Verify order: payment first, then reservation
    InOrder inOrder = inOrder(payment, seats);
    inOrder.verify(payment).makePayment(accountId, 95);
    inOrder.verify(seats).reserveSeat(accountId, 5);
    inOrder.verifyNoMoreInteractions();
}

@Test
@DisplayName("Rejects when account id is invalid")
void invalidAccount() {

    Executable call = () -> service.purchaseTickets(0L, new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1));
    InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class, call);
    assertNotNull(ex.getMessage()); // use the returned Throwable
    verifyNoInteractions(payment, seats);
}

@Test
@DisplayName("Rejects when total tickets exceed limit")
void tooManyTickets() {

    Executable call = () -> service.purchaseTickets(1L, new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 26));
    InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class, call);
    assertNotNull(ex.getMessage()); // use the returned Throwable
    verifyNoInteractions(payment, seats);
}

@Test
@DisplayName("Rejects child/infant without adult")
void noAdultWithDependents() {

    Executable call = () -> service.purchaseTickets(1L, new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1));
    InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class, call);
    assertNotNull(ex.getMessage()); // use the returned Throwable
    verifyNoInteractions(payment, seats);
}

@Test
@DisplayName("Rejects more infants than adults")
void tooManyInfants() {

    Executable call = () -> service.purchaseTickets(1L,
    new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1),
    new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2));

    InvalidPurchaseException ex = assertThrows(InvalidPurchaseException.class, call);
    assertNotNull(ex.getMessage());
    verifyNoInteractions(payment, seats);
}
@Test
@DisplayName("Aggregates duplicate ticket types across varargs")
void aggregatesDuplicates() {
    Long id = 7L;
    service.purchaseTickets(
        id,
        new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1),
        new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1),
        new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2),
        new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1)
    );
    // adults=2, children=2, infants=1 => amount = 2*25 + 2*15, seats = 4
    verify(payment).makePayment(id, (int) (2L*25 + 2L*15));
    verify(seats).reserveSeat(id, 4);
}
}
