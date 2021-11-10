package com.leizhou.shop.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.leizhou.api.IUserService;
import com.leizhou.constant.ShopCode;
import com.leizhou.entity.Result;
import com.leizhou.exception.CastException;
import com.leizhou.shop.mapper.TradeUserMapper;
import com.leizhou.shop.mapper.TradeUserMoneyLogMapper;
import com.leizhou.shop.pojo.TradeUser;
import com.leizhou.shop.pojo.TradeUserMoneyLog;
import com.leizhou.shop.pojo.TradeUserMoneyLogExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

@Component
@Service(interfaceClass = IUserService.class)
public class UserServiceImpl implements IUserService {

    @Autowired
    private TradeUserMapper tradeUserMapper;

    @Autowired
    private TradeUserMoneyLogMapper tradeUserMoneyLogMapper;

    @Override
    public TradeUser findOne(Long userId) {
        if (userId == null) {
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }
        return tradeUserMapper.selectByPrimaryKey(userId);
    }

    @Override
    public Result updateMoneyPaid(TradeUserMoneyLog userMoneyLog) {
        if (userMoneyLog == null ||
                userMoneyLog.getUserId() == null ||
                userMoneyLog.getOrderId() == null ||
                userMoneyLog.getUseMoney() == null ||
                userMoneyLog.getUseMoney().compareTo(BigDecimal.ZERO) <= 0) {
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }

        TradeUserMoneyLogExample tradeUserMoneyLogExample = new TradeUserMoneyLogExample();
        TradeUserMoneyLogExample.Criteria criteria = tradeUserMoneyLogExample.createCriteria();
        criteria.andOrderIdEqualTo(userMoneyLog.getOrderId());
        criteria.andUserIdEqualTo(userMoneyLog.getUserId());
        int r = tradeUserMoneyLogMapper.countByExample(tradeUserMoneyLogExample);
        TradeUser tradeUser = tradeUserMapper.selectByPrimaryKey(userMoneyLog.getUserId());
        // balance money
        if (userMoneyLog.getMoneyLogType().intValue() == ShopCode.SHOP_USER_MONEY_PAID.getCode().intValue()) {
            if (r > 0) {
                // already paid
                CastException.cast(ShopCode.SHOP_ORDER_PAY_STATUS_IS_PAY);

                tradeUser.setUserMoney(new BigDecimal(tradeUser.getUserMoney()).subtract(userMoneyLog.getUseMoney()).longValue());
                tradeUserMapper.updateByPrimaryKey(tradeUser);
            }
        }
        // refund
        if (userMoneyLog.getMoneyLogType().intValue() == ShopCode.SHOP_USER_MONEY_REFUND.getCode().intValue()) {
            if (r < 0) {
                TradeUserMoneyLogExample tradeUserMoneyLogExample1 = new TradeUserMoneyLogExample();
                TradeUserMoneyLogExample.Criteria criteria1 = tradeUserMoneyLogExample1.createCriteria();
                criteria1.andOrderIdEqualTo(userMoneyLog.getOrderId());
                criteria1.andUserIdEqualTo(userMoneyLog.getUserId());
                criteria1.andMoneyLogTypeEqualTo(ShopCode.SHOP_USER_MONEY_REFUND.getCode());

                int existsRefund = tradeUserMoneyLogMapper.countByExample(tradeUserMoneyLogExample1);
                if (existsRefund > 0){
                    CastException.cast(ShopCode.SHOP_USER_MONEY_REFUND_ALREADY);
                }

                tradeUser.setUserMoney(new BigDecimal(tradeUser.getUserMoney()).add(userMoneyLog.getUseMoney()).longValue());
                tradeUserMapper.updateByPrimaryKey(tradeUser);
            }
        }

        userMoneyLog.setCreateTime(new Date());
        tradeUserMoneyLogMapper.insert(userMoneyLog);
        return new Result(ShopCode.SHOP_SUCCESS.getSuccess(), ShopCode.SHOP_SUCCESS.getMessage());
    }
}
