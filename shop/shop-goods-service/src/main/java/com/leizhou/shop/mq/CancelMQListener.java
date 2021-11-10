package com.leizhou.shop.mq;

import com.alibaba.fastjson.JSON;
import com.leizhou.constant.ShopCode;
import com.leizhou.entity.MQEntity;
import com.leizhou.shop.mapper.TradeGoodsMapper;
import com.leizhou.shop.mapper.TradeGoodsNumberLogMapper;
import com.leizhou.shop.mapper.TradeMqConsumerLogMapper;
import com.leizhou.shop.pojo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.Date;


@Slf4j
@Component
@RocketMQMessageListener(topic = "${mq.order.topic}", consumerGroup = "${mq.order.consumer.group.name}", messageModel = MessageModel.BROADCASTING)
public class CancelMQListener implements RocketMQListener<MessageExt> {


    @Autowired
    private TradeMqConsumerLogMapper tradeMqConsumerLogMapper;

    @Value("${mq.order.consumer.group.name}")
    private String groupName;

    @Autowired
    private TradeGoodsMapper tradeGoodsMapper;

    @Autowired
    private TradeGoodsNumberLogMapper tradeGoodsNumberLogMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = null;
        String tags = null;
        String keys = null;

        String body = null;
        TradeMqConsumerLog tradeMqConsumerLog;
        try {
            msgId = messageExt.getMsgId();
            tags = messageExt.getTags();
            keys = messageExt.getKeys();

            body = new String(messageExt.getBody(), "UTF-8");

            TradeMqConsumerLogKey tradeMqConsumerLogKey = new TradeMqConsumerLogKey();
            tradeMqConsumerLogKey.setMsgKey(msgId);
            tradeMqConsumerLogKey.setMsgTag(tags);
            tradeMqConsumerLogKey.setGroupName(groupName);

            tradeMqConsumerLog = tradeMqConsumerLogMapper.selectByPrimaryKey(tradeMqConsumerLogKey);

            if (tradeMqConsumerLog != null) {
                Integer consumerStatus = tradeMqConsumerLog.getConsumerStatus();
                if (ShopCode.SHOP_MQ_MESSAGE_STATUS_SUCCESS.getCode().intValue() == consumerStatus) {
                    log.info("消息 " + msgId + ",已经处理");
                    return;
                }
                if (ShopCode.SHOP_MQ_MESSAGE_STATUS_PROCESSING.getCode().intValue() == consumerStatus) {
                    log.info("消息 " + msgId + ",正在处理");
                    return;
                }

                if (ShopCode.SHOP_MQ_MESSAGE_STATUS_FAIL.getCode().intValue() == consumerStatus) {
                    Integer consumerTimes = tradeMqConsumerLog.getConsumerTimes();
                    if (consumerTimes > 3) {
                        log.info("消息:" + msgId + ",处理超过3次， 不处理");
                        return;
                    }
                    tradeMqConsumerLog.setConsumerStatus(ShopCode.SHOP_MQ_MESSAGE_STATUS_PROCESSING.getCode());


                    TradeMqConsumerLogExample tradeMqConsumerLogExample = new TradeMqConsumerLogExample();
                    TradeMqConsumerLogExample.Criteria criteria = tradeMqConsumerLogExample.createCriteria();
                    criteria.andMsgTagEqualTo(tradeMqConsumerLog.getMsgTag());
                    criteria.andMsgKeyEqualTo(tradeMqConsumerLog.getMsgKey());
                    criteria.andConsumerTimesEqualTo(tradeMqConsumerLog.getConsumerTimes());
                    criteria.andGroupNameEqualTo(groupName);


                    int updateResult = tradeMqConsumerLogMapper.updateByExampleSelective(tradeMqConsumerLog, tradeMqConsumerLogExample);
                    if (updateResult > 0) {
                        log.info("遇到并发修改，稍后处理");
                    }
                    return;
                }

            } else {
                tradeMqConsumerLog = new TradeMqConsumerLog();
                tradeMqConsumerLog.setMsgId(msgId);
                tradeMqConsumerLog.setMsgTag(tags);
                tradeMqConsumerLog.setMsgId(msgId);
                tradeMqConsumerLog.setMsgBody(body);
                tradeMqConsumerLog.setMsgKey(keys);
                tradeMqConsumerLog.setConsumerTimes(0);
                tradeMqConsumerLog.setGroupName(groupName);
                tradeMqConsumerLog.setConsumerStatus(ShopCode.SHOP_MQ_MESSAGE_STATUS_PROCESSING.getCode());

                tradeMqConsumerLogMapper.insert(tradeMqConsumerLog);
            }

            resetGoodsNumber(body);

            tradeMqConsumerLog.setConsumerStatus(ShopCode.SHOP_MQ_MESSAGE_STATUS_SUCCESS.getCode());
            tradeMqConsumerLog.setConsumerTimestamp(new Date());

            tradeMqConsumerLogMapper.updateByPrimaryKey(tradeMqConsumerLog);
            log.info("回退库存成功");
        } catch (Exception e) {
            e.printStackTrace();

            TradeMqConsumerLogKey tradeMqConsumerLogKey = new TradeMqConsumerLogKey();
            tradeMqConsumerLogKey.setMsgKey(msgId);
            tradeMqConsumerLogKey.setMsgTag(tags);
            tradeMqConsumerLogKey.setGroupName(groupName);

            tradeMqConsumerLog = tradeMqConsumerLogMapper.selectByPrimaryKey(tradeMqConsumerLogKey);

            if (tradeMqConsumerLog == null) {
                tradeMqConsumerLog = new TradeMqConsumerLog();
                tradeMqConsumerLog.setMsgId(msgId);
                tradeMqConsumerLog.setMsgTag(tags);
                tradeMqConsumerLog.setMsgKey(keys);
                tradeMqConsumerLog.setMsgId(msgId);
                tradeMqConsumerLog.setMsgBody(body);
                tradeMqConsumerLog.setConsumerTimes(1);
                tradeMqConsumerLog.setGroupName(groupName);
                tradeMqConsumerLog.setConsumerStatus(ShopCode.SHOP_MQ_MESSAGE_STATUS_PROCESSING.getCode());

                tradeMqConsumerLogMapper.insert(tradeMqConsumerLog);
            }else{
                tradeMqConsumerLog.setConsumerTimes(tradeMqConsumerLog.getConsumerTimes() + 1);
                tradeMqConsumerLogMapper.updateByPrimaryKey(tradeMqConsumerLog);
            }
        }

    }

    /**
     * 回退库存
     *
     * @param body
     */
    private void resetGoodsNumber(String body) {
        MQEntity mqEntity = JSON.parseObject(body, MQEntity.class);
        Long goodsId = mqEntity.getGoodsId();
        TradeGoods tradeGoods = tradeGoodsMapper.selectByPrimaryKey(goodsId);
        tradeGoods.setGoodsNumber(tradeGoods.getGoodsNumber() + mqEntity.getGoodsNum());
        tradeGoodsMapper.updateByPrimaryKey(tradeGoods);

        TradeGoodsNumberLog tradeGoodsNumberLog = new TradeGoodsNumberLog();
        tradeGoodsNumberLog.setOrderId(mqEntity.getOrderId());
        tradeGoodsNumberLog.setGoodsId(mqEntity.getGoodsId());
        tradeGoodsNumberLog.setGoodsNumber(mqEntity.getGoodsNum());
        tradeGoodsNumberLog.setLogTime(new Date());


        tradeGoodsNumberLogMapper.deleteByPrimaryKey(tradeGoodsNumberLog);
    }
}
