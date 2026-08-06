package com.quickpay.notification.client;

import com.quickpay.notification.dto.request.EmailNotification;
import com.quickpay.notification.dto.request.SMSNotification;
import com.quickpay.notification.dto.response.ProviderResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationProviderClient {

    private final RestClient notificationProviderClient;

    public NotificationProviderClient(RestClient notificationProviderRestClient){ // the name of the param is very important here
        this.notificationProviderClient = notificationProviderRestClient;
    }

    public ProviderResponse smsProvider(String phoneNumber,String message, String reference){

        return notificationProviderClient
                .post()
                .uri("/provider/v1/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SMSNotification(phoneNumber,message,reference))
                .retrieve()
                .onStatus(status -> status.value() == 422, ((request, response) -> {
                    // nothing the job will retry
                }))
                .body(ProviderResponse.class);
    }


    public ProviderResponse emailProvider(String email, String message, String reference){

        return notificationProviderClient
                .post()
                .uri("/provider/v1/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmailNotification(email,message,reference))
                .retrieve()
                .onStatus(status -> status.value() == 422, ((request, response) -> {
                    // nothing the job will retry
                }))
                .body(ProviderResponse.class);
    }
}
