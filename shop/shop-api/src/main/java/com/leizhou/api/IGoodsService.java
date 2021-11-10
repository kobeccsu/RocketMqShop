package com.leizhou.api;

import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradeGoods;
import com.leizhou.shop.pojo.TradeGoodsNumberLog;
import com.leizhou.shop.pojo.TradeOrder;

public interface IGoodsService {

    TradeGoods findOne(Long goodsId);

    Result reduceGoodsNum(TradeGoodsNumberLog goodsNumberLog);
}
