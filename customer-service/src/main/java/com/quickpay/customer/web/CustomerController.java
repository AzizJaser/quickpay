package com.quickpay.customer.web;

import com.quickpay.customer.dto.request.CustomerActivateRequest;
import com.quickpay.customer.dto.request.CustomerRequest;
import com.quickpay.customer.dto.response.CustomerResponse;
import com.quickpay.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/customer")
public class CustomerController {

    private final CustomerService customerService;


    @PostMapping
    public ResponseEntity<CustomerResponse> registerNewCustomer(@RequestBody @Valid CustomerRequest request){
        return new ResponseEntity<>(customerService.registerCustomer(request), HttpStatus.CREATED);
    }

    @PutMapping("/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(@RequestBody @Valid CustomerActivateRequest request){
        return new ResponseEntity<>(customerService.activateCustomer(request), HttpStatus.OK);
    }


}