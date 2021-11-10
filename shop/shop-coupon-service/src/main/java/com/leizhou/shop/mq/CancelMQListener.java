package com.leizhou.shop.mq;

import com.alibaba.fastjson.JSON;
import com.leizhou.constant.ShopCode;
import com.leizhou.entity.MQEntity;
import com.leizhou.shop.mapper.TradeCouponMapper;
import com.leizhou.shop.pojo.TradeCoupon;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.io.UnsupportedEncodingException;


@Slf4j
@Component
@RocketMQMessageListener(topic = "${mq.order.topic}", consumerGroup = "${mq.order.consumer.group.name}", messageModel = MessageModel.BROADCASTING)
public class CancelMQListener implements RocketMQListener<MessageExt> {


    @Autowired
    private TradeCouponMapper couponMapper;

    @Override
    public void onMessage(MessageExt message) {

        try {
            MQEntity mqEntity = JSON.parseObject(message.getBody(), MQEntity.class);
            log.info("优惠券端接收到消息");
            if (mqEntity.getCouponId() != null) {
                TradeCoupon coupon = couponMapper.selectByPrimaryKey(mqEntity.getCouponId());
                coupon.setIsUsed(ShopCode.SHOP_COUPON_UNUSED.getCode());
                coupon.setUsedTime(null);
                coupon.setOrderId(null);

                couponMapper.updateByPrimaryKey(coupon);
                log.info("回退优惠券成功");
            }else{
                log.info("订单 " + mqEntity.getOrderId() + " 没有优惠券需要回退");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info("回退优惠券失败");
        }
    }
}
