package com.samyookgoo.palgoosam.payment.domain;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.global.exception.ErrorCode;
import com.samyookgoo.palgoosam.payment.constant.PaymentStatus;
import com.samyookgoo.palgoosam.payment.constant.PaymentType;
import com.samyookgoo.palgoosam.payment.controller.request.PaymentCreateRequest;
import com.samyookgoo.palgoosam.payment.exception.PaymentBadRequestException;
import com.samyookgoo.palgoosam.payment.exception.PaymentInvalidStateException;
import com.samyookgoo.palgoosam.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne
    @JoinColumn(name = "seller_id")
    private User seller;

    @OneToOne
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String addressLine1;

    private String addressLine2;

    @Column(nullable = false)
    private String zipCode;

    @Column(nullable = false)
    private Integer itemPrice;

    @Column(nullable = false)
    private Integer deliveryFee;

    @Column(nullable = false)
    private Integer finalPrice;

    private LocalDateTime approvedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // TODO: orderId로 네이밍 변경 고려
    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private PaymentType type;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void validatePaymentConditions(long amount) {
        if (this.status == PaymentStatus.PAID) {
            throw new PaymentInvalidStateException(ErrorCode.ALREADY_PAID);
        }

        if (this.finalPrice != amount) {
            throw new PaymentBadRequestException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    public void markAsPaid(OffsetDateTime approvedAt) {
        this.status = PaymentStatus.PAID;
        this.approvedAt = approvedAt.toLocalDateTime();
    }

    public void markAsFailed() {
        if (this.status == PaymentStatus.PAID) {
            throw new PaymentInvalidStateException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        if (this.status != PaymentStatus.FAILED) {
            this.status = PaymentStatus.FAILED;
        }
    }

    public static Payment of(Auction auction, User buyer, PaymentCreateRequest request, int deliveryFee,
                             String orderNumber) {
        return Payment.builder()
                .buyer(buyer)
                .seller(auction.getSeller())
                .auction(auction)
                .recipientName(request.getRecipientName())
                .recipientEmail(buyer.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .zipCode(request.getZipCode())
                .itemPrice(request.getItemPrice())
                .deliveryFee(deliveryFee)
                .finalPrice(request.getItemPrice() + deliveryFee)
                .orderNumber(orderNumber)
                .status(PaymentStatus.READY)
                .build();
    }
}
