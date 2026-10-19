package io.xlogistx.payment.paypal.daos;

import org.zoxweb.shared.data.SetNameDescriptionImpl;
import org.zoxweb.shared.util.*;

@SuppressWarnings("serial")
public class PPPaymentOptions
        extends SetNameDescriptionImpl {

    public enum Params
            implements GetNVConfig {
        ALLOWED_PAYMENT_METHOD(NVConfigManager.createNVConfig("allowed_payment_method", "Optional payment method type. If specified, the transaction will go through for only instant payment. ", "AllowedPaymentMethod", false, true, String.class)),

        ;

        private final NVConfig cType;

        Params(NVConfig c) {
            cType = c;
        }

        public NVConfig getNVConfig() {
            return cType;
        }

    }

    public static final NVConfigEntity NVC_PAYPAL_PAYMENT_OPTIONS_DAO = new NVConfigEntityPortable(
            "pp_payment_options_dao",
            null,
            "PPPaymentOptions",
            true,
            false,
            false,
            false,
            PPPaymentOptions.class,
            SUS.extractNVConfigs(Params.values()),
            null,
            false,
            SetNameDescriptionImpl.NVC_NAME_DESCRIPTION_IMPL
    );

    public PPPaymentOptions() {
        super(NVC_PAYPAL_PAYMENT_OPTIONS_DAO);
    }

    public String getAllowedPaymentMethod() {
        return lookupValue(Params.ALLOWED_PAYMENT_METHOD);
    }

    public void setAllowedPaymentMethod(String method) {
        setValue(Params.ALLOWED_PAYMENT_METHOD, method);
    }

}