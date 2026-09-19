package com.quickpay.customer.client;

import com.quickpay.customer.dto.response.WalletResponse;
import com.quickpay.customer.exception.WalletAccessDeniedException;
import com.quickpay.customer.exception.WalletNotFoundException;
import com.quickpay.customer.exception.WalletUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Type;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WalletClient {

    private final RestClient walletRestClient;


//    public WalletResponse getWalletByWalletNumber(String walletNumber){
//        try {
//            return walletRestClient
//                    .get()
//                    .uri("/v1/wallets/{walletNumber}", walletNumber)
//                    .retrieve()
//                    .onStatus(status -> status.value() == 404, (request, response) -> {
//                        throw new WalletNotFoundException(walletNumber);
//                    })
//                    .onStatus(status -> status.value() == 403, (request, response) -> {
//                        throw new WalletAccessDeniedException(walletNumber);
//                    })
//                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
//                        throw new WalletUnavailableException(walletNumber);
//                    })
//                    .body(WalletResponse.class);
//        } catch (ResourceAccessException e) {
//            throw new WalletUnavailableException(walletNumber, e);
//        }
//    }

    public List<WalletResponse> getWalletsByCif(String cif){
        try {
            return walletRestClient
                    .get()
                    .uri("/v1/wallets/{cif}/all", cif)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new WalletUnavailableException();
                    })
                    .onStatus(status -> status.value() == 403, (request, response) -> {
                        throw new WalletAccessDeniedException();
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new WalletUnavailableException();
                    })
                    .body(new ParameterizedTypeReference<List<WalletResponse>>() {});
        } catch (ResourceAccessException e) {
            throw new WalletUnavailableException(e);
        }
    }
}
