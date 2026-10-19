package io.xlogistx.payment.paypal.daos;

import org.zoxweb.shared.data.SetNameDescriptionImpl;
import org.zoxweb.shared.util.*;

@SuppressWarnings("serial")
public class PPAmount
        extends SetNameDescriptionImpl {

    public enum Params
            implements GetNVConfig {
        CURRENCY(NVConfigManager.createNVConfig("currency", "3-letter currency code. PayPal does not support all currencies.", "Currency", true, true, String.class)),
        TOTAL(NVConfigManager.createNVConfig("total", "Total amount charged from the payer to the payee. In case of a refund, this is the refunded amount to the original payer from the payee. 10 characters max with support for 2 decimal places.", "Total", true, true, String.class)),
        DETAILS(NVConfigManager.createNVConfigEntity("details", "Additional details related to a payment amount.", "Details", false, true, PPDetails.NVC_PAYPAL_DETAILS_DAO)),

        ;

        private final NVConfig cType;

        Params(NVConfig c) {
            cType = c;
        }

        public NVConfig getNVConfig() {
            return cType;
        }
    }

    public static final NVConfigEntity NVC_PAYPAL_AMOUNT_DAO = new NVConfigEntityPortable(
            "pp_amount_dao",
            null,
            "PPAmount",
            true,
            false,
            false,
            false,
            PPAmount.class,
            SUS.extractNVConfigs(Params.values()),
            null,
            false,
            SetNameDescriptionImpl.NVC_NAME_DESCRIPTION_IMPL
    );

    public PPAmount() {
        super(NVC_PAYPAL_AMOUNT_DAO);
    }

    public String getCurrency() {
        return lookupValue(Params.CURRENCY);
    }

    public void setCurrency(String currency) {
        setValue(Params.CURRENCY, currency);
    }

    public String getTotal() {
        return lookupValue(Params.TOTAL);
    }

    public void setTotal(String total) {
        setValue(Params.TOTAL, total);
    }

    public PPDetails getDetails() {
        return lookupValue(Params.DETAILS);
    }

    public void setDetails(PPDetails details) {
        setValue(Params.DETAILS, details);
    }

}