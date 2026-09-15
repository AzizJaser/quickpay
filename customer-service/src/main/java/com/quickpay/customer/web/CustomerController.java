package com.quickpay.customer.web;

import com.quickpay.customer.dto.request.CustomerRequest;
import com.quickpay.customer.dto.response.CustomerResponse;
import com.quickpay.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customerService;


    @PostMapping
    public ResponseEntity<CustomerResponse> registerNewCustomer(@RequestBody @Valid CustomerRequest request){
        return new ResponseEntity<>(customerService.registerCustomer(request), HttpStatus.CREATED);
    }


}