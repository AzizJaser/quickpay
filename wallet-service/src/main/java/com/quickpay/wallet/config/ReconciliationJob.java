package com.quickpay.wallet.config;

import com.quickpay.wallet.domain.Wallet;
import com.quickpay.wallet.repository.WalletRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
public class ReconciliationJob {

    WalletRepository walletRepository;

    private static final String MDC_KEY = "correlationId";

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationJob.class);

    @Scheduled(fixedDelayString = "${wallet.reconciliation-interval-ms:60000}")
    public void reconcile(){
        String correlationId = "reconcile-wallets-" + UUID.randomUUID().toString().substring(0,8);
        MDC.put(MDC_KEY,correlationId);
        try {
            List<Wallet> drifted = walletRepository.findDriftedWallets();

            if(drifted.isEmpty()){
                logger.info("reconciliation OK - {} ... all balance match the ledger");
            }else {
                drifted.forEach(wallet ->
                        logger.error("DRIFTED DETECTED: wallet={} stored balance={} disagrees with its ledger",wallet.getWallet_number(),wallet.getBalance())
                );
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
