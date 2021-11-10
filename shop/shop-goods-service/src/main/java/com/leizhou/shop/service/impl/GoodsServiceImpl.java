package com.leizhou.shop.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.leizhou.api.IGoodsService;
import com.leizhou.constant.ShopCode;
import com.leizhou.entity.Result;
import com.leizhou.exception.CastException;
import com.leizhou.shop.mapper.TradeGoodsMapper;
import com.leizhou.shop.mapper.TradeGoodsNumberLogMapper;
import com.leizhou.shop.pojo.TradeGoods;
import com.leizhou.shop.pojo.TradeGoodsNumberLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Service(interfaceClass = IGoodsService.class)
public class GoodsServiceImpl implements IGoodsService {

    @Autowired
    private TradeGoodsMapper tradeGoodsMapper;

    @Autowired
    private TradeGoodsNumberLogMapper tradeGoodsNumberLogMapper;

    @Override
    public TradeGoods findOne(Long goodsId) {
        if (goodsId == null){
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }

        return tradeGoodsMapper.selectByPrimaryKey(goodsId);
    }

    @Override
    public Result reduceGoodsNum(TradeGoodsNumberLog goodsNumberLog) {
        if (goodsNumberLog == null ||
            goodsNumberLog.getGoodsNumber() == null ||
            goodsNumberLog.getOrderId() == null ||
            goodsNumberLog.getGoodsNumber().intValue() <= 0) {
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }

        TradeGoods goods = tradeGoodsMapper.selectByPrimaryKey(goodsNumberLog.getGoodsId());
        if (goods.getGoodsNumber() < goodsNumberLog.getGoodsNumber()){
            CastException.cast(ShopCode.SHOP_GOODS_NUM_NOT_ENOUGH);
        }

        goods.setGoodsNumber(goods.getGoodsNumber() - goodsNumberLog.getGoodsNumber());
        tradeGoodsMapper.updateByPrimaryKey(goods);

        goodsNumberLog.setGoodsNumber(-goodsNumberLog.getGoodsNumber());
        goodsNumberLog.setLogTime(new Date());
        tradeGoodsNumberLogMapper.insert(goodsNumberLog);

        return new Result(ShopCode.SHOP_SUCCESS.getSuccess(), ShopCode.SHOP_SUCCESS.getMessage());
    }
}
