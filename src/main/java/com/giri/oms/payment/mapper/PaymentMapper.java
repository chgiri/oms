package com.giri.oms.payment.mapper;

import com.giri.oms.payment.dto.PaymentResponse;
import com.giri.oms.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse mapToPaymentResponse(Payment payment);

    // Payment is intentionally NOT built from PaymentRequest here — resolving/validating
    // the order requires a call to OrderService, which is business logic that belongs
    // in the service layer, not the mapper.
}