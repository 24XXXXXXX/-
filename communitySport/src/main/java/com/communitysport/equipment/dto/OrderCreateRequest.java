package com.communitysport.equipment.dto;

public class OrderCreateRequest {

    // 创建订单请求 DTO（用户端下单）：
    // - 只包含收货信息（快照）
    // - 不包含商品列表与金额：
    //   - 商品明细来自购物车
    //   - 金额由服务端根据商品单价与数量重新计算
    //   这样可以避免前端篡改订单金额/商品数据

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }
}
