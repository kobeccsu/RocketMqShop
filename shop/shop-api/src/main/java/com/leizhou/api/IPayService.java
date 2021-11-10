package com.leizhou.api;

import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradePay;

public interface IPayService {
    Result createPayment(TradePay tradePay);
    Result callbackPayment(TradePay tradePay);
}
