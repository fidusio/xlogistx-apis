package io.xlogistx.api.rapid;

import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPMediaType;
import org.zoxweb.shared.http.HTTPMessageConfig;
import org.zoxweb.shared.http.HTTPMessageConfigInterface;
import org.zoxweb.shared.http.HTTPMethod;
import org.zoxweb.shared.util.GetDescription;
import org.zoxweb.shared.util.GetNameValue;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.RateController;

public class RapidAPIBuilder
implements HTTPAPIBuilder {
    private static final LogWrapper log = new LogWrapper(RapidAPIBuilder.class).setEnabled(false);

    public static final RapidAPIBuilder SINGLETON = new RapidAPIBuilder();

    public static final String DOMAIN = "rapid-api";
    public static final String API_KEY_HEADER_NAME = "x-rapidapi-key";


    public enum Command
            implements GetNameValue<String>, GetDescription {
        VALIDATE_EMAIL("validate-email", "https://email-checker.p.rapidapi.com/verify/v1", "validate email"),
        ;
        private final String name;
        private final String value;
        private final String description;

        Command(String name, String uri, String description) {
            this.name = name;
            this.value = uri;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }





    private RapidAPIBuilder()
    {
        buildCheckEmailAPI();
    }


    private void buildCheckEmailAPI() {
        HTTPMessageConfigInterface validateEmailHMCI = HTTPMessageConfig.createAndInit(Command.VALIDATE_EMAIL.getValue(), null, HTTPMethod.GET, true, HTTPMediaType.APPLICATION_WWW_URL_ENC);
        validateEmailHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        validateEmailHMCI.getHeaders().build("x-rapidapi-host", "email-checker.p.rapidapi.com");
        HTTPAPIEndPoint<GetNameValue<String>, NVGenericMap> validateEmailAPI = HTTPAPIManager.SINGLETON.buildEndPoint(Command.VALIDATE_EMAIL, DOMAIN, "Validate email address", validateEmailHMCI);
        validateEmailAPI.setDataEncoder((hmci, email)->{
            hmci.getParameters().build(email);
            if(log.isEnabled()) log.getLogger().info(GSONUtil.toJSONDefault(hmci, true));
          return hmci;
        });
        validateEmailAPI.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        validateEmailAPI.setRateController(new RateController("email-checker-rc", "5/min"));
        if (log.isEnabled()) log.getLogger().info("Endpoint:" + validateEmailAPI.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(validateEmailAPI);
    }


    public RapidAPI createAPI(String name, String description, NVGenericMap props) {
        return HTTPAPIManager.SINGLETON.buildAPICaller(new RapidAPI(name, description), DOMAIN, props);
    }
}
