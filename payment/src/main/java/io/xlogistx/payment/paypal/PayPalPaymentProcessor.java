package io.xlogistx.payment.paypal;

import io.xlogistx.payment.paypal.daos.*;
import io.xlogistx.shared.data.OrderTransactionDAO;
import org.zoxweb.server.api.APIPaymentProcessor;
import org.zoxweb.server.util.DateUtil;
import org.zoxweb.shared.accounting.FinancialTransaction;
import org.zoxweb.shared.api.APIConfigInfo;
import org.zoxweb.shared.api.APIException;
import org.zoxweb.shared.api.APIExceptionHandler;
import org.zoxweb.shared.api.APIToken;
import org.zoxweb.shared.data.AddressInfo;
import org.zoxweb.shared.data.CreditCardInfo;
import org.zoxweb.shared.util.GetName;
import org.zoxweb.shared.util.SUS;

import java.io.IOException;


@SuppressWarnings("serial")
public class PayPalPaymentProcessor
    implements APIPaymentProcessor<APIToken, APIToken> {

    private static final String URL = "https://api.paypal.com";

    private APIConfigInfo configInfo;
    private volatile APIToken apiTokenDAO;

    public PayPalPaymentProcessor(APIConfigInfo configInfo) {
        setAPIConfigInfo(configInfo);
    }

    public PayPalPaymentProcessor() {

    }


    @Override
    public APIConfigInfo getAPIConfigInfo() {
        return configInfo;
    }

    @Override
    public void setAPIConfigInfo(APIConfigInfo configInfo) {
        this.configInfo = configInfo;
    }

    @Override
    public synchronized APIToken connect() throws APIException {

        if (apiTokenDAO == null) {
            apiTokenDAO = newConnection();
        }

        return apiTokenDAO;
    }

    @Override
    public APIToken newConnection() throws APIException {
        try {
            return PayPalRestAPI.OAUTH2Token(URL,
                    configInfo.getProperties().getValue((GetName)PayPalPaymentProcessorCreator.Param.API_KEY),
                    configInfo.getProperties().getValue((GetName)PayPalPaymentProcessorCreator.Param.API_SECRET));
        } catch (IOException | InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new APIException(e.getMessage());
        }
    }

    @Override
    public void close() throws APIException {

    }

    @Override
    public boolean isProviderActive() {
        return false;
    }

    @Override
    public APIExceptionHandler getAPIExceptionHandler() {
        return null;
    }

    @Override
    public void setAPIExceptionHandler(APIExceptionHandler exceptionHandler) {

    }

    @Override
    public long lastTimeAccessed() {
        return 0;
    }

    @Override
    public long inactivityDuration() {
        return 0;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public <T> T lookupProperty(GetName propertyName) {
        return null;
    }

    @Override
    public String getAppID() {
        return null;
    }

    @Override
    public void setAppID(String appID) {

    }


    @Override
    public String toCanonicalID() {
        return null;
    }

    @Override
    public String getDomainID() {
        return null;
    }

    @Override
    public void setDomainID(String domainID) {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setDescription(String str) {

    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void setName(String name) {

    }



    @Override
    public FinancialTransaction createTransaction(FinancialTransaction financialTransaction) {
        SUS.checkIfNulls("Transaction is null.", financialTransaction);

        OrderTransactionDAO orderTransactionDAO = null;

        if (financialTransaction.getReferencedNVE() instanceof OrderTransactionDAO) {
            orderTransactionDAO = (OrderTransactionDAO) financialTransaction.getReferencedNVE();
        }

        SUS.checkIfNulls("OrderTransactionDAO is missing.", orderTransactionDAO);

        CreditCardInfo creditCardDAO = orderTransactionDAO.getPaymentInfo().getCreditCard();
        AddressInfo addressDAO = orderTransactionDAO.getPaymentInfo().getBillingAddress();

        // Billing address
        PPAddress billingAddress = new PPAddress();
        billingAddress.setAddressLine1(addressDAO.getStreet());
        billingAddress.setCity(addressDAO.getCity());
        billingAddress.setState(addressDAO.getStateOrProvince());
        billingAddress.setPostalCode(addressDAO.getZIPOrPostalCode());
        billingAddress.setCountryCode(addressDAO.getCountry());

        // Credit Card
        PPCreditCard cc = new PPCreditCard();
        cc.setNumber(creditCardDAO.getCardNumber());
        cc.setType(creditCardDAO.getCardType().getDisplay());
        cc.setExpireMonth(DateUtil.getNormalizedMonth(creditCardDAO.getExpirationDate()));
        cc.setExpireYear(DateUtil.getNormalizedYear(creditCardDAO.getExpirationDate()));
        cc.setCardValidationCode(creditCardDAO.getSecurityCode());
        cc.setFirstName(creditCardDAO.getFirstName());
        cc.setLastName(creditCardDAO.getLastName());
        cc.setBillingAddress(billingAddress);

        // Amount
        PPAmount amount = new PPAmount();
        amount.setCurrency(financialTransaction.getAmount().getCurrency().name());
        amount.setTotal(financialTransaction.getAmount().getAmount().toString());

        // Transaction
        PPTransaction transaction = new PPTransaction();
        transaction.setAmount(amount);

        // Funding Instrument
        PPFundingInstrument fundingInstrument = new PPFundingInstrument();
        fundingInstrument.setCreditCard(cc);

        // Payer
        PPPayer payer = new PPPayer();
        payer.setPaymentMethod("credit_card");
        payer.getFundingInstruments().add(fundingInstrument);

        // Payment
        PPPayment payment = new PPPayment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.getTransactions().add(transaction);

        try {
            payment = PayPalRestAPI.payment(apiTokenDAO, URL, payment);
        } catch (IOException | InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new APIException("Transaction creation failed: " + e.getMessage());
        }

        financialTransaction.setExternalReference(payment.getID());

        return financialTransaction;
    }

    @Override
    public FinancialTransaction lookupTransaction(FinancialTransaction financialTransaction) {
        return null;
    }

    @Override
    public FinancialTransaction updateTransaction(FinancialTransaction financialTransaction) {
        return null;
    }

    @Override
    public FinancialTransaction cancelTransaction(FinancialTransaction financialTransaction) {
        return null;
    }

    @Override
    public FinancialTransaction captureTransaction(FinancialTransaction financialTransaction) {
        return null;
    }

    @Override
    public FinancialTransaction refundTransaction(FinancialTransaction financialTransaction) {
        SUS.checkIfNulls("Transaction is null.", financialTransaction);
        SUS.checkIfNulls("External reference is null.", financialTransaction.getExternalReference());
        SUS.checkIfNulls("Amount is null.", financialTransaction.getAmount());

        PPRefund refund = null;

        try {
            refund = PayPalRestAPI.refund(apiTokenDAO, URL,
                    financialTransaction.getExternalReference(),
                    financialTransaction.getAmount().getAmount().toString(),
                    financialTransaction.getAmount().getCurrency().name());
        } catch (IOException | InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new APIException("Transaction refund failed: " + e.getMessage());
        }

        financialTransaction.setExternalReference(refund.getID());

        return financialTransaction;
    }

}
