package io.xlogistx.api.ai;

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
import java.util.Date;
import java.util.List;

public class AIAPI
        extends HTTPAPICaller {
    public static final LogWrapper log = new LogWrapper(AIAPI.class);

    protected AIAPI(String name, String description) {
        super(name, description);
    }



    public String transcribe(File file) throws IOException {
        return transcribe(new FileInputStream(file), file.getName());
    }

    /**
     * Transcribe a text
     * @param is input stream
     * @param name user defined name
     * @return text of the recording
     * @throws IOException in case of api error
     */
    public String transcribe(InputStream is, String name) throws IOException {
        NamedValue<InputStream> param = new NamedValue<InputStream>();
        param.setName(name);
        param.setValue(is);
        param.getProperties().build(new NVLong("length", is.available()));
        NVGenericMap response = syncCall(AIAPIBuilder.Command.TRANSCRIBE, param);
        IOUtil.close(is);
        return response.getValue("text");
    }

    public List<NVGenericMap> models() throws IOException {

        NVGenericMap result = syncCall(AIAPIBuilder.Command.MODELS, null);
        NVGenericMapList data = result.getNV("data");
        return data.getValue();
    }


    public NVGenericMap model(String model) throws IOException {
        return syncCall(AIAPIBuilder.Command.MODELS, model);
    }

    public String visionCompletion(String gptModel, String prompt, int maxTokens, InputStream is, String imageType) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, AIAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, maxTokens, is, imageType)));
    }

    public String visionCompletion(String gptModel, String prompt, int maxTokens, UByteArrayOutputStream baos, String imageType) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, AIAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, maxTokens, baos, imageType)));
    }

    public String completion(String gptModel, String prompt, int maxTokens) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, AIAPIBuilder.SINGLETON.toPromptParams(gptModel, prompt, maxTokens)));
    }


    private static String parseCompletionResponse(NVGenericMap response) {
        NVGenericMapList choices = (NVGenericMapList) response.get("choices");
        if (log.isEnabled()) log.getLogger().info("" + choices);
        NVGenericMap firstChoice = choices.getValue().get(0);
        if (log.isEnabled()) log.getLogger().info("" + firstChoice);
        NVGenericMap message = (NVGenericMap) firstChoice.get("message");
        Object content = message.getValue("content");
        return "" + content;
    }

    public static void main(String... args) {
        try {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String aiAPIKey = params.stringValue("ai-api-key");
            String aiAPIURL = params.stringValue("ai-api-url", true);


            AIAPIBuilder.Command command = params.enumValue("command", AIAPIBuilder.Command.values());
            AIAPI apiCaller = AIAPIBuilder.SINGLETON.createAPI("main-app", "Command line api", HTTPAPIBuilder.Prop.toProp(null, new HTTPAuthorization(HTTPAuthScheme.BEARER, aiAPIKey)));
            if(aiAPIURL != null)
            {
                apiCaller.updateURL(aiAPIURL);
            }
            NVGenericMap response = null;
            RateCounter rc = new RateCounter();
            rc.start();
            switch (command) {
                case COMPLETION:
                    String prompt = params.stringValue("prompt");
                    String gptModel = params.stringValue("model");
                    String imageUrl = params.stringValue("image-url", true);
                    NVGenericMap completion = null;
                    if (imageUrl != null) {
                        String imageType = ImageUtil.getImageFormat(imageUrl);
                        UByteArrayOutputStream imageBAOS = IOUtil.inputStreamToByteArray(new FileInputStream(imageUrl), true);
                        completion = AIAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, 0, imageBAOS, imageType);
                    } else
                        completion = AIAPIBuilder.SINGLETON.toPromptParams(gptModel, prompt, 0);

                    response = apiCaller.syncCall(command, completion);
                    System.out.println(command + "\n" + response);

                    break;
                case TRANSCRIBE:
                    File file = new File(params.stringValue("file"));
                    if (!file.exists())
                        throw new FileNotFoundException(file.getName());
                    System.out.println(command + "\n" + apiCaller.transcribe(file));
                    break;
                case MODELS:

                    List<NVGenericMap> models = apiCaller.models();
                    for (NVGenericMap model : models) {
                        int date = model.getValue("created");
                        System.out.println(model.getValue("id") + " created: " + new Date(((long) date * 1000)));
                    }
                    System.out.println("Models count: " + models.size());
                    break;
            }
            rc.stop(1);
            System.out.println("it took " + rc);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
