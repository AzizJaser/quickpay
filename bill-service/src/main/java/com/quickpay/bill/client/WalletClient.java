package com.quickpay.bill.client;

import com.quickpay.bill.dto.request.ReverseRequest;
import com.quickpay.bill.dto.request.TransferRequest;
import com.quickpay.bill.dto.response.TransferResponse;
import com.quickpay.bill.exception.ReserveDeclinedException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {


    private final RestClient walletRestClient;

    private final static String SUSPENSE_ACCOUNT = "000000000003";

    private final static String Biller_ACCOUNT = "000000000004";

    public WalletClient(RestClient walletRestClient){
        this.walletRestClient = walletRestClient;
    }

    public TransferResponse transfer(String debited, String credited, Long amount, String idempotencyKey){
        return walletRestClient
                .post()
                .uri("/v1/transfer/betweenWallets")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TransferRequest(debited,credited,amount))
                .retrieve()
                .onStatus(status -> status.value()==409, ((request, response) -> {
                    return;
                }))
                .body(TransferResponse.class);
    }

    public TransferResponse reserve(String debited,Long amount, String idempotencyKey){
        return walletRestClient
                .post()
                .uri("/v1/transfer/betweenWallets")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TransferRequest(debited, SUSPENSE_ACCOUNT,amount))
                .retrieve()
                .onStatus(status -> status.value() == 400, ((request, response) -> {
                    throw new ReserveDeclinedException("wallet number '"+debited+"' declined to reserve");
                }))
                .onStatus(status -> status.value()==409, ((request, response) -> {
                    return;
                }))
                .body(TransferResponse.class);
    }

    public TransferResponse capture(Long amount, String idempotencyKey){
        return transfer(SUSPENSE_ACCOUNT,Biller_ACCOUNT,amount,idempotencyKey);
    }


    public TransferResponse reverse(String originalEntryId, String idempotencyKey){

        return walletRestClient
                .post()
                .uri("/v1/transfer/revers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReverseRequest(originalEntryId))
                .retrieve()
                .onStatus(status -> status.value() == 400, ((request, response) -> {
                    throw new ReserveDeclinedException("revers was decline for entry Id number "+originalEntryId);
                }))
                .onStatus(status -> status.value()==409, ((request, response) -> {
                    return;
                }))
                .body(TransferResponse.class);
    }
}
