package com.leizhou.api;


import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradeOrder;

public interface IOrderService {
    Result confirmOrder(TradeOrder order);
}
