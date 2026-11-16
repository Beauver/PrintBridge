package com.beauver.printbridge.common.ratelimit;

import com.beauver.printbridge.common.error.TooManyRequestsException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ResourceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    static class Plain {
        public void free() {
        }

        @RateLimit(limit = 1, windowSeconds = 30)
        public void perAddress() {
        }

        @RateLimit(limit = 1, windowSeconds = 30, per = RateLimitKey.USER)
        public void perUser() {
        }
    }

    @RateLimit(limit = 2, windowSeconds = 30)
    static class Limited {
        public void inherits() {
        }

        public void inheritsToo() {
        }

        @RateLimit(limit = 1, windowSeconds = 30)
        public void stricter() {
        }
    }

    @Mock
    ResourceInfo resource;
    @Mock
    ClientAddress clientAddress;
    @Mock
    SecurityIdentity identity;

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        filter.limiter = new RateLimiter(new MutableClock());
        filter.clientAddress = clientAddress;
        filter.identity = identity;
        lenient().when(clientAddress.get()).thenReturn("10.0.0.1");
        lenient().when(identity.isAnonymous()).thenReturn(true);
    }

    private void call(Class<?> type, String method) throws NoSuchMethodException {
        doReturn(type).when(resource).getResourceClass();
        when(resource.getResourceMethod()).thenReturn(type.getMethod(method));
        filter.enforce(resource);
    }

    private void loggedInAs(String name) {
        Principal principal = () -> name;
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
    }

    @Test
    void leavesEndpointsWithoutTheAnnotationAlone() throws Exception {
        for (int i = 0; i < 50; i++) {
            call(Plain.class, "free");
        }
        when(resource.getResourceMethod()).thenReturn(null);
        filter.enforce(resource);
    }

    @Test
    void refusesTheRequestOverTheLimitWithTheTimeToWait() throws Exception {
        call(Plain.class, "perAddress");

        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> call(Plain.class, "perAddress"));
        assertEquals(30, e.retryAfterSeconds());
        assertEquals("Too many attempts, try again in 30 seconds", e.getMessage());
    }

    @Test
    void countsEachAddressOnItsOwn() throws Exception {
        call(Plain.class, "perAddress");
        when(clientAddress.get()).thenReturn("10.0.0.2");

        call(Plain.class, "perAddress");
    }

    @Test
    void countsPerUserWhateverTheAddress() throws Exception {
        loggedInAs("7");
        call(Plain.class, "perUser");
        lenient().when(clientAddress.get()).thenReturn("10.0.0.2");
        assertThrows(TooManyRequestsException.class, () -> call(Plain.class, "perUser"));

        loggedInAs("8");
        call(Plain.class, "perUser");
    }

    @Test
    void countsAnonymousRequestsOfAUserLimitByAddress() throws Exception {
        call(Plain.class, "perUser");
        assertThrows(TooManyRequestsException.class, () -> call(Plain.class, "perUser"));

        when(clientAddress.get()).thenReturn("10.0.0.2");
        call(Plain.class, "perUser");
    }

    @Test
    void aLoggedInUserIsStillCountedByAddressOnAnAddressLimit() throws Exception {
        lenient().when(identity.isAnonymous()).thenReturn(false);
        call(Plain.class, "perAddress");

        assertThrows(TooManyRequestsException.class, () -> call(Plain.class, "perAddress"));
    }

    @Test
    void aLimitOnTheClassAppliesToEachEndpointSeparately() throws Exception {
        call(Limited.class, "inherits");
        call(Limited.class, "inherits");
        assertThrows(TooManyRequestsException.class, () -> call(Limited.class, "inherits"));

        call(Limited.class, "inheritsToo");
    }

    @Test
    void aLimitOnTheMethodWinsOverTheClass() throws Exception {
        call(Limited.class, "stricter");

        assertThrows(TooManyRequestsException.class, () -> call(Limited.class, "stricter"));
    }
}
