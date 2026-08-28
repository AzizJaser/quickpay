package com.quickpay.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickpay.wallet.domain.LedgerEntry;
import com.quickpay.wallet.domain.NotificationEvent;
import com.quickpay.wallet.domain.Wallet;
import com.quickpay.wallet.dto.event.MoneyMovedPayload;
import com.quickpay.wallet.enums.TransactionType;
import com.quickpay.wallet.enums.WalletStatus;
import com.quickpay.wallet.exception.*;
import com.quickpay.wallet.repository.LedgerEntryRepository;
import com.quickpay.wallet.repository.NotificationEventRepository;
import com.quickpay.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final ObjectMapper objectMapper;
    private final static String INTERNAL_TRANSACTION_ACCOUNT = "000000000001";
    private final static String INTERNAL_OUTWARD_ACCOUNT = "000000000002";
    private final static String SUSPENSE_ACCOUNT = "000000000003";
    private final static String BILLER_ACCOUNT = "000000000004";
    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    @Value("${wallet.max-per-cif:5}")
    private int maxNumberOfWallets;

    @Value("${wallet.create-max-retries:5}")
    private int createMaxRetries;



    // need to write the DTOs for this service
    @Transactional
    public LedgerEntry transfer(String debitedWalletNumber, String creditedWalletNumber, Long amount, String idempotencyKey,String original_entry_id,String settles_entry_id, TransactionType transactionType){

        if(amount == null || amount <= 0) {
                throw new InvalidAmountException("Unable to the transaction, amount is 0 or less");
        }

        if(debitedWalletNumber.equals(creditedWalletNumber)){
            throw new ConflictEntryException("debited wallet is the credited wallet, please make sure debited wallet is different than credited wallet.");
        }

        if(ledgerEntryRepository.existsByIdempotencyKey(idempotencyKey)){
            throw new DuplicatedEntryException("Duplicated entry rejected!",idempotencyKey);
        }

        Wallet debitedWallet;
        Wallet creditedWallet;

        if(compareWallets(debitedWalletNumber,creditedWalletNumber).equals(debitedWalletNumber)){
            debitedWallet = lockOrThrow(debitedWalletNumber);
            creditedWallet = lockOrThrow(creditedWalletNumber);
        }else {
            creditedWallet = lockOrThrow(creditedWalletNumber);
            debitedWallet = lockOrThrow(debitedWalletNumber);
        }
        if(debitedWallet.getStatus() != WalletStatus.Active){
            throw new WalletNotActiveException("Wallet not active",debitedWalletNumber,debitedWallet.getStatus());
        }
        if(creditedWallet.getStatus() != WalletStatus.Active){
            throw new WalletNotActiveException("Wallet not active",creditedWalletNumber,creditedWallet.getStatus());
        }
        if(debitedWallet.getBalance() >= amount || debitedWallet.isAllowsNegative()){
            debitedWallet.setBalance(debitedWallet.getBalance()-amount);
            creditedWallet.setBalance(creditedWallet.getBalance()+amount);
        }else{
            throw new InsufficientBalanceException("insufficient balance for wallet number = "+debitedWalletNumber,debitedWalletNumber);
        }
        // post ledger entry
        LedgerEntry entry = new LedgerEntry(debitedWalletNumber,creditedWalletNumber,-amount,amount,idempotencyKey,original_entry_id,settles_entry_id,transactionType);
        ledgerEntryRepository.save(entry);

        if(entry.getTransactionType().isCustomerFacing()){
            if(!debitedWallet.isInternal()){
                notificationEventRepository.save(notificationEventHelper("wallet.money.sent",debitedWallet.getCif(),debitedWalletNumber,creditedWalletNumber,entry.getEntryId(), entry.getCredited_amount()));
            }
            if (!creditedWallet.isInternal()){
                notificationEventRepository.save(notificationEventHelper("wallet.money.received",creditedWallet.getCif(),creditedWalletNumber,debitedWalletNumber,entry.getEntryId(), entry.getCredited_amount()));
            }
        }
        logger.info("ledger with entry id {} saved",entry.getEntryId());
        return entry;
    }

    @Transactional
    public LedgerEntry topUp(String wallet_number,Long amount,String idempotencyKey){
        return transfer(INTERNAL_TRANSACTION_ACCOUNT,wallet_number,amount,idempotencyKey,null,null,TransactionType.DEPOSIT);
    }
    @Transactional
    public LedgerEntry withdraw(String wallet_number,Long amount,String idempotencyKey){
        return transfer(wallet_number,INTERNAL_OUTWARD_ACCOUNT,amount,idempotencyKey,null,null,TransactionType.WITHDRAWAL);
    }
    @Transactional
    public LedgerEntry revers(String original_entry_id, String idempotencyKey){
        LedgerEntry entry = ledgerEntryRepository.findByEntryId(original_entry_id)
                .orElseThrow(()-> new EntryNotFoundException(original_entry_id));
        TransactionType type = entry.getTransactionType() == TransactionType.HOLD ? TransactionType.RELEASE : TransactionType.REVERSAL;
        return transfer(entry.getCredited_wallet_number(),entry.getDebited_wallet_number(), entry.getCredited_amount(), idempotencyKey,original_entry_id,null,type);
    }
    @Transactional
    public LedgerEntry hold(String wallet_number,Long amount,String idempotencyKey){
        return transfer(wallet_number,SUSPENSE_ACCOUNT,amount,idempotencyKey,null,null,TransactionType.HOLD);
    }

    @Transactional
    public LedgerEntry settle(String entryId,String idempotencyKey){
        LedgerEntry entry = ledgerEntryRepository.findByEntryId(entryId)
                .orElseThrow(()-> new EntryNotFoundException(entryId));

        if(entry.getTransactionType() == TransactionType.HOLD){

            Optional<LedgerEntry> discharged = ledgerEntryRepository.findEntryByHoldId(entryId);
            if(discharged.isPresent()){
                LedgerEntry discharger = discharged.get();
                throw new HoldAlreadyDischargedException(entryId, discharger.getEntryId(), discharger.getTransactionType());
            }
            LedgerEntry settledEntry = transfer(SUSPENSE_ACCOUNT, BILLER_ACCOUNT, entry.getCredited_amount(), idempotencyKey,null,entryId,TransactionType.SETTLEMENT);
            return settledEntry;
        } else{
            throw new UnHoldTransactionException(entryId);
        }
    }

    public Wallet createWallet(String cif,String wallet_name){
        for(int attempt = 0; attempt < createMaxRetries ; attempt++){
            int index = walletRepository.countByCif(cif);
            if(index >= maxNumberOfWallets){
                throw new NumberOfWalletsExceededException("Number of wallet exceeded for CIF: " + cif);
            }
            String wallet_number = String.format("%02d", index) + cif;

            Wallet wallet = new Wallet(wallet_number,cif,wallet_name, 0L, WalletStatus.Pending,false,false,null); // db will take care of date
            try{
                walletRepository.save(wallet);
                return wallet;
            }
            catch (DataIntegrityViolationException e){
                logger.warn("collision detected!");
            }
        }
        throw new WalletNumberAllocationException("could not allocate a wallet number for cif " + cif + " after " + createMaxRetries + " attempts");
    }

    public Wallet activateWallet(String wallet_number){
        Wallet wallet = walletRepository.findByWalletNumber(wallet_number)
                .orElseThrow(() -> new WalletNotFoundException("Wallet was not found with id = "+wallet_number,wallet_number));
        wallet.setStatus(WalletStatus.Active);
        walletRepository.save(wallet);
        return wallet;
    }

    public Wallet suspendWallet(String wallet_number){
        Wallet wallet = walletRepository.findByWalletNumber(wallet_number)
                .orElseThrow(() -> new WalletNotFoundException("Wallet was not found with id = "+wallet_number,wallet_number));
        wallet.setStatus(WalletStatus.Suspended);
        walletRepository.save(wallet);
        return wallet;
    }

    public Wallet closeWallet(String wallet_number){
        Wallet wallet = walletRepository.findByWalletNumber(wallet_number)
                .orElseThrow(() -> new WalletNotFoundException("Wallet was not found with id = "+wallet_number,wallet_number));
        wallet.setStatus(WalletStatus.Closed);
        return walletRepository.save(wallet);
    }

    public Wallet fetchWallet(String wallet_number){
        return walletRepository.findByWalletNumber(wallet_number)
                .orElseThrow(() -> new WalletNotFoundException("Wallet was not found with id = "+wallet_number,wallet_number));
    }


    private Wallet lockOrThrow(String walletNumber){
        return walletRepository.findWithLockByWalletNumber(walletNumber)
                .orElseThrow(() -> new WalletNotFoundException("Wallet was not found with id = "+walletNumber,walletNumber));
    }
    private String compareWallets(String a, String b){
        return a.compareTo(b) > 0 ? a : b;
    }

    private NotificationEvent notificationEventHelper(String eventType,String cif, String walletNumber,String counterParty ,String entryId, Long amount) throws ParsingNotificationEventException {
        MoneyMovedPayload eventPayload = new MoneyMovedPayload(entryId,cif,walletNumber,counterParty,amount,LocalDateTime.now());
        String payload = null;
        try {
            payload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new ParsingNotificationEventException(entryId);
        }
        NotificationEvent event = new NotificationEvent(payload,null,eventType,UUID.randomUUID(), MDC.get("correlationId"));
        return event;
    }
}
