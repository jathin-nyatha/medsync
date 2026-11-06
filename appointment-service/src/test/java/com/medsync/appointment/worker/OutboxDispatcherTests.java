package com.medsync.appointment.worker;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medsync.appointment.observability.OutboxMetrics;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.service.OutboxEventClaimService;
import com.medsync.appointment.service.OutboxEventDeliveryService;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTests {

    @Mock
    private OutboxEventClaimService claimService;

    @Mock
    private OutboxEventDeliveryService deliveryService;

    @Mock
    private OutboxMetrics outboxMetrics;

    private OutboxDispatcherProperties properties;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new OutboxDispatcherProperties();
        properties.setBatchSize(10);
        properties.setLeaseDuration(Duration.ofSeconds(120));

        dispatcher = new OutboxDispatcher(
                claimService,
                deliveryService,
                properties,
                outboxMetrics
        );
    }

    @Test
    void recordsClaimedCountAndDoesNothingForEmptyBatch() {
        when(claimService.claimBatch(10, Duration.ofSeconds(120)))
                .thenReturn(List.of());

        dispatcher.dispatchPendingEvents();

        verify(outboxMetrics).recordClaimed(0);
        verifyNoInteractions(deliveryService);
    }

    @Test
    void recordsClaimFailureWhenBatchCannotBeClaimed() {
        when(claimService.claimBatch(10, Duration.ofSeconds(120)))
                .thenThrow(new IllegalStateException("Database unavailable"));

        dispatcher.dispatchPendingEvents();

        verify(outboxMetrics).recordClaimFailure();
        verifyNoInteractions(deliveryService);
    }
}