package com.foodflow.delivery.api;

import com.foodflow.delivery.domain.*;
import com.foodflow.delivery.order.OrderClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DeliveryServiceTest {
    private final DeliveryAssignmentRepository assignments = mock(DeliveryAssignmentRepository.class);
    private final DeliveryPartnerRepository partners = mock(DeliveryPartnerRepository.class);
    private final OrderClient orders = mock(OrderClient.class);
    private final DeliveryService service = new DeliveryService(assignments, partners, orders, "internal");

    @Test
    void partnerCannotUpdateAnotherPartnersDelivery() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setPartnerId(owner);
        assignment.setStatus(DeliveryStatus.ASSIGNED);
        when(assignments.findById(id)).thenReturn(Optional.of(assignment));

        assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(id, other, DeliveryStatus.PICKED_UP));
        verifyNoInteractions(orders);
    }

    @Test
    void invalidStatusTransitionIsRejected() {
        UUID partner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setPartnerId(partner);
        assignment.setStatus(DeliveryStatus.ASSIGNED);
        when(assignments.findById(id)).thenReturn(Optional.of(assignment));

        assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(id, partner, DeliveryStatus.DELIVERED));
    }
}
