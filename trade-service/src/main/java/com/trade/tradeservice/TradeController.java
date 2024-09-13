package com.trade.tradeservice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class TradeController {

    @Autowired
    private BankAccountProxy bankAccountProxy;
    @Autowired
    private CryptoWalletProxy cryptoWalletProxy;
    @Autowired
    private TradeRepository repository;
    @Autowired
    private CurrencyConversionProxy currencyConversionProxy;
    @Autowired
    private CurrencyExchangeProxy currencyExchangeProxy;

    @Value("#{'${crypto.currencies}'.split(',')}")
    private List<String> cryptoCurrencies;

    @Value("#{'${primary.fiat.currencies}'.split(',')}")
    private List<String> primaryFiatCurrencies;

    @Value("#{'${secondary.fiat.currencies}'.split(',')}")
    private List<String> secondaryFiatCurrencies;

    @GetMapping("/trade-service/from/{from}/to/{to}/quantity/{quantity}")
    public ResponseEntity<?> makeTrade(
            @PathVariable String from,
            @PathVariable String to,
            @PathVariable Double quantity,
            HttpServletRequest request) throws Exception {

        String email = request.getHeader("X-User-Email");

        try {
            if (from.equals(to)) {
                throw new CustomExceptions.InvalidRequestParameterValueException("Cannot trade between the same currency!");
            }

            BankAccountDto userBankAccount = bankAccountProxy.getBankAccount(email).getBody();
            CryptoWalletDto userCryptoWallet = cryptoWalletProxy.getCryptoWallet(email).getBody();
            TradeService values = repository.findByFromAndTo(from, to);

            if (userCryptoWallet == null || userBankAccount == null) {
                throw new CustomExceptions.EntityDoesntExistException("User with given email " + email + " has no account in our system!");
            }

            if (cryptoCurrencies.contains(from) && primaryFiatCurrencies.contains(to)) {
                BigDecimal conversionMultiple = values.getConversionMultiple();
                BigDecimal cryptoAmount = getCryptoAmount(userCryptoWallet, from);
                if (cryptoAmount.compareTo(BigDecimal.valueOf(quantity)) >= 0) {
                    cryptoWalletProxy.changeCryptoWalletBalance(email, BigDecimal.valueOf(quantity), from, false);
                    BankAccountDto account = bankAccountProxy.changeBankAccountBalance(email, conversionMultiple.multiply(BigDecimal.valueOf(quantity)), to, true).getBody();
                    return ResponseEntity.ok().body(
                            "User : " + email + System.lineSeparator() +
                            "RSD amount : " + account.getRSD_amount().toString() + System.lineSeparator() +
                            "USD amount : " + account.getUSD_amount().toString() + System.lineSeparator() +
                            "GBP amount : " + account.getGBP_amount().toString() + System.lineSeparator() +
                            "CHF amount : " + account.getCHF_amount().toString() + System.lineSeparator() +
                            "EUR amount : " + account.getEUR_amount().toString() + System.lineSeparator() +
                            "User successfully converted " + quantity
                            + " of " + from + " to " + conversionMultiple.multiply(BigDecimal.valueOf(quantity)) + " of " + to);
                } else {
                    throw new CustomExceptions.InsufficientFundsException("Amount of " + from + " user has in his crypto wallet is less than wanted quantity: " + quantity);
                }
            } else if (primaryFiatCurrencies.contains(from) && cryptoCurrencies.contains(to)) {
                BigDecimal conversionMultiple = values.getConversionMultiple();
                BigDecimal fiatAmount = getFiatAmount(userBankAccount, from);
                if (fiatAmount.compareTo(BigDecimal.valueOf(quantity)) >= 0) {
                    bankAccountProxy.changeBankAccountBalance(email, BigDecimal.valueOf(quantity), from, false);
                    CryptoWalletDto wallet = cryptoWalletProxy.changeCryptoWalletBalance(email, conversionMultiple.multiply(BigDecimal.valueOf(quantity)), to, true).getBody();
                    return ResponseEntity.ok().body(
                            "User : " + email + System.lineSeparator() +
                                    "BTC amount : " + wallet.getBTC_amount().toString() + System.lineSeparator() +
                                    "ETH amount : " + wallet.getETH_amount().toString() + System.lineSeparator() +
                                    "LTC amount : " + wallet.getLTC_amount().toString() + System.lineSeparator() +
                                    "XRP amount : " + wallet.getXRP_amount().toString() + System.lineSeparator() +
                                    "User successfully converted " + quantity
                                    + " of " + from + " to " + conversionMultiple.multiply(BigDecimal.valueOf(quantity)) + " of " + to);
                } else {
                    throw new CustomExceptions.InsufficientFundsException("Amount of " + from + " user has on his bank account is less than wanted quantity: " + quantity);
                }
            } else if (cryptoCurrencies.contains(from) && secondaryFiatCurrencies.contains(to)) {
                BigDecimal cryptoAmount = getCryptoAmount(userCryptoWallet, from);
                TradeService cryptoToEUR = repository.findByFromAndTo(from, "EUR");
                BigDecimal eurToCurr = currencyExchangeProxy.getExchange("EUR", to).getBody().getConversionMultiple();

                if (cryptoAmount.compareTo(BigDecimal.valueOf(quantity)) >= 0) {
                    cryptoWalletProxy.changeCryptoWalletBalance(email, BigDecimal.valueOf(quantity), from, false);
                    bankAccountProxy.changeBankAccountBalance(email, cryptoToEUR.getConversionMultiple().multiply(BigDecimal.valueOf(quantity)), "EUR", true);
                    bankAccountProxy.changeBankAccountBalance(email, cryptoToEUR.getConversionMultiple().multiply(BigDecimal.valueOf(quantity)), "EUR", false);
                    BankAccountDto account = bankAccountProxy.changeBankAccountBalance(email, cryptoToEUR.getConversionMultiple().multiply(BigDecimal.valueOf(quantity)).multiply(eurToCurr), to, true).getBody();
                    return ResponseEntity.ok().body(
                            "User : " + email + System.lineSeparator() +
                                    "RSD amount : " + account.getRSD_amount().toString() + System.lineSeparator() +
                                    "USD amount : " + account.getUSD_amount().toString() + System.lineSeparator() +
                                    "GBP amount : " + account.getGBP_amount().toString() + System.lineSeparator() +
                                    "CHF amount : " + account.getCHF_amount().toString() + System.lineSeparator() +
                                    "EUR amount : " + account.getEUR_amount().toString() + System.lineSeparator() +
                                    "User successfully converted " + quantity
                                    + " of " + from + " to " + cryptoToEUR.getConversionMultiple().multiply(BigDecimal.valueOf(quantity)).multiply(eurToCurr) + " of " + to);
                } else {
                    throw new CustomExceptions.InsufficientFundsException("Amount of " + from + " user has in his crypto wallet is less than wanted quantity: " + quantity);
                }
            } else if (secondaryFiatCurrencies.contains(from) && cryptoCurrencies.contains(to)) {
                BigDecimal fiatAmount = getFiatAmount(userBankAccount, from);
                BigDecimal currToEur = currencyExchangeProxy.getExchange(from, "EUR").getBody().getConversionMultiple();
                TradeService eurToCrypto = repository.findByFromAndTo("EUR", to);

                if (fiatAmount.compareTo(BigDecimal.valueOf(quantity)) >= 0) {
                    bankAccountProxy.changeBankAccountBalance(email, BigDecimal.valueOf(quantity), from, false);
                    bankAccountProxy.changeBankAccountBalance(email, BigDecimal.valueOf(quantity).multiply(currToEur), "EUR", true);
                    bankAccountProxy.changeBankAccountBalance(email, currToEur.multiply(BigDecimal.valueOf(quantity)), "EUR", false);
                    CryptoWalletDto wallet = cryptoWalletProxy.changeCryptoWalletBalance(email, currToEur.multiply(BigDecimal.valueOf(quantity).multiply(eurToCrypto.getConversionMultiple())), to, true).getBody();
                    return ResponseEntity.ok().body(
                            "User : " + email + System.lineSeparator() +
                                    "BTC amount : " + wallet.getBTC_amount().toString() + System.lineSeparator() +
                                    "ETH amount : " + wallet.getETH_amount().toString() + System.lineSeparator() +
                                    "LTC amount : " + wallet.getLTC_amount().toString() + System.lineSeparator() +
                                    "XRP amount : " + wallet.getXRP_amount().toString() + System.lineSeparator() +
                                    "User successfully converted " + quantity
                                    + " of " + from + " to " + currToEur.multiply(BigDecimal.valueOf(quantity).multiply(eurToCrypto.getConversionMultiple())) + " of " + to);
                } else {
                    throw new CustomExceptions.InsufficientFundsException("Amount of " + from + " user has in his bank account is less than wanted quantity: " + quantity);
                }
            } else {
                throw new CustomExceptions.InvalidRequestParameterValueException("From currency " + from + ", and to currency " + to + ", " +
                        "which are given in the request parameters, are either non-existent or not allowed!");
            }

        } catch (CustomExceptions.EntityDoesntExistException | CustomExceptions.InvalidRequestParameterValueException |
                 CustomExceptions.InsufficientFundsException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    private BigDecimal getCryptoAmount(CryptoWalletDto userCryptoWallet, String currency) {
        switch (currency) {
            case "BTC":
                return userCryptoWallet.getBTC_amount();
            case "ETH":
                return userCryptoWallet.getETH_amount();
            case "LTC":
                return userCryptoWallet.getLTC_amount();
            default:
                return userCryptoWallet.getXRP_amount();
        }
    }

    private BigDecimal getFiatAmount(BankAccountDto userBankAccount, String currency) {
        switch (currency) {
            case "EUR":
                return userBankAccount.getEUR_amount();
            case "USD":
                return userBankAccount.getUSD_amount();
            case "RSD":
                return userBankAccount.getRSD_amount();
            case "GBP":
                return userBankAccount.getGBP_amount();
            default:
                return userBankAccount.getCHF_amount();
        }
    }
}
