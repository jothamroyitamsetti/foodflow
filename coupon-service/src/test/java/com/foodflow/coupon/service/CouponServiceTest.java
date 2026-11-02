package com.foodflow.coupon.service;

import com.foodflow.coupon.dto.CouponRequest;
import com.foodflow.coupon.entity.Coupon;
import com.foodflow.coupon.repository.CouponRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponServiceTest {
  @Test
  void unknownCouponIsRejected() {
    CouponRepository repo = mock(CouponRepository.class);
    when(repo.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

    assertThrows(ResponseStatusException.class, () -> new CouponService(repo).validate(" nope "));
  }

  @Test
  void createNormalizesCouponCodeAndRejectsExpiryInThePast() {
    CouponRepository repo = mock(CouponRepository.class);
    when(repo.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.empty());
    when(repo.save(org.mockito.ArgumentMatchers.any(Coupon.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    CouponService service = new CouponService(repo);

    Coupon created = service.create(new CouponRequest(
        " save10 ", BigDecimal.TEN, 5, LocalDateTime.now().plusDays(1)));

    assertEquals("SAVE10", created.getCode());
    assertThrows(ResponseStatusException.class, () -> service.create(new CouponRequest(
        "OLD", BigDecimal.TEN, 5, LocalDateTime.now().minusMinutes(1))));
  }

  @Test
  void redeemAcceptsMixedCaseAndIncrementsUsage() {
    CouponRepository repo = mock(CouponRepository.class);
    Coupon coupon = coupon("SAVE10");
    when(repo.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
    when(repo.save(coupon)).thenReturn(coupon);

    Coupon redeemed = new CouponService(repo).redeem(" save10 ");

    assertEquals(1, redeemed.getUsedCount());
    verify(repo).findByCodeForUpdate("SAVE10");
  }

  @Test
  void redeemRejectsAnExhaustedCoupon() {
    CouponRepository repo = mock(CouponRepository.class);
    Coupon coupon = coupon("SAVE10");
    coupon.setUsedCount(coupon.getMaxUses());
    when(repo.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));

    assertThrows(ResponseStatusException.class, () -> new CouponService(repo).redeem("SAVE10"));
  }

  private Coupon coupon(String code) {
    Coupon coupon = new Coupon();
    coupon.setCode(code);
    coupon.setDiscountPercent(BigDecimal.TEN);
    coupon.setMaxUses(1);
    coupon.setExpiresAt(LocalDateTime.now().plusDays(1));
    return coupon;
  }
}
