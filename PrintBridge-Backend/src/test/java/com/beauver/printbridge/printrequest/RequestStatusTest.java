package com.beauver.printbridge.printrequest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.beauver.printbridge.printrequest.RequestStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestStatusTest {

    @Test
    void transitionsFollowTheFlow() {
        assertEquals(List.of(IN_REVIEW, ACCEPTED, DECLINED), SUBMITTED.nextStatuses());
        assertEquals(List.of(ACCEPTED, DECLINED), IN_REVIEW.nextStatuses());
        assertEquals(List.of(PRINTING, COMPLETED), ACCEPTED.nextStatuses());
        assertEquals(List.of(COMPLETED, ACCEPTED, DECLINED), PRINTING.nextStatuses());
        assertTrue(DECLINED.nextStatuses().isEmpty());
        assertTrue(COMPLETED.nextStatuses().isEmpty());
    }

    @Test
    void canOnlyMoveForwardAlongTheFlow() {
        assertTrue(SUBMITTED.canMoveTo(DECLINED));
        assertFalse(SUBMITTED.canMoveTo(COMPLETED));
        assertFalse(IN_REVIEW.canMoveTo(SUBMITTED));
        assertFalse(ACCEPTED.canMoveTo(DECLINED));
        assertTrue(PRINTING.canMoveTo(ACCEPTED));
        assertFalse(SUBMITTED.canMoveTo(PRINTING));
        assertFalse(ACCEPTED.canMoveTo(ACCEPTED));
    }

    @Test
    void paymentIsAllowedFromAcceptedOn() {
        assertTrue(ACCEPTED.allowsPayment());
        assertTrue(PRINTING.allowsPayment());
        assertTrue(COMPLETED.allowsPayment());
        assertFalse(SUBMITTED.allowsPayment());
        assertFalse(IN_REVIEW.allowsPayment());
        assertFalse(DECLINED.allowsPayment());
    }

    @Test
    void declinedAndCompletedAreFinal() {
        assertTrue(DECLINED.isFinal());
        assertTrue(COMPLETED.isFinal());
        assertFalse(ACCEPTED.isFinal());
        assertFalse(PRINTING.isFinal());
        assertEquals(java.util.Set.of(SUBMITTED, IN_REVIEW, ACCEPTED, PRINTING), OPEN);
    }
}
