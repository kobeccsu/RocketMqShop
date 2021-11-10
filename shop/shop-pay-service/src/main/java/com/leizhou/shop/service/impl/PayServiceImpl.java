package com.leizhou.shop.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.leizhou.api.IPayService;
import com.leizhou.constant.ShopCode;
import com.leizhou.entity.Result;
import com.leizhou.exception.CastException;
import com.leizhou.shop.mapper.TradeMqProducerTempMapper;
import com.leizhou.shop.mapper.TradePayMapper;
import com.leizhou.shop.pojo.TradeMqProducerTemp;
import com.leizhou.shop.pojo.TradePay;
import com.leizhou.shop.pojo.TradePayExample;
import com.leizhou.utils.IDWorker;
import jdk.nashorn.internal.ir.CatchNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;


@Slf4j
@Component
@Service(interfaceClass = IPayService.class)
public class PayServiceImpl implements IPayService {

    @Autowired
    private TradePayMapper tradePayMapper;

    @Autowired
    private TradeMqProducerTempMapper tradeMqProducerTempMapper;

    @Autowired
    private IDWorker idWorker;

    @Value("rocketmq.producer.group")
    private String groupName;

    @Value("${mq.topic}")
    private String topic;

    @Value("${mq.pay.tag}")
    private String tag;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public Result createPayment(TradePay tradePay) {

        if (tradePay == null || tradePay.getOrderId() == null) {
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }

        TradePayExample example = new TradePayExample();
        TradePayExample.Criteria criteria = example.createCriteria();
        criteria.andOrderIdEqualTo(tradePay.getOrderId());
        criteria.andIsPaidEqualTo(ShopCode.SHOP_PAYMENT_IS_PAID.getCode());
        int countResult = tradePayMapper.countByExample(example);

        if (countResult > 0) {
            CastException.cast(ShopCode.SHOP_PAYMENT_IS_PAID);
        }

        tradePay.setIsPaid(ShopCode.SHOP_ORDER_PAY_STATUS_NO_PAY.getCode());
        tradePay.setPayId(idWorker.nextId());
        tradePayMapper.insert(tradePay);

        return new Result(ShopCode.SHOP_SUCCESS.getSuccess(), ShopCode.SHOP_SUCCESS.getMessage());
    }

    @Override
    public Result callbackPayment(TradePay tradePay) {
        if (tradePay.getIsPaid().intValue() != ShopCode.SHOP_ORDER_PAY_STATUS_IS_PAY.getCode().intValue()) {
            CastException.cast(ShopCode.SHOP_PAYMENT_PAY_ERROR);
            return new Result(ShopCode.SHOP_FAIL.getSuccess(), ShopCode.SHOP_FAIL.getMessage());
        }

        Long payId = tradePay.getPayId();
        TradePay pay = tradePayMapper.selectByPrimaryKey(payId);
        if (pay == null) {
            CastException.cast(ShopCode.SHOP_PAYMENT_NOT_FOUND);
        }
        pay.setIsPaid(ShopCode.SHOP_PAYMENT_IS_PAID.getCode());
        int r = tradePayMapper.updateByPrimaryKeySelective(pay);
        if (r > 0) {
            TradeMqProducerTemp tradeMqProducerTemp = new TradeMqProducerTemp();
            tradeMqProducerTemp.setId(String.valueOf(idWorker.nextId()));
            tradeMqProducerTemp.setGroupName(groupName);
            tradeMqProducerTemp.setMsgTopic(topic);
            tradeMqProducerTemp.setMsgTag(tag);
            tradeMqProducerTemp.setMsgKey(String.valueOf(tradePay.getPayId()));
            tradeMqProducerTemp.setMsgBody(JSON.toJSONString(tradePay));
            tradeMqProducerTemp.setCreateTime(new Date());

            tradeMqProducerTempMapper.insert(tradeMqProducerTemp);

            threadPoolTaskExecutor.submit(() -> {
                SendResult result = null;
                try {
                    result = sendMessage(topic, tag, String.valueOf(tradePay.getPayId()), JSON.toJSONString(tradePay));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                log.info("消息发送成功");
                if (result.getSendStatus().equals(SendStatus.SEND_OK)) {
                    tradeMqProducerTempMapper.deleteByPrimaryKey(tradeMqProducerTemp.getId());
                    log.info("删除数据库临时数据");
                }
            });
        }

        return new Result(ShopCode.SHOP_SUCCESS.getSuccess(), ShopCode.SHOP_SUCCESS.getMessage());
    }

    private SendResult sendMessage(String topic, String tag, String keys, String body) throws MQBrokerException, RemotingException, InterruptedException, MQClientException {
        if (StringUtils.isEmpty(topic)) {
            CastException.cast(ShopCode.SHOP_MQ_TOPIC_IS_EMPTY);
        }

        if (StringUtils.isEmpty(body)) {
            CastException.cast(ShopCode.SHOP_MQ_MESSAGE_BODY_IS_EMPTY);
        }

        Message message = new Message(topic, tag, keys, body.getBytes());
        return rocketMQTemplate.getProducer().send(message);
    }
}
