package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.common.error.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StatusFilterTest {

    @Test
    void blankAndOpenMeanOpenStatuses() {
        assertEquals(RequestStatus.OPEN, StatusFilter.parse(null));
        assertEquals(RequestStatus.OPEN, StatusFilter.parse(" "));
        assertEquals(RequestStatus.OPEN, StatusFilter.parse("open"));
    }

    @Test
    void allMeansEveryStatus() {
        assertEquals(EnumSet.allOf(RequestStatus.class), StatusFilter.parse("ALL"));
    }

    @Test
    void singleStatusIgnoringCase() {
        assertEquals(Set.of(RequestStatus.IN_REVIEW), StatusFilter.parse(" in_review "));
    }

    @Test
    void unknownValueIsInvalid() {
        var e = assertThrows(InvalidInputException.class, () -> StatusFilter.parse("DONE"));
        assertEquals("Unknown status filter: DONE", e.getMessage());
    }
}
