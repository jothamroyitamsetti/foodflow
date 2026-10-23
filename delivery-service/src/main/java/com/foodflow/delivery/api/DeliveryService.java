package com.foodflow.delivery.api;

import com.foodflow.delivery.domain.*;
import com.foodflow.delivery.order.OrderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeliveryService {
    private final DeliveryAssignmentRepository assignments;
    private final DeliveryPartnerRepository partners;
    private final OrderClient orders;
    private final String internalToken;

    public DeliveryService(DeliveryAssignmentRepository assignments, DeliveryPartnerRepository partners,
                           OrderClient orders, @Value("${foodflow.internal-token}") String internalToken) {
        this.assignments = assignments;
        this.partners = partners;
        this.orders = orders;
        this.internalToken = internalToken;
    }

    @Transactional
    public DeliveryDtos.DeliveryResponse assign(UUID orderId) {
        if (assignments.findByOrderId(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a delivery assignment");
        }
        OrderClient.EligibilityResponse eligibility = orders.eligibility(internalToken, orderId);
        if (!eligibility.eligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not ready for delivery");
        }
        DeliveryPartner partner = partners.findAvailableForUpdate(PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No delivery partners are available"));
        partner.setAvailable(false);
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setOrderId(orderId);
        assignment.setPartnerId(partner.getUserId());
        assignment.setAssignedAt(Instant.now());
        DeliveryAssignment saved = assignments.save(assignment);
        orders.updateStatus(internalToken, orderId, new OrderClient.StatusRequest("OUT_FOR_DELIVERY"));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryDtos.DeliveryResponse> myDeliveries(UUID partnerId, Pageable pageable) {
        return assignments.findByPartnerId(partnerId, pageable).map(this::toResponse);
    }

    @Transactional
    public DeliveryDtos.DeliveryResponse updateStatus(UUID assignmentId, UUID partnerId, DeliveryStatus next) {
        DeliveryAssignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"));
        if (!assignment.getPartnerId().equals(partnerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot update another partner's delivery");
        }
        boolean valid = (assignment.getStatus() == DeliveryStatus.ASSIGNED && next == DeliveryStatus.PICKED_UP)
                || (assignment.getStatus() == DeliveryStatus.PICKED_UP && next == DeliveryStatus.DELIVERED);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid delivery status transition");
        }
        if (next == DeliveryStatus.DELIVERED) {
            orders.updateStatus(internalToken, assignment.getOrderId(), new OrderClient.StatusRequest("DELIVERED"));
            partners.findById(partnerId).ifPresent(partner -> partner.setAvailable(true));
        }
        assignment.setStatus(next);
        return toResponse(assignment);
    }

    private DeliveryDtos.DeliveryResponse toResponse(DeliveryAssignment assignment) {
        return new DeliveryDtos.DeliveryResponse(assignment.getId(), assignment.getOrderId(),
                assignment.getPartnerId(), assignment.getStatus(), assignment.getAssignedAt());
    }
}
