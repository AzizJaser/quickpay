package com.quickpay.customer.client;

import com.quickpay.customer.dto.request.BillPaymentRequest;
import com.quickpay.customer.dto.response.BillResponse;
import com.quickpay.customer.exception.BillUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class BillClient {

    private final RestClient billRestClient;



    public BillResponse pay(BillPaymentRequest billPaymentRequest, String idempotency){
        try {

        return billRestClient
                .post()
                .uri("/bill/reserve")
                .header("Idempotency-Key",idempotency)
                .contentType(MediaType.APPLICATION_JSON)
                .body(billPaymentRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, ((request, response) -> {
                    throw new BillUnavailableException();
                }))
                .body(BillResponse.class);
        } catch (ResourceAccessException e){
            throw new BillUnavailableException(e);
        }

    }
}
