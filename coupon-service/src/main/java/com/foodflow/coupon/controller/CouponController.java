package com.foodflow.coupon.controller;

import com.foodflow.coupon.dto.CouponRequest;
import com.foodflow.coupon.entity.Coupon;
import com.foodflow.coupon.service.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {
  private final CouponService service;

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public Coupon create(@Valid @RequestBody CouponRequest request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<Coupon> all() {
    return service.all();
  }

  @GetMapping("/{code}/validate")
  public Coupon validate(@PathVariable String code) {
    return service.validate(code);
  }

  @PostMapping("/{code}/redeem")
  public Coupon redeem(@PathVariable String code) {
    return service.redeem(code);
  }

  @PostMapping("/{code}/release")
  public void release(@PathVariable String code) {
    service.release(code);
  }
}
