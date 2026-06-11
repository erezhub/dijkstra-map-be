package com.eRez.map.consumer;

import com.eRez.map.dto.event.NodeChangedEvent;
import com.eRez.map.services.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRecalculationConsumer {

    private final RouteService routeService;

    @RabbitListener(queues = "${rabbitmq.queue.route-recalculation}")
    public void onNodeChanged(NodeChangedEvent event) {
        log.info("Received node-changed event: type={}, node={}", event.getType(), event.getNodeName());
        routeService.recalculateAllStale();
    }
}
