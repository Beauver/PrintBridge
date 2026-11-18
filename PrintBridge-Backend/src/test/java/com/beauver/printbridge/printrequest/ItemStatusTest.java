package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.file.ItemStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.beauver.printbridge.printrequest.file.ItemStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemStatusTest {

    @Test
    void flowAsAgreedInTheIssue() {
        assertEquals(List.of(ACCEPTED, DECLINED), PENDING.nextStatuses());
        assertEquals(List.of(PRINTING, COMPLETED, DECLINED), ACCEPTED.nextStatuses());
        assertEquals(List.of(COMPLETED, ACCEPTED, DECLINED), PRINTING.nextStatuses());
        assertEquals(List.of(ACCEPTED), DECLINED.nextStatuses());
        assertEquals(List.of(ACCEPTED), COMPLETED.nextStatuses());
    }

    @Test
    void canMoveToFollowsTheFlow() {
        assertTrue(PENDING.canMoveTo(ACCEPTED));
        assertFalse(PENDING.canMoveTo(PRINTING));
        assertFalse(DECLINED.canMoveTo(PENDING));
        for (ItemStatus status : ItemStatus.values()) {
            assertFalse(status.canMoveTo(status));
        }
    }
}
