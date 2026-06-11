package com.eRez.map.consumer;

import com.eRez.map.dto.event.NodeChangedEvent;
import com.eRez.map.services.RouteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RouteRecalculationConsumerTest {

    @Mock RouteService routeService;

    @InjectMocks RouteRecalculationConsumer consumer;

    @Test
    void nodeAdded_triggersRecalculation() {
        consumer.onNodeChanged(new NodeChangedEvent("NODE_ADDED", null));
        verify(routeService).recalculateAllStale();
    }

    @Test
    void nodeUpdated_triggersRecalculation() {
        consumer.onNodeChanged(new NodeChangedEvent("NODE_UPDATED", "Amsterdam"));
        verify(routeService).recalculateAllStale();
    }

    @Test
    void nodeDeletedIntermediate_triggersRecalculation() {
        consumer.onNodeChanged(new NodeChangedEvent("NODE_DELETED_INTERMEDIATE", "Amsterdam"));
        verify(routeService).recalculateAllStale();
    }
}
