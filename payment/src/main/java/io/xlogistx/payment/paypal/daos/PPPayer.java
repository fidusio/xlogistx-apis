package io.xlogistx.payment.paypal.daos;

import org.zoxweb.shared.data.SetNameDescriptionImpl;
import org.zoxweb.shared.util.*;
import org.zoxweb.shared.util.NVConfigEntity.ArrayType;

import java.util.List;

@SuppressWarnings("serial")
public class PPPayer
        extends SetNameDescriptionImpl {
    public enum Params
            implements GetNVConfig {
        PAYMENT_METHOD(NVConfigManager.createNVConfig("payment_method", "Payment method used. Must be either credit_card or paypal.", "PaymentMethod", true, true, String.class)),
        FUNDING_INSTRUMENTS(NVConfigManager.createNVConfigEntity("funding_instruments", "A list of funding instruments for the current payment.", "FundingInstruments", true, true, PPFundingInstrument.NVC_PAYPAL_FUNDING_INSTRUMENT_DAO, ArrayType.LIST)),
        PAYER_INFO(NVConfigManager.createNVConfigEntity("payer_info", "Information related to the payer.", "PayerInfo", true, true, PPPayerInfo.NVC_PAYPAL_PAYER_INFO_DAO)),
        STATUS(NVConfigManager.createNVConfig("status", "Status of the payer's PayPal account. Only supported when the payment_method is set to paypal. Allowed values: VERIFIED or UNVERIFIED.", "Status", true, true, String.class));

        private final NVConfig cType;

        Params(NVConfig c) {
            cType = c;
        }

        public NVConfig getNVConfig() {
            return cType;
        }

    }

    public static final NVConfigEntity NVC_PAYPAL_PAYER_DAO = new NVConfigEntityPortable(
            "pp_payer_dao",
            null,
            "PPPayer",
            true,
            false,
            false,
            false,
            PPPayer.class,
            SUS.extractNVConfigs(Params.values()),
            null,
            false,
            SetNameDescriptionImpl.NVC_NAME_DESCRIPTION_IMPL
    );

    public PPPayer() {
        super(NVC_PAYPAL_PAYER_DAO);
    }

    public String getPaymentMethod() {
        return lookupValue(Params.PAYMENT_METHOD);
    }

    public void setPaymentMethod(String method) {
        setValue(Params.PAYMENT_METHOD, method);
    }

    @SuppressWarnings("unchecked")
    public ArrayValues<PPFundingInstrument> getFundingInstruments() {
        return (ArrayValues<PPFundingInstrument>) lookup(Params.FUNDING_INSTRUMENTS);
    }

    @SuppressWarnings("unchecked")
    public void setFundingInstruments(ArrayValues<PPFundingInstrument> values) {
        ArrayValues<PPFundingInstrument> content = (ArrayValues<PPFundingInstrument>) lookup(Params.FUNDING_INSTRUMENTS);
        content.add(values.values(), true);
    }

    @SuppressWarnings("unchecked")
    public void setFundingInstruments(List<PPFundingInstrument> values) {
        ArrayValues<PPFundingInstrument> content = (ArrayValues<PPFundingInstrument>) lookup(Params.FUNDING_INSTRUMENTS);
        content.add(values.toArray(new PPFundingInstrument[0]), true);
    }

    public PPPayerInfo getPayerInfo() {
        return lookupValue(Params.PAYER_INFO);
    }

    public void setPayerInfo(PPPayerInfo info) {
        setValue(Params.PAYER_INFO, info);
    }

    public String getStatus() {
        return lookupValue(Params.STATUS);
    }

    public void setStatus(String status) {
        setValue(Params.STATUS, status);
    }
}