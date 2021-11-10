package com.leizhou.shop.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.leizhou.api.IOrderService;
import com.leizhou.entity.Result;
import com.leizhou.shop.pojo.TradeOrder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Reference
    private IOrderService orderService;

    @RequestMapping("/confirm")
    public Result confirmOrder(@RequestBody TradeOrder order){
        
        return orderService.confirmOrder(order);
    }

}
