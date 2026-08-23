package com.quickpay.bill.config;

import com.quickpay.bill.client.BillerClient;
import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.dto.response.BillerResult;
import com.quickpay.bill.enums.BillStatus;
import com.quickpay.bill.enums.BillerStatus;
import com.quickpay.bill.repository.BillRepository;
import com.quickpay.bill.service.BillService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
public class EODReconciliationJob {

    private final BillRepository billRepository;

    private final BillService billService;

    private final BillerClient billerClient;

    private static final String MDC_KEY = "correlationId";

    private static final Logger logger = LoggerFactory.getLogger(EODReconciliationJob.class);


    @Scheduled(fixedDelayString = "${bill.eod-interval-ms:60000}")
    public void sweep(){
        String correlationId = "reconciliation-bills-" + UUID.randomUUID().toString().substring(0,8);
        MDC.put(MDC_KEY,correlationId);
        try {

            // get the reserved bill and move the funds to the customers wallets
            List<Bill> bills = billRepository.findByStatus(BillStatus.Reserved);

            for(Bill bill : bills){
                try {
                    BillerResult result = billerClient.inquire(bill.getPaymentId());
                    billService.resolve(bill,result);
                }catch (HttpServerErrorException e){
                    logger.error("biller 5xx in reconciliations ...");
                    // OPS ticket
                } catch (ResourceAccessException e){
                    logger.error("unable to connect to the biller ...");
                }catch (Exception e){
                    logger.error("unable to resolve bill "+bill.getPaymentId());
                }
            }
        } finally {
            MDC.remove(MDC_KEY);
        }

    }

}
