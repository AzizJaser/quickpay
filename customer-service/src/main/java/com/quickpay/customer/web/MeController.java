package com.quickpay.customer.web;

import com.quickpay.customer.client.WalletClient;
import com.quickpay.customer.dto.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
public class MeController { // Me is the use / customer, all the request here will attribute "cif" and valid session

    private final WalletClient walletClient;


    @GetMapping("/wallets")
    public ResponseEntity<List<WalletResponse>> myWallets(@RequestAttribute("cif") String cif){
        return new ResponseEntity<>(walletClient.getWalletsByCif(cif), HttpStatus.OK);
    }
}
