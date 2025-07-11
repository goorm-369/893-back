package com.samyookgoo.palgoosam.payment.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TossPaymentConfirmResponse {
    private String paymentKey;
    private String orderId;
    private String orderName;
    private String approvedAt;
    private Integer totalAmount;
    private String customerEmail;
    private String customerName;
    private String customerMobilePhone;

    public static TossPaymentConfirmResponse from(TossPaymentConfirmResponse apiResponse,
                                                  String recipientEmail,
                                                  String recipientName,
                                                  String phoneNumber) {
        apiResponse.setCustomerEmail(recipientEmail);
        apiResponse.setCustomerName(recipientName);
        apiResponse.setCustomerMobilePhone(phoneNumber);
        return apiResponse;
    }
}
