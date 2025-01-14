package io.xlogistx.api.james;

import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.NVStringList;
import org.zoxweb.shared.util.ParamUtil;
import org.zoxweb.shared.util.SUS;

import java.util.Arrays;

public class ApacheJames
{


    public static void main(String ...args)
    {
         try
         {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String url = params.stringValue("url");
            AJAPIBuilder.Command command = params.enumValue("command", AJAPIBuilder.Command.values());
            String domain = params.stringValue("domain", true);
            HTTPAPICaller apiCaller = AJAPIBuilder.SINGLETON.createAPI("AJ", null, HTTPAPIBuilder.Prop.toProp(url, null));
            String response = null;
            switch (command)
            {
                case UPDATE_USER:
                case ADD_USER:
                    NVGenericMap userData = new NVGenericMap()
                            .build("email", params.stringValue("email"))
                            .build("password", params.stringValue("password"));
                    byte[] userResult = apiCaller.syncCall(command, userData);
                    response = Arrays.toString(userResult) + " " + params.stringValue("email");
                    break;
                case GET_USERS:
                    NVGenericMap[] getUsersResult = apiCaller.syncCall(command, null);
                    if(domain != null)
                    {
                        NVStringList matching = new NVStringList();
                        for(NVGenericMap user : getUsersResult)
                        {
                            String email = user.getValue("username");
                            if(email.endsWith(domain))
                            {
                                matching.add(email);
                            }
                        }

                        response =  GSONUtil.toJSONDefault(matching, true);
                    }
                    else
                        response = GSONUtil.toJSONDefault(getUsersResult, true);
                    break;
                case DELETE_USER:
                    byte[] deleteUserResult = apiCaller.syncCall(command, params.stringValue("email"));
                    response =  Arrays.toString(deleteUserResult);
                    break;
                case ADD_DOMAIN:
                    byte[] addDomainResult = apiCaller.syncCall(command, params.stringValue("domain"));
                    response = Arrays.toString(addDomainResult);
                    break;
                case GET_DOMAINS:
                    String[] getDomainsResult = apiCaller.syncCall(command, null);
                    response = GSONUtil.toJSONDefault(getDomainsResult, true);
                    break;
                case DELETE_DOMAIN:
                    byte[] deleteDomainResult = apiCaller.syncCall(command, params.stringValue("domain"));
                    response = Arrays.toString(deleteDomainResult);
                    break;

            }


            System.out.println(command + "\n" + response);

         }
         catch (Exception e)
         {
             e.printStackTrace();
             System.err.println(SUS.errorMessage("\nUsage ApacheJames ...params", AJAPIBuilder.Command.values()));

         }
    }
}
