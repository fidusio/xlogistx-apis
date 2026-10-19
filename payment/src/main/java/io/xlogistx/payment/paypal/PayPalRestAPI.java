package io.xlogistx.payment.paypal;

import org.zoxweb.server.http.HTTPCall;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.api.APIToken;
import org.zoxweb.shared.http.*;
import org.zoxweb.shared.util.Const;
import org.zoxweb.shared.util.NVEntity;
import org.zoxweb.shared.util.NVPair;
import org.zoxweb.shared.util.SUS;

import io.xlogistx.payment.paypal.daos.PPAmount;
import io.xlogistx.payment.paypal.daos.PPPayment;
import io.xlogistx.payment.paypal.daos.PPRefund;

import java.io.IOException;

public class PayPalRestAPI {

    private PayPalRestAPI() {

    }

    public static APIToken OAUTH2Token(String url, String clientID, String clientSecret)
            throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, NullPointerException, IllegalArgumentException {

        HTTPMessageConfigInterface hcc = HTTPMessageConfig.createAndInit(url, "/v1/oauth2/token", HTTPMethod.POST);
        hcc.getHeaders().add(new NVPair(HTTPHeader.ACCEPT, HTTPMediaType.APPLICATION_JSON));
        hcc.getHeaders().add(new NVPair(HTTPHeader.ACCEPT_LANGUAGE, "en_US"));

        hcc.setBasicAuthorization(clientID, clientSecret);
        hcc.getParameters().add(new NVPair("grant_type", "client_credentials"));
        HTTPCall hc = new HTTPCall(hcc, null);
        HTTPResponseData rd = hc.sendRequest();
        APIToken ret = GSONUtil.fromJSON(SUS.toString(rd.getData()), APIToken.class);

        ret.setCreationTime(System.currentTimeMillis());
        ret.setLastTimeUpdated(System.currentTimeMillis());
        ret.setLastTimeRead(System.currentTimeMillis());
        ret.setExpirationTimeUnit(Const.TimeInMillis.SECOND);

        return ret;
    }

    public static PPPayment payment(APIToken token, String url, PPPayment payment)
            throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, NullPointerException, IllegalArgumentException {
        HTTPMessageConfigInterface hcc = HTTPMessageConfig.createAndInit(url, "/v1/payments/payment", HTTPMethod.POST);
        hcc.setContentType(HTTPMediaType.APPLICATION_JSON);
        hcc.getHeaders().add(new NVPair(HTTPHeader.ACCEPT, HTTPMediaType.APPLICATION_JSON));
        hcc.getHeaders().add(new NVPair(HTTPHeader.ACCEPT_LANGUAGE, "en_US"));
        //hcc.getHeaderParameters().add(HTTPAuthorizationType.BEARER.toHTTPHeader(token.getTokenType(), token.getAccessToken()));
        hcc.setAuthorization(HTTPAuthorization.createBearer(token.getAccessToken()));
        String json = GSONUtil.toJSON(payment, true, false, false);
        hcc.setContent(SUS.getBytes(json));

        System.out.println(GSONUtil.toJSON((NVEntity) hcc, true, false, false));
        HTTPCall hc = new HTTPCall(hcc, null);
        HTTPResponseData rd = hc.sendRequest();

        PPPayment response = GSONUtil.fromJSON(SUS.toString(rd.getData()), PPPayment.class);
        return response;
    }

    public static PPRefund refund(APIToken token, String url, String refundID, String total, String currency)
            throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, NullPointerException, IllegalArgumentException {
        HTTPMessageConfigInterface hcc = HTTPMessageConfig.createAndInit(url, "/v1/payments/sale/" + refundID + "/refund", HTTPMethod.POST);
        hcc.setContentType(HTTPMediaType.APPLICATION_JSON);
        hcc.getHeaders().add(new NVPair(HTTPHeader.ACCEPT, HTTPMediaType.APPLICATION_JSON));
        hcc.getHeaders().add(new NVPair(HTTPHeader.ACCEPT_LANGUAGE, "en_US"));
        //hcc.getHeaderParameters().add(HTTPAuthorizationType.BEARER.toHTTPHeader(token.getTokenType(), token.getAccessToken()));
        hcc.setAuthorization(HTTPAuthorization.createBearer(token.getAccessToken()));
        PPAmount amount = new PPAmount();
        amount.setTotal(total);
        amount.setCurrency(currency);

        String json = GSONUtil.toJSONWrapper("amount", amount, true, false, false, null);
        hcc.setContent(SUS.getBytes(json));

        System.out.println(GSONUtil.toJSON((NVEntity) hcc, true, false, false));
        HTTPCall hc = new HTTPCall(hcc, null);
        HTTPResponseData rd = hc.sendRequest();

        return GSONUtil.fromJSON(SUS.toString(rd.getData()), PPRefund.class);
    }


}
