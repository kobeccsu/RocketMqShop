package com.leizhou.shop.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.leizhou.api.ICouponService;
import com.leizhou.api.IGoodsService;
import com.leizhou.api.IOrderService;
import com.leizhou.api.IUserService;
import com.leizhou.constant.ShopCode;
import com.leizhou.entity.MQEntity;
import com.leizhou.entity.Result;
import com.leizhou.exception.CastException;
import com.leizhou.shop.mapper.TradeOrderMapper;
import com.leizhou.shop.pojo.*;
import com.leizhou.utils.IDWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

@Component
@Service(interfaceClass = IOrderService.class)
@Slf4j
public class OrderServiceImpl implements IOrderService {

    @Reference
    private IGoodsService goodsService;

    @Reference
    private IUserService userService;

    @Reference
    private ICouponService couponService;

    @Autowired
    private IDWorker idWorker;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Value("${mq.order.topic}")
    private String topic;

    @Value("${mq.order.tag.cancel}")
    private String tag;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public Result confirmOrder(TradeOrder order) {
        checkOrder(order);

        Long orderId = setPreOrder(order);
        try {
            reduceGoodsNum(order);
            updateCouponStatus(order);
            reduceMoneyPaid(order);

//            CastException.cast(ShopCode.SHOP_FAIL);
            updateOrderStatus(order);

            return new Result(ShopCode.SHOP_SUCCESS.getSuccess(), ShopCode.SHOP_SUCCESS.getMessage());
        }catch (Exception ex ){
            MQEntity mqEntity = new MQEntity();
            mqEntity.setOrderId(order.getOrderId());
            mqEntity.setUserId(order.getUserId());
            mqEntity.setUserMoney(order.getMoneyPaid());
            mqEntity.setCouponId(order.getCouponId());
            mqEntity.setGoodsId(order.getGoodsId());
            mqEntity.setGoodsNum(order.getGoodsNumber());

            try {
                sendCancelOrder(topic, tag, order.getOrderId().toString(), JSON.toJSONString(mqEntity));
            } catch (Exception e) {
                e.printStackTrace();
            }

            return new Result(ShopCode.SHOP_FAIL.getSuccess(), ShopCode.SHOP_FAIL.getMessage());
        }

    }

    private void sendCancelOrder(String topic, String tag, String keys, String body) throws MQBrokerException, RemotingException, InterruptedException, MQClientException {
        Message message = new Message(topic, tag, keys, body.getBytes());
        rocketMQTemplate.getProducer().send(message);
    }

    private void updateOrderStatus(TradeOrder order) {
        order.setOrderStatus(ShopCode.SHOP_ORDER_CONFIRM.getCode());
        order.setPayStatus(ShopCode.SHOP_ORDER_PAY_STATUS_NO_PAY.getCode());
        order.setConfirmTime(new Date());
        int result = tradeOrderMapper.updateByPrimaryKey(order);

        if (result <= 0){
            CastException.cast(ShopCode.SHOP_ORDER_CONFIRM_FAIL);
        }

        log.info("订单 " + order.getOrderId() + " 确认订单成功");
    }

    /**
     *
     * @param order
     */
    private void reduceMoneyPaid(TradeOrder order) {
       if (order.getMoneyPaid() != null && order.getMoneyPaid().compareTo(BigDecimal.ZERO) == 1){
           TradeUserMoneyLog tradeUserMoneyLog = new TradeUserMoneyLog();
           tradeUserMoneyLog.setOrderId(order.getOrderId());
           tradeUserMoneyLog.setUserId(order.getUserId());
           tradeUserMoneyLog.setUseMoney(order.getMoneyPaid());
           tradeUserMoneyLog.setMoneyLogType(ShopCode.SHOP_USER_MONEY_PAID.getCode());

           Result result = userService.updateMoneyPaid(tradeUserMoneyLog);
           if (result.getSuccess().equals(ShopCode.SHOP_FAIL.getSuccess())){
               CastException.cast(ShopCode.SHOP_USER_MONEY_REDUCE_FAIL);
           }
           log.info("订单" + order.getOrderId() + ",扣减余额成功");
       }
    }

    private void updateCouponStatus(TradeOrder order) {
        if (order.getCouponId() != null) {
            TradeCoupon coupon = couponService.findOne(order.getCouponId());
            coupon.setOrderId(order.getOrderId());
            coupon.setIsUsed(ShopCode.SHOP_COUPON_ISUSED.getCode());
            coupon.setUsedTime(new Date());

            Result result = couponService.updateCouponStatus(coupon);
            if (result.getSuccess().equals(ShopCode.SHOP_FAIL.getSuccess())){
                CastException.cast(ShopCode.SHOP_COUPON_USE_FAIL);
            }

            log.info("订单" + order.getOrderId() + " 使用优惠券成功");
        }
    }

