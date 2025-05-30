package io.xlogistx.api;

import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPMediaType;
import org.zoxweb.shared.http.HTTPMessageConfig;
import org.zoxweb.shared.http.HTTPMessageConfigInterface;
import org.zoxweb.shared.http.HTTPMethod;
import org.zoxweb.shared.util.*;

public class XlogAPIBuilder {
    public static final LogWrapper log = new LogWrapper(XlogAPIBuilder.class).setEnabled(true);
    public static final XlogAPIBuilder SINGLETON = new XlogAPIBuilder();
    //private static final RateController RC_TEST = new RateController("test-rc", "10/s");

    public enum Command
            implements GetNameValue<String>, GetDescription {
        LOGIN("login", "subject/login/{appID}", "Login to the API"),
        PING("ping", "ping/{detailed}", "Ping the AP"),
        TIMESTAMP("timestamp", "timestamp", "Get the timestamp of the API"),
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

    public static final String DOMAIN = "xlog-api";

    private XlogAPIBuilder() {
        buildPingAPI();
        buildTimestampAPI();
        buildLoginAPI();
    }

    private void buildPingAPI() {
        HTTPMessageConfigInterface pingHMCI = HTTPMessageConfig.createAndInit(null, Command.PING.getValue(), HTTPMethod.GET, false, (String) null);
        pingHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
//        if(log.isEnabled()) log.getLogger().info("isURLEncodingEnabled:" + pingHMCI.isURLEncodingEnabled());
        pingHMCI.setContentType((String) null);

        if (log.isEnabled()) log.getLogger().info("isURLEncodingEnabled:" + pingHMCI.isContentURLEncoded());

        HTTPAPIEndPoint<Boolean, NVGenericMap> pingAPI = HTTPAPIManager.SINGLETON.buildEndPoint(Command.PING, DOMAIN, "Ping the server", pingHMCI);
//        pingAPI.setRateController(RC_TEST);
        pingAPI.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        pingAPI.setDataEncoder((hmci, detailed) -> {
            if (detailed)
                hmci.getParameters().build(new NVBoolean("detailed", detailed));
            return hmci;
        });
        if (log.isEnabled()) log.getLogger().info("Endpoint:" + pingAPI.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(pingAPI);
//        if(log.isEnabled()) log.getLogger().info("after isURLEncodingEnabled:" + pingAPI.getConfig().isURLEncodingEnabled());
    }

    private void buildTimestampAPI() {
        HTTPMessageConfigInterface timestampHMCI = HTTPMessageConfig.createAndInit(null, Command.TIMESTAMP.getValue(), HTTPMethod.GET, false, (String) null);
        timestampHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        HTTPAPIEndPoint<NamedValue<?>, NVGenericMap> timestampAPI = HTTPAPIManager.SINGLETON.buildEndPoint(Command.TIMESTAMP, DOMAIN, "Get timestamp from the server", timestampHMCI);
//        timestampAPI.setRateController(RC_TEST);
        timestampAPI.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        if (log.isEnabled()) log.getLogger().info("Endpoint:" + timestampAPI.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(timestampAPI);
    }

    private void buildLoginAPI() {
        HTTPMessageConfigInterface loginHMCI = HTTPMessageConfig.createAndInit(null, Command.LOGIN.getValue(), HTTPMethod.GET, false, (String) null);
        loginHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        HTTPAPIEndPoint<NamedValue<?>, NVGenericMap> loginAPI = HTTPAPIManager.SINGLETON.buildEndPoint(Command.LOGIN, DOMAIN, Command.LOGIN.getDescription(), loginHMCI);
//        timestampAPI.setRateController(RC_TEST);
        loginAPI.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        if (log.isEnabled()) log.getLogger().info("Endpoint:" + loginAPI.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(loginAPI);
    }


//    public HTTPAPICaller create(String url, HTTPAuthorization authorization)
//    {
//        return HTTPAPIManager.SINGLETON.createAPICaller(DOMAIN, "default", authorization).updateURL(url);
//    }


    public XlogClient createAPI(String name, String description, NVGenericMap props) {
        return HTTPAPIManager.SINGLETON.buildAPICaller(new XlogClient(name, description), DOMAIN, props);
    }

}
