package com.quickpay.bill.client;

import com.quickpay.bill.dto.request.HoldRequest;
import com.quickpay.bill.dto.request.ReverseRequest;
import com.quickpay.bill.dto.request.SettleRequest;
import com.quickpay.bill.dto.request.TransferRequest;
import com.quickpay.bill.dto.response.TransferResponse;
import com.quickpay.bill.exception.FundIsReleasedException;
import com.quickpay.bill.exception.ReserveDeclinedException;
import com.quickpay.bill.exception.SettleRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {


    private final RestClient walletRestClient;

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
                .uri("/v1/transfer/hold")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new HoldRequest(debited,amount))
                .retrieve()
                .onStatus(status -> status.value() == 400, ((request, response) -> {
                    throw new ReserveDeclinedException("wallet number '"+debited+"' declined to reserve");
                }))
                .onStatus(status -> status.value()==409, ((request, response) -> {
                    return;
                }))
                .body(TransferResponse.class);
    }

    public void capture(String entryId, String idempotencyKey){
        try {
            walletRestClient
                    .post()
                    .uri("/v1/transfer/settle")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SettleRequest(entryId))
                    .retrieve()
                    .onStatus(status -> status.value() == 400, ((request, response) -> {
                        throw new SettleRejectedException(entryId);
                    }))
                    .body(TransferResponse.class);
        } catch (HttpClientErrorException.Conflict e){
            ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
            Object type = (problem == null || problem.getProperties() == null) ? null : problem.getProperties().get("dischargeType");
            if(type == null) {
                // do nothing -> payment is already done
            } else {
                if(type.equals("SETTLEMENT")){
                    return;
                } else{
                    throw new  FundIsReleasedException(entryId);
                }
            }
        }
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
