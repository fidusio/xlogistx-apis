package io.xlogistx.payment.paypal.daos;

import org.zoxweb.shared.data.SetNameDescriptionImpl;
import org.zoxweb.shared.util.*;

@SuppressWarnings("serial")
public class PPFundingInstrument
        extends SetNameDescriptionImpl {
    public enum Params
            implements GetNVConfig {
        CREDIT_CARD(NVConfigManager.createNVConfigEntity("credit_card", "Credit card details.", "CreditCard", true, true, PPCreditCard.NVC_PAYPAL_CREDIT_CARD_DAO)),
        CREDIT_CARD_TOKEN(NVConfigManager.createNVConfigEntity("credit_card_token", "Token for credit card details stored with PayPal. You can use this in place of a credit card.", "CreditCardToken", true, true, PPCreditCardToken.NVC_PAYPAL_CREDIT_CARD_TOKEN_DAO)),

        ;

        private final NVConfig cType;

        Params(NVConfig c) {
            cType = c;
        }

        public NVConfig getNVConfig() {
            return cType;
        }
    }

    public static final NVConfigEntity NVC_PAYPAL_FUNDING_INSTRUMENT_DAO = new NVConfigEntityPortable(
            "pp_funding_instrument_dao",
            null,
            "PPFundingInstrument",
            true,
            false,
            false,
            false,
            PPFundingInstrument.class,
            SUS.extractNVConfigs(Params.values()),
            null,
            false,
            SetNameDescriptionImpl.NVC_NAME_DESCRIPTION_IMPL
    );

    public PPFundingInstrument() {
        super(NVC_PAYPAL_FUNDING_INSTRUMENT_DAO);
    }

    public PPCreditCard getCreditCard() {
        return lookupValue(Params.CREDIT_CARD);
    }

    public void setCreditCard(PPCreditCard card) {
        setValue(Params.CREDIT_CARD, card);
    }

    public PPCreditCardToken getCreditCardToken() {
        return lookupValue(Params.CREDIT_CARD_TOKEN);
    }

    public void setCreditCardToken(PPCreditCardToken token) {
        setValue(Params.CREDIT_CARD_TOKEN, token);
    }

}