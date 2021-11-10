package com.leizhou.api;

import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradeOrder;
import com.leizhou.shop.pojo.TradeUser;
import com.leizhou.shop.pojo.TradeUserMoneyLog;

public interface IUserService {
    TradeUser findOne(Long userId);

    Result updateMoneyPaid(TradeUserMoneyLog order);
}
