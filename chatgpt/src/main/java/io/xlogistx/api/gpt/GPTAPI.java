package io.xlogistx.api.gpt;

import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.shared.http.HTTPAuthScheme;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.*;

import java.io.*;

public class GPTAPI
extends HTTPAPICaller
{
    public static final LogWrapper log = new LogWrapper(GPTAPI.class);

    protected GPTAPI(String name, String description) {
        super(name, description);
    }


    public String transcribe(File file) throws IOException
    {
        return transcribe(new FileInputStream(file), file.getName());

    }

    public String transcribe(InputStream is, String name) throws IOException
    {
        NamedValue<InputStream> param = new NamedValue<InputStream>();
        param.setName(name);
        param.setValue(is);
        param.getProperties().build(new NVLong("length", is.available()));
        NVGenericMap response = syncCall(GTPAPIBuilder.Command.TRANSCRIBE, param);
        return response.getValue("text");
    }

    public String visionCompletion(String gptModel, String prompt, int maxTokens, InputStream is, String imageType) throws IOException {
        return parseCompletionResponse(syncCall(GTPAPIBuilder.Command.COMPLETION, GTPAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, maxTokens, is, imageType)));
    }

    public String visionCompletion(String gptModel, String prompt, int maxTokens, UByteArrayOutputStream baos, String imageType) throws IOException
    {
        return parseCompletionResponse(syncCall(GTPAPIBuilder.Command.COMPLETION, GTPAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, maxTokens, baos, imageType)));
    }
    public String completion(String gptModel, String prompt, int maxTokens) throws IOException {
        return parseCompletionResponse(syncCall(GTPAPIBuilder.Command.COMPLETION, GTPAPIBuilder.SINGLETON.toPromptParams(gptModel, prompt, maxTokens)));
    }


    private static String parseCompletionResponse(NVGenericMap response)
    {
        NVGenericMapList choices = (NVGenericMapList) response.get("choices");
        if (log.isEnabled()) log.getLogger().info("" + choices);
        NVGenericMap firstChoice = choices.getValue().get(0);
        if (log.isEnabled()) log.getLogger().info("" + firstChoice);
        NVGenericMap message = (NVGenericMap) firstChoice.get("message");
        Object content = message.getValue("content");
        return ""+content;
    }

    public static void main(String ...args)
    {
        try
        {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String gptAPIKey = params.stringValue("gpt-key");

            GTPAPIBuilder.Command command = params.enumValue("command", GTPAPIBuilder.Command.values());
            GPTAPI apiCaller = GTPAPIBuilder.SINGLETON.createAPI("main-app","Command line api", HTTPAPIBuilder.Prop.toProp(null, new HTTPAuthorization(HTTPAuthScheme.BEARER, gptAPIKey)));
            NVGenericMap response = null;
            RateCounter rc = new RateCounter();
            rc.start();
            switch (command)
            {
                case COMPLETION:
                    String prompt = params.stringValue("prompt");
                    String gptModel = params.stringValue("model");
                    String imageUrl =  params.stringValue("image-url", true);
                    NVGenericMap completion =null;
                    if (imageUrl != null)
                    {
                        String imageType = ImageUtil.getImageFormat(imageUrl);
                        UByteArrayOutputStream imageBAOS = IOUtil.inputStreamToByteArray(new FileInputStream(imageUrl), true);
                        completion = GTPAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, 0, imageBAOS, imageType);
                    }
                    else
                        completion = GTPAPIBuilder.SINGLETON.toPromptParams(gptModel, prompt, 0);

                    response = apiCaller.syncCall(command, completion);
                    System.out.println(command + "\n" + response);

                    break;
                case TRANSCRIBE:
                    File file = new File(params.stringValue("file"));
                    if(!file.exists())
                        throw new FileNotFoundException(file.getName());
                    System.out.println(command + "\n" + apiCaller.transcribe(file));
                    break;
            }
            rc.stop();
            System.out.println("it took " + rc);


        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
