package io.xlogistx.api;

import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.*;
import org.zoxweb.shared.util.*;

public class XlogAPI
{
    public static final LogWrapper log = new LogWrapper(XlogAPI.class).setEnabled(true);
    public static final XlogAPI SINGLETON = new XlogAPI();
    //private static final RateController RC_TEST = new RateController("test-rc", "10/s");

    public enum Command
        implements GetNameValue<String>
    {
        TIMESTAMP("timestamp", "timestamp"),
        PING("ping", "ping/{detailed}"),
        ;
        private final String name;
        private final String value;
        Command(String name, String uri){this.name = name; this.value = uri;}
        public String getName(){return name;}
        public String getValue(){return value;}
    }

    public static final String DOMAIN = "xlog-api";

    private XlogAPI()
    {
        buildPingAPI();
        buildTimestampAPI();
    }

    private void buildPingAPI()
    {
        HTTPMessageConfigInterface pingHMCI = HTTPMessageConfig.createAndInit(null, Command.PING.getValue(), HTTPMethod.GET, false, (String) null);
        pingHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
//        if(log.isEnabled()) log.getLogger().info("isURLEncodingEnabled:" + pingHMCI.isURLEncodingEnabled());
        pingHMCI.setContentType((String) null);

        if(log.isEnabled()) log.getLogger().info("isURLEncodingEnabled:" + pingHMCI.isURLEncodingEnabled());

        HTTPAPIEndPoint<Boolean, NVGenericMap> pingAPI = HTTPAPIManager.SINGLETON.buildEndPoint(Command.PING, DOMAIN, "Ping the server", pingHMCI);
//        pingAPI.setRateController(RC_TEST);
        pingAPI.setDataDecoder(hrd-> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        pingAPI.setDataEncoder((hmci, detailed) -> {
            if(detailed)
                hmci.getParameters().build(new NVBoolean("detailed", detailed));
            return hmci;
        });
        if(log.isEnabled()) log.getLogger().info("Endpoint:" + pingAPI.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(pingAPI);
//        if(log.isEnabled()) log.getLogger().info("after isURLEncodingEnabled:" + pingAPI.getConfig().isURLEncodingEnabled());
    }

    private void buildTimestampAPI()
    {
        HTTPMessageConfigInterface timestampHMCI = HTTPMessageConfig.createAndInit(null, Command.TIMESTAMP.getValue(), HTTPMethod.GET, false, (String)null);
        timestampHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        HTTPAPIEndPoint<NamedValue<?>, NVGenericMap> timestampAPI = HTTPAPIManager.SINGLETON.buildEndPoint(Command.TIMESTAMP, DOMAIN, "Get timestamp from the server", timestampHMCI);
//        timestampAPI.setRateController(RC_TEST);
        timestampAPI.setDataDecoder(hrd-> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        if(log.isEnabled()) log.getLogger().info("Endpoint:" + timestampAPI.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(timestampAPI);
    }

    public HTTPAPICaller create(String url, HTTPAuthorization authorization)
    {
        return HTTPAPIManager.SINGLETON.createAPICaller(DOMAIN, "default", authorization).updateURL(url);
    }

}
