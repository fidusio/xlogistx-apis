package io.xlogistx.api.ai;

import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.io.SharedIOUtil;
import org.zoxweb.shared.util.*;

import java.io.*;
import java.util.ArrayList;
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
        NVGenericMap response = null;
        try {
            param.setName(name);
            param.setValue(is);
            param.getProperties().build(new NVLong("length", is.available()));
            response = syncCall(AIAPIBuilder.Command.TRANSCRIBE, param);
        } finally {
            SharedIOUtil.close(is);
        }

        return response.getValue("text");
    }


    public String[] availableModels()
            throws IOException {
        List<NVGenericMap> models = models();
        List<String> ret = new ArrayList<>();
        for (NVGenericMap model : models) {
            ret.add(model.getValue("id"));
        }

        return ret.toArray(new String[ret.size()]);
    }

    public List<NVGenericMap> models() throws IOException {

        NVGenericMap result = syncCall(AIAPIBuilder.Command.MODELS, null);
        NVGenericMapList data = result.getNV("data");
        return data.getValue();
    }


    public NVGenericMap model(String model) throws IOException {
        return syncCall(AIAPIBuilder.Command.MODELS, model);
    }

    /**
     * Chat completion with an image to analyze based on the prompt
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param is the image content input stream
     * @param imageType the image type ie: png, jpeg
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String visionCompletion(String aiModel, String prompt, int maxTokens, InputStream is, String imageType) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, is, imageType)));
    }

    /**
     * Chat completion with an image to analyze based on the prompt
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param baos the image content buffer
     * @param imageType the image type ie: png, jpeg
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String visionCompletion(String aiModel, String prompt, int maxTokens, UByteArrayOutputStream baos, String imageType) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, baos, imageType)));
    }

    /**
     * Chat completion based on a text prompt
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String completion(String aiModel, String prompt, int maxTokens) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, AIAPIBuilder.SINGLETON.toPromptParams(aiModel, prompt, maxTokens)));
    }

    /**
     * Chat completion with a skill .md file merged with the prompt via {@link #toSkillPrompt(String, String)}
     * @param aiModel the ai model to use
     * @param skillMD the skill .md file
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @return the completion response text
     * @throws IOException in case of api or file error
     */
    public String completion(String aiModel, File skillMD, String prompt, int maxTokens) throws IOException {
        return completion(aiModel, new FileInputStream(skillMD), prompt, maxTokens);
    }

    /**
     * Chat completion with a preloaded skill content merged with the prompt via {@link #toSkillPrompt(String, String)}
     * @param aiModel the ai model to use
     * @param skillContent the preloaded skill content, if null or empty the prompt is sent as is
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String completion(String aiModel, String skillContent, String prompt, int maxTokens) throws IOException {
        return completion(aiModel, toSkillPrompt(skillContent, prompt), maxTokens);
    }

    /**
     * Chat completion with a skill content read from an input stream and merged with the prompt via {@link #toSkillPrompt(String, String)}
     * @param aiModel the ai model to use
     * @param skillIS the skill content input stream, always closed
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @return the completion response text
     * @throws IOException in case of api or stream error
     */
    public String completion(String aiModel, InputStream skillIS, String prompt, int maxTokens) throws IOException {
        String skillContent;
        try {
            skillContent = IOUtil.inputStreamToString(skillIS, true);
        } finally {
            SharedIOUtil.close(skillIS);
        }
        return completion(aiModel, toSkillPrompt(skillContent, prompt), maxTokens);
    }

    /**
     * Merge a skill content with a prompt, the skill is wrapped in a &lt;skill&gt; tag block
     * followed by the prompt, AI service agnostic since the result is a plain prompt
     * @param skillContent the skill text usually the content of a skill .md file
     * @param prompt the user prompt
     * @return the merged prompt
     */
    public static String toSkillPrompt(String skillContent, String prompt) {
        if (SUS.isEmpty(skillContent))
            return prompt;
        return "<skill>\n" + skillContent + "\n</skill>\n\n" + prompt;
    }


    /**
     * Parse a chat/completions response and extract the first choice message content
     * @param response the decoded api response
     * @return the content text, the refusal text if the content is null, null if neither is set
     * @throws IOException if the response is an api error or has no choices
     */
    private static String parseCompletionResponse(NVGenericMap response) throws IOException {
        NVGenericMap error = response.getNV("error");
        if (error != null)
            throw new IOException("API error: " + error.getValue("message"));

        NVGenericMapList choices = (NVGenericMapList) response.get("choices");
        if (choices == null || choices.getValue().isEmpty())
            throw new IOException("API response has no choices: " + response);

        NVGenericMap firstChoice = choices.getValue().get(0);
        if (log.isEnabled()) log.getLogger().info("" + firstChoice);
        NVGenericMap message = (NVGenericMap) firstChoice.get("message");
        if (message == null)
            throw new IOException("API response choice has no message: " + firstChoice);

        String finishReason = firstChoice.getValue("finish_reason");
        if (finishReason != null && !"stop".equals(finishReason))
            log.getLogger().warning("finish_reason: " + finishReason);

        Object content = message.getValue("content");
        if (content == null)
            return message.getValue("refusal");
        return "" + content;
    }

    public static void main(String... args) {
        try {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String aiAPIKey = params.stringValue("ai-api-key");
            String aiAPIURL = params.stringValue("ai-api-url", true);
            AIAPIBuilder.AIAPIType aiType = params.enumValue("ai-type", AIAPIBuilder.AIAPIType.values());


            AIAPIBuilder.Command command = params.enumValue("command", AIAPIBuilder.Command.values());


            AIAPI apiCaller;
            if (aiType != null) {
                apiCaller = AIAPIBuilder.createAIAPI(aiType, null, aiAPIKey);
            } else {
                apiCaller = AIAPIBuilder.SINGLETON.createAPI("main-app", "Command line api", HTTPAPIBuilder.Prop.toProp(aiAPIURL, HTTPAuthorization.createBearer(aiAPIKey)));
            }
            NVGenericMap response = null;
            RateCounter rc = new RateCounter();
            rc.start();
            switch (command) {
                case COMPLETION:
                    String prompt = params.stringValue("prompt");
                    String skillMD = params.stringValue("skill-md", true);
                    if (skillMD != null)
                        prompt = toSkillPrompt(IOUtil.inputStreamToString(new FileInputStream(skillMD), true), prompt);
                    String gptModel = params.stringValue("ai-model");
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

                    String[] models = apiCaller.availableModels();
                    for (String model : models) {
//                        System.out.println(command + "\n" + model);
//                        int date = model.getValue("created");
                        System.out.println(model);
                    }
                    System.out.println("Models count: " + models.length);
                    break;
            }
            rc.stop(1);
            System.out.println("it took " + rc);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
