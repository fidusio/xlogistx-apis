package io.xlogistx.api;

import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.http.OkHTTPCall;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.task.TaskUtil;
import org.zoxweb.server.util.DateUtil;
import org.zoxweb.shared.http.HTTPAuthorizationBasic;
import org.zoxweb.shared.task.ConsumerCallback;
import org.zoxweb.shared.util.Const;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.ParamUtil;
import org.zoxweb.shared.util.RateCounter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class XlogClient
        extends HTTPAPICaller
{

    public static final LogWrapper log = new LogWrapper(XlogClient.class).setEnabled(true);
    protected XlogClient(String name, String description)
    {
        super(name, description);
    }

    public long timestamp()
            throws IOException
    {
        NVGenericMap nvgm = syncCall(XlogAPIBuilder.Command.TIMESTAMP, null);
        return nvgm.getValue("current_time");
    }


    public static void main(String ...args)
    {
        try
        {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            params.hide("password");
            System.out.println(params);
            String url = params.stringValue("url");


            XlogAPIBuilder.Command command = params.enumValue("command", XlogAPIBuilder.Command.values());


            //HTTPAPICaller apiCaller = XlogAPIBuilder.SINGLETON.create(url, null);
            XlogClient apiCaller = XlogAPIBuilder.SINGLETON.createAPI("XlogistX", "Xlogistx client API", null);
            String user = params.stringValue("user", true);
            String password = params.stringValue("password", null);
            boolean print = params.booleanValue("print", true);
            apiCaller.updateExecutor(TaskUtil.defaultTaskProcessor());
            //apiCaller.updateScheduler( null);
            apiCaller.updateURL(url);
            int repeat = params.intValue("repeat", 1);
            boolean detailed = params.booleanValue("detailed", true);

            System.out.println("url: " + url + " command: " + command + " repeat: " +repeat); ;
            AtomicLong success = new AtomicLong();
            AtomicLong fail = new AtomicLong();
            ConsumerCallback<NVGenericMap> callback = new ConsumerCallback<NVGenericMap>() {


                public void exception(Exception e)
                {
                    fail.incrementAndGet();
                    if(print) {
                       e.printStackTrace();
                    }
                }
                @Override
                public void accept(NVGenericMap nvGenericMap) {
                    success.incrementAndGet();

                    if(print)
                        System.out.println(success + " " + nvGenericMap);

                }
            };

            if(password != null && user != null)
            {
                apiCaller.setHTTPAuthorization(new HTTPAuthorizationBasic(user, password));
            }
            //apiCaller.updateRateController(new RateController("test", "10/s");

            System.out.println("Simple api timestamp: " + DateUtil.DEFAULT_ZULU_MILLIS.format(apiCaller.timestamp()));

            Runnable toRun = null;
            switch (command)
            {
                case TIMESTAMP:
                    toRun = ()-> apiCaller.asyncCall(command, null, callback);

                    break;
                case PING:
                    toRun = ()-> apiCaller.asyncCall(command, detailed, callback);
                    break;
            }

            toRun.run();


            long ts = System.currentTimeMillis();
            for (int i = 0; i < repeat; i++)
            {
               toRun.run();
            }


//            switch (command)
//            {
//                case TIMESTAMP:
//                    System.out.println("Simple api timestamp: " + DateUtil.DEFAULT_ZULU_MILLIS.format(apiCaller.timestamp()));
//                    for (int i = 0 ; i < repeat; i++)
//                        apiCaller.asyncCall(command, null, callback);
//
//                    break;
//                case PING:
//                    for (int i = 0 ; i < repeat; i++)
//                        apiCaller.asyncCall(command, detailed, callback);
//                    break;
//            }

            ts = TaskUtil.waitIfBusyThenClose(200) - ts;
            RateCounter rc = new RateCounter("OverAll");
            rc.register(ts, repeat);

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