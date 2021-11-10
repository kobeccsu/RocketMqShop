package com.leizhou.api;

import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradeCoupon;

public interface ICouponService {
    TradeCoupon findOne(Long couponId);

    Result updateCouponStatus(TradeCoupon coupon);
}
