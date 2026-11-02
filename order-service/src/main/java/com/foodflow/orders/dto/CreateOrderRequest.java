package com.foodflow.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public class CreateOrderRequest {
  @NotEmpty
  private List<@Valid OrderLineRequest> items;

  @Size(max = 30)
  private String couponCode;

  @Pattern(regexp = "RAZORPAY|COD")
  private String paymentMethod = "RAZORPAY";

  @NotBlank
  @Size(max = 500)
  private String deliveryAddress;

  public CreateOrderRequest() {}

  public List<OrderLineRequest> items() {
    return items;
  }

  public void setItems(List<OrderLineRequest> items) {
    this.items = items;
  }

  public String couponCode() {
    return couponCode;
  }

  public void setCouponCode(String couponCode) {
    this.couponCode = couponCode;
  }

  public String paymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(String paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String deliveryAddress() {
    return deliveryAddress;
  }

  public void setDeliveryAddress(String deliveryAddress) {
    this.deliveryAddress = deliveryAddress;
  }

  public static class OrderLineRequest {
    @NotNull
    private UUID menuItemId;

    @Min(1)
    @Max(20)
    private int quantity;

    public OrderLineRequest() {}

    public UUID menuItemId() {
      return menuItemId;
    }

    public void setMenuItemId(UUID menuItemId) {
      this.menuItemId = menuItemId;
    }

    public int quantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }
  }
}
