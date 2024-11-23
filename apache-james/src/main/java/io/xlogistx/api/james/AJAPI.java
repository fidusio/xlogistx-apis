package io.xlogistx.api.james;

import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.*;
import org.zoxweb.shared.util.BiDataEncoder;
import org.zoxweb.shared.util.GetName;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.RateController;

/**
 * This class defines the ApacheJames api
 */

public class AJAPI
{

    public static final RateController AJ_RC = new RateController("ApacheJames", "4/s");

    public static final LogWrapper log = new LogWrapper(AJAPI.class).setEnabled(true);
    public enum Command
        implements GetName
    {
        ADD_USER("add-user"),
        GET_USERS("get-users"),
        DELETE_USER("delete-user"),
        UPDATE_USER("update-user"),
        ADD_DOMAIN("add-domain"),
        GET_DOMAINS("get-domains"),
        DELETE_DOMAIN("delete-domain"),
        ;

        private final String name;
        Command(String name)
        {
            this.name = name;
        }


        public String toString()
        {
            return getName();
        }

        /**
         * @return the name of the object
         */
        @Override
        public String getName() {
            return name;
        }
    }

    public static final AJAPI SINGLETON = new AJAPI();

    public static final String DOMAIN = "apache-james";

    public static final BiDataEncoder<HTTPMessageConfigInterface, NVGenericMap, HTTPMessageConfigInterface> USER_ENCODER = (hmci, nvgm) ->
    {
        hmci.getParameters().add(nvgm.get("email"));
        nvgm.remove("email");
        hmci.setContent(GSONUtil.toJSONDefault(nvgm));
        return hmci;
    };

    private AJAPI()
    {
        buildUsersEndpoints();
        buildDomainsEndpoints();
    }
    private void buildUsersEndpoints()
    {
        // get user
        HTTPMessageConfigInterface usersHMCI = HTTPMessageConfig.createAndInit(null, "users", HTTPMethod.GET, false);
        HTTPAPIEndPoint<Void, NVGenericMap[]> getAllUsers = HTTPAPIManager.SINGLETON.buildEndPoint(Command.GET_USERS, DOMAIN, "Get all users", usersHMCI);
        getAllUsers.setRateController(AJ_RC);
        getAllUsers.setDataDecoder(hrd-> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap[].class));
        if(log.isEnabled()) log.getLogger().info("Endpoint:" + getAllUsers.toCanonicalID());
        HTTPAPIManager.SINGLETON.register(getAllUsers);

        // add user
        HTTPMessageConfigInterface addUserHMCI = HTTPMessageConfig.createAndInit(null, "users/{email}", HTTPMethod.PUT, false);
        addUserHMCI.setContentType(HTTPMediaType.APPLICATION_JSON);

        HTTPAPIEndPoint<NVGenericMap, HTTPResponseData> addUser = HTTPAPIManager.SINGLETON.buildEndPoint(Command.ADD_USER, DOMAIN, "Add Domain", addUserHMCI);
        addUser.setRateController(AJ_RC);
        addUser.setDataEncoder((hmci, nvgm) ->
        {
            hmci.getParameters().add(nvgm.get("email"));
            nvgm.remove("email");
            hmci.setContent(GSONUtil.toJSONDefault(nvgm));
            return hmci;
        });
        HTTPAPIManager.SINGLETON.register(addUser);

        // delete user
        HTTPMessageConfigInterface deleteUserHMCI = HTTPMessageConfig.createAndInit(null, "users/{email}", HTTPMethod.DELETE, false);
        HTTPAPIEndPoint<String, HTTPResponseData> deleteUser = HTTPAPIManager.SINGLETON.buildEndPoint(Command.DELETE_USER, DOMAIN, "Delete User", deleteUserHMCI);
        deleteUser.setDataEncoder((httpMessageConfigInterface, s) -> {httpMessageConfigInterface.getParameters().build("email", s); return httpMessageConfigInterface;});
        deleteUser.setRateController(AJ_RC);
        HTTPAPIManager.SINGLETON.register(deleteUser);


        // update user
        HTTPMessageConfigInterface updateUserHMCI = HTTPMessageConfig.createAndInit(null, "users/{email}", HTTPMethod.PUT, false);
        addUserHMCI.setContentType(HTTPMediaType.APPLICATION_JSON);
        HTTPAPIEndPoint<NVGenericMap, HTTPResponseData> updateUser = HTTPAPIManager.SINGLETON.buildEndPoint(Command.UPDATE_USER, DOMAIN, "Update User", updateUserHMCI);
        updateUser.setDataEncoder((httpMessageConfigInterface, nvgm) ->
        {
            httpMessageConfigInterface.getParameters().add(nvgm.get("email"));
            nvgm.remove("email");
            httpMessageConfigInterface.setContent(GSONUtil.toJSONDefault(nvgm));
            return httpMessageConfigInterface;
        });
        updateUser.setRateController(AJ_RC);
        HTTPAPIManager.SINGLETON.register(updateUser);

    }

    private void buildDomainsEndpoints()
    {
        // get all domains
        HTTPMessageConfigInterface getDomainsHMCI = HTTPMessageConfig.createAndInit(null, "domains", HTTPMethod.GET, false);
        HTTPAPIEndPoint<Void, String[]> getAllDomains = HTTPAPIManager.SINGLETON.buildEndPoint(Command.GET_DOMAINS, DOMAIN, "Get all domains", getDomainsHMCI);
        getAllDomains.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), String[].class));
        getAllDomains.setRateController(AJ_RC);
        HTTPAPIManager.SINGLETON.register(getAllDomains);

        // add domain
        HTTPMessageConfigInterface addDomainHMCI = HTTPMessageConfig.createAndInit(null, "domains/{domain}", HTTPMethod.PUT, false);
        HTTPAPIEndPoint<String, HTTPResponseData> addDomain = HTTPAPIManager.SINGLETON.buildEndPoint(Command.ADD_DOMAIN, DOMAIN, "Add Domain", addDomainHMCI);
        addDomain.setDataEncoder((httpMessageConfigInterface, s) -> {httpMessageConfigInterface.getParameters().build("domain", s); return httpMessageConfigInterface;});
        addDomain.setRateController(AJ_RC);
        HTTPAPIManager.SINGLETON.register(addDomain);

        // delete domain
        HTTPMessageConfigInterface deleteDomainHMCI = HTTPMessageConfig.createAndInit(null, "domains/{domain}", HTTPMethod.DELETE, false);
        HTTPAPIEndPoint<String, HTTPResponseData> deleteDomain = HTTPAPIManager.SINGLETON.buildEndPoint(Command.DELETE_DOMAIN, DOMAIN, "Delete Domain", deleteDomainHMCI);
        deleteDomain.setDataEncoder((httpMessageConfigInterface, s) -> {httpMessageConfigInterface.getParameters().build("domain", s); return httpMessageConfigInterface;});
        deleteDomain.setRateController(AJ_RC);
        HTTPAPIManager.SINGLETON.register(deleteDomain);



    }


    public HTTPAPICaller create(String url, HTTPAuthorization authorization)
    {
        return HTTPAPIManager.SINGLETON.createAPICaller(DOMAIN, "default", authorization).updateURL(url);
    }
}
