package io.xlogistx.api;

import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.http.OkHTTPCall;
import org.zoxweb.server.task.TaskUtil;
import org.zoxweb.shared.http.HTTPAuthorizationBasic;
import org.zoxweb.shared.task.ConsumerCallback;
import org.zoxweb.shared.util.Const;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.ParamUtil;
import org.zoxweb.shared.util.RateCounter;

import java.util.concurrent.atomic.AtomicLong;

public class XlogClient {
    public static void main(String ...args)
    {
        try
        {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String url = params.stringValue("url");
            XlogAPI.Command command = params.enumValue("command", XlogAPI.Command.values());
            HTTPAPICaller apiCaller = XlogAPI.SINGLETON.create(url, null);
            String user = params.stringValue("user", true);
            String password = params.stringValue("password", null);
            boolean print = params.booleanValue("print", true);
            apiCaller.updateExecutor(TaskUtil.defaultTaskProcessor());
            apiCaller.updateScheduler( null);
            int repeat = params.intValue("repeat", 1);

            AtomicLong success = new AtomicLong();
            AtomicLong fail = new AtomicLong();
            ConsumerCallback<NVGenericMap> callback = new ConsumerCallback<NVGenericMap>() {


                public void exception(Exception e)
                {
                    fail.incrementAndGet();
                    if(print)
                        e.printStackTrace();
                }
                @Override
                public void accept(NVGenericMap nvGenericMap) {
                    success.incrementAndGet();

                    if(print)
                        System.out.println(nvGenericMap);

                }
            };

            if(password != null && user != null)
            {
                apiCaller.setHTTPAuthorization(new HTTPAuthorizationBasic(user, password));
            }
            long ts = System.currentTimeMillis();
            
            for (int i = 0; i < repeat; i++)
            {
                switch (command)
                {
                    case TIMESTAMP:
                        apiCaller.asyncCall(command, null, callback);
                        break;
                    case PING:
                        boolean detailed = params.booleanValue("detailed", true);
                        apiCaller.asyncCall(command, detailed, callback);
                        break;
                }
            }

            ts = TaskUtil.waitIfBusyThenClose(200) - ts;
            RateCounter rc = new RateCounter("OverAll");
            rc.register(ts, OkHTTPCall.OK_HTTP_CALLS.getCounts());

            System.out.println("OkHTTPCall stat: " + Const.TimeInMillis.toString(ts) + " to send: " + OkHTTPCall.OK_HTTP_CALLS.getCounts() + " failed: " + fail+
                    " rate: " +  OkHTTPCall.OK_HTTP_CALLS.rate(Const.TimeInMillis.SECOND.MILLIS) + " per/second" + " average call duration: " + OkHTTPCall.OK_HTTP_CALLS.average() + " millis");


            System.out.println("App  stats: " + repeat + " it took " + Const.TimeInMillis.toString(ts)  +  " rate: " + rc.rate(Const.TimeInMillis.SECOND.MILLIS) + " per/second" + " average call duration: " + rc.average() + " millis");

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}