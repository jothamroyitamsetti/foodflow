package com.foodflow.coupon.service;

import com.foodflow.coupon.dto.CouponRequest;
import com.foodflow.coupon.entity.Coupon;
import com.foodflow.coupon.repository.CouponRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CouponService {
  private final CouponRepository repo;

  public List<Coupon> all() {
    return repo.findAll(Sort.by(Sort.Direction.ASC, "code"));
  }

  public Coupon create(CouponRequest request) {
    String code = normalize(request.code());
    if (repo.findByCodeIgnoreCase(code).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon code already exists");
    }
    if (!request.expiresAt().isAfter(LocalDateTime.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon expiry must be in the future");
    }
    Coupon coupon = new Coupon();
    coupon.setCode(code);
    coupon.setDiscountPercent(request.discountPercent());
    coupon.setMaxUses(request.maxUses());
    coupon.setExpiresAt(request.expiresAt());
    return repo.save(coupon);
  }

  public Coupon validate(String code) {
    Coupon coupon = repo.findByCodeIgnoreCase(normalize(code))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    ensureUsable(coupon);
    return coupon;
  }

  @Transactional
  public Coupon redeem(String code) {
    Coupon coupon = repo.findByCodeForUpdate(normalize(code))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    ensureUsable(coupon);
    coupon.setUsedCount(coupon.getUsedCount() + 1);
    return repo.save(coupon);
  }

  @Transactional
  public void release(String code) {
    Coupon coupon = repo.findByCodeIgnoreCase(normalize(code))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
    repo.save(coupon);
  }

  private void ensureUsable(Coupon coupon) {
    if (!coupon.isActive() || !coupon.getExpiresAt().isAfter(LocalDateTime.now())
        || coupon.getUsedCount() >= coupon.getMaxUses()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon is expired or usage limit reached");
    }
  }

  private String normalize(String code) {
    if (code == null || code.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon code is required");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }
}
