package com.leizhou.shop.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.leizhou.api.IPayService;
import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradePay;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay")
public class PayController {

    @Reference
    private IPayService payService;

    @RequestMapping("/createPayment")
    public Result createPayment(@RequestBody TradePay pay){
        return payService.createPayment(pay);
    }

    @RequestMapping("/callBackPayment")
    public Result callBackPayment(@RequestBody TradePay pay) throws Exception {
        return payService.callbackPayment(pay);
    }

}
