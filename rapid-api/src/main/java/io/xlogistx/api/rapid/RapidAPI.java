package io.xlogistx.api.rapid;

import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.GetNameValue;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.ParamUtil;

import java.io.IOException;

public class RapidAPI
        extends HTTPAPICaller {
    public static final LogWrapper log = new LogWrapper(RapidAPI.class);

    protected RapidAPI(String name, String description) {
        super(name != null ? name : "rapid-api", description != null ? description : "https://rapidapi.com/ caller");
    }

    public NVGenericMap checkEmail(String email) throws IOException {
        return syncCall(RapidAPIBuilder.Command.VALIDATE_EMAIL, GetNameValue.create("email", email));
    }

    public static void main(String... args) {
        try {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String apiKey = params.stringValue("api-key");
            String[] emails = ParamUtil.parseWithSep(",", params.stringValue("emails"));

            RapidAPI rapidAPI = RapidAPIBuilder.SINGLETON.createAPI("main-app", "Command line api", HTTPAPIBuilder.Prop.toProp(null, new HTTPAuthorization(RapidAPIBuilder.API_KEY_HEADER_NAME, apiKey, true)));

            for (String email : emails) {
                NVGenericMap nvmg = rapidAPI.checkEmail(email);
                System.out.println(GSONUtil.toJSONDefault(nvmg, true));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Usage RapidAPI command=[validate-email] api-key=rapid-api-key emails=acme@acme.com,mail@example.com");
        }
    }
}