    private void reduceGoodsNum(TradeOrder order) {
        TradeGoodsNumberLog goodsNumberLog = new TradeGoodsNumberLog();
        goodsNumberLog.setOrderId(order.getOrderId());
        goodsNumberLog.setGoodsId(order.getGoodsId());
        goodsNumberLog.setGoodsNumber(order.getGoodsNumber());

        Result result = goodsService.reduceGoodsNum(goodsNumberLog);
        if (result.getSuccess().equals(ShopCode.SHOP_FAIL.getSuccess())){
            CastException.cast(ShopCode.SHOP_REDUCE_GOODS_NUM_FAIL);
        }
        log.info("订单" + order.getOrderId() + "扣减成功");
    }

    private void checkOrder(TradeOrder order) {
        if (order == null) {
            CastException.cast(ShopCode.SHOP_ORDER_INVALID);
        }

        TradeGoods goods = goodsService.findOne(order.getGoodsId());
        if (goods == null) {
            CastException.cast(ShopCode.SHOP_GOODS_NO_EXIST);
        }
        TradeUser user = userService.findOne(order.getUserId());
        if (user == null) {
            CastException.cast(ShopCode.SHOP_USER_NO_EXIST);
        }
        if (order.getGoodsPrice().compareTo(goods.getGoodsPrice()) != 0) {
            CastException.cast(ShopCode.SHOP_GOODS_PRICE_INVALID);
        }
        if (order.getGoodsNumber() > goods.getGoodsNumber()) {
            CastException.cast(ShopCode.SHOP_GOODS_NUM_NOT_ENOUGH);
        }

        log.info("All validation passed, order id :" + order.getOrderId());
    }

    private Long setPreOrder(TradeOrder order) {
        order.setOrderStatus(ShopCode.SHOP_ORDER_NO_CONFIRM.getCode());

        long orderId = idWorker.nextId();
        order.setOrderId(orderId);

        BigDecimal shippingFee = calculateShippingFee(order.getOrderAmount());
        if (order.getShippingFee().compareTo(shippingFee) != 0) {
            CastException.cast(ShopCode.SHOP_ORDER_SHIPPINGFEE_INVALID);
        }

        BigDecimal orderAmount = order.getGoodsPrice().multiply(new BigDecimal(order.getGoodsNumber()));
        orderAmount.add(shippingFee);
        if (order.getOrderAmount().compareTo(orderAmount) != 0) {
            CastException.cast(ShopCode.SHOP_ORDERAMOUNT_INVALID);
        }

        BigDecimal moneyPaid = order.getMoneyPaid();
        if (moneyPaid != null) {
            int moneyCompare = moneyPaid.compareTo(BigDecimal.ZERO);
            if (moneyCompare == -1) {
                CastException.cast(ShopCode.SHOP_MONEY_PAID_LESS_ZERO);
            }

            if (moneyCompare == 1) {
                TradeUser user = userService.findOne(order.getUserId());
                if (moneyPaid.compareTo(new BigDecimal(user.getUserMoney())) == 1) {
                    CastException.cast(ShopCode.SHOP_MONEY_PAID_INVALID);
                }
            }
        } else {
            order.setMoneyPaid(BigDecimal.ZERO);
        }

        Long couponId = order.getCouponId();
        if (couponId != null) {
            TradeCoupon coupon = couponService.findOne(couponId);
            if (coupon == null) {
                CastException.cast(ShopCode.SHOP_COUPON_NO_EXIST);
            }
            if (coupon.getIsUsed().intValue() == ShopCode.SHOP_COUPON_ISUSED.getCode().intValue()) {
                CastException.cast(ShopCode.SHOP_COUPON_ISUSED);
            }

            order.setCouponPaid(coupon.getCouponPrice());
        } else {
            order.setCouponPaid(BigDecimal.ZERO);
        }

        BigDecimal payAmount = order.getOrderAmount().subtract(order.getMoneyPaid()).subtract(order.getCouponPaid());
        order.setPayAmount(payAmount);
        order.setAddTime(new Date());

        tradeOrderMapper.insert(order);

        return orderId;
    }

    private BigDecimal calculateShippingFee(BigDecimal orderAmount) {
        if (orderAmount.compareTo(new BigDecimal(100)) > 0) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(10);
    }
}
