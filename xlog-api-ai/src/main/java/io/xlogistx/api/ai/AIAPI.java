package io.xlogistx.api.ai;

import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.http.HTTPCallback;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.io.SharedIOUtil;
import org.zoxweb.shared.task.ConsumerCallback;
import org.zoxweb.shared.util.*;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AIAPI
        extends HTTPAPICaller {
    public static final LogWrapper log = new LogWrapper(AIAPI.class);


    public static final  DataDecoder<NVGenericMap, String> AIMDDecoder = (input)-> {
        if (input == null)
            return null;

        // error payload: {"error": {"message": ..., "type": ...}}
        GetNameValue<?> error = input.get("error");
        if (error instanceof NVGenericMap) {
            String message = ((NVGenericMap) error).decodedValue("message", DataDecoder.AsStringOrNull);
            return "> **Error:** " + (message != null ? message
                    : "`` " + GSONUtil.toJSONDefault(error) + " ``");
        }

        // chat completions: choices[0].message.content, legacy completions: choices[0].text
        GetNameValue<?> choices = input.get("choices");
        if (choices instanceof NVGenericMapList) {
            List<NVGenericMap> list = ((NVGenericMapList) choices).getValue();
            if (!list.isEmpty()) {
                NVGenericMap first = list.get(0);
                GetNameValue<?> message = first.get("message");
                if (message instanceof NVGenericMap) {
                    String content = ((NVGenericMap) message).decodedValue("content", DataDecoder.AsStringOrNull);
                    return content != null ? content : (((NVGenericMap) message).decodedValue("refusal", DataDecoder.AsStringOrNull));
                }
                return first.decodedValue("text", DataDecoder.AsStringOrNull);
            }
        }

        // responses api: output[] typed items (message/reasoning/function_call/...);
        // assistant text is the message items' content[] output_text parts
        GetNameValue<?> output = input.get("output");
        if (output instanceof NVGenericMapList) {
            StringBuilder sb = new StringBuilder();
            for (NVGenericMap item : ((NVGenericMapList) output).getValue()) {
                if (!"message".equals(item.getValue("type")))
                    continue;
                GetNameValue<?> content = item.get("content");
                if (content instanceof NVGenericMapList) {
                    for (NVGenericMap part : ((NVGenericMapList) content).getValue()) {
                        String text = null;
                        if ("output_text".equals(part.decodedValue("type", DataDecoder.AsStringOrNull)))
                            text = part.decodedValue("text", DataDecoder.AsStringOrNull);
                        else if ("refusal".equals(part.decodedValue("type", DataDecoder.AsStringOrNull)))
                            text = part.decodedValue("refusal", DataDecoder.AsStringOrNull);

                        if (text != null) {
                            if (sb.length() > 0)
                                sb.append("\n\n");
                            sb.append(text);
                        }
                    }
                }
            }
            if (sb.length() > 0)
                return sb.toString();
        }

        // output_text is an SDK convenience helper, not part of the raw json:
        // honored last in case the caller pre-flattened the response
        String outputText = input.decodedValue("output_text", DataDecoder.AsStringOrNull);
        if (outputText != null)
            return outputText;

        // last resort: top level content/text
        String content = input.decodedValue("content", DataDecoder.AsStringOrNull);
        return content != null ? content : input.decodedValue("text", DataDecoder.AsStringOrNull);
    };

    protected AIAPI(String name, String description) {
        super(name, description);
    }


    public String transcribe(File file) throws IOException {
        return transcribe(Files.newInputStream(file.toPath()), file.getName());
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
            response = syncCall(AIAPIBuilder.Command.TRANSCRIBE, null, param);
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

        NVGenericMap result = syncCall(AIAPIBuilder.Command.MODELS, null, null);
        NVGenericMapList data = result.getNV("data");
        return data.getValue();
    }


    public NVGenericMap model(String model) throws IOException {
        return syncCall(AIAPIBuilder.Command.MODELS, null, model);
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
//    public String visionCompletion(String aiModel, String prompt, int maxTokens, InputStream is, String imageType) throws IOException {
//        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, imageType, is)));
//    }

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
//    public String visionCompletion(String aiModel, String prompt, int maxTokens, UByteArrayOutputStream baos, String imageType) throws IOException {
//        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, imageType, baos)));
//    }

    /**
     * Chat completion with an image to analyze based on the prompt
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param ubaos the image content buffer
     * @param imageType the image type ie: png, jpeg
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String visionCompletion(String aiModel, String prompt, int maxTokens, String imageType, UByteArrayOutputStream... ubaos) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, imageType, ubaos)));
    }


    /**
     * Chat completion with an image to analyze based on the prompt
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param images the image content buffer
     * @param imageType the image type ie: png, jpeg
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String visionCompletion(String aiModel, String prompt, int maxTokens, String imageType, InputStream... images) throws IOException {
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, imageType, images)));
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
        return parseCompletionResponse(syncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toPromptParams(aiModel, prompt, maxTokens)));
    }

    /**
     * Chat completion with a skill .md file merged with the prompt via {@link #toSkillPrompt(String, String)}
     *
     * @param aiModel   the ai model to use
     * @param prompt    the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param skillMD   the skill .md file
     * @return the completion response text
     * @throws IOException in case of api or file error
     */
    public String completion(String aiModel, String prompt, int maxTokens, File skillMD) throws IOException {
        return completion(aiModel, prompt, maxTokens, new FileInputStream(skillMD));
    }

    /**
     * Chat completion with a preloaded skill content merged with the prompt via {@link #toSkillPrompt(String, String)}
     *
     * @param aiModel      the ai model to use
     * @param prompt       the user prompt
     * @param maxTokens    max tokens to generate, 0 for the api default
     * @param skillContent the preloaded skill content, if null or empty the prompt is sent as is
     * @return the completion response text
     * @throws IOException in case of api error
     */
    public String completion(String aiModel, String prompt, int maxTokens, String skillContent) throws IOException {
        return completion(aiModel, toSkillPrompt(prompt, skillContent), maxTokens);
    }

    /**
     * Chat completion with a skill content read from an input stream and merged with the prompt via {@link #toSkillPrompt(String, String)}
     *
     * @param aiModel   the ai model to use
     * @param prompt    the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param skillIS   the skill content input stream, always closed
     * @return the completion response text
     * @throws IOException in case of api or stream error
     */
    public String completion(String aiModel, String prompt, int maxTokens, InputStream skillIS) throws IOException {
        String skillContent;
        try {
            skillContent = IOUtil.inputStreamToString(skillIS, true);
        } finally {
            SharedIOUtil.close(skillIS);
        }
        return completion(aiModel, toSkillPrompt(prompt, skillContent), maxTokens);
    }

    /**
     * Async chat completion based on a text prompt, the raw api response is delivered to the callback
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param callback accept is invoked with the raw api response, exception in case of call failure
     * @return the pending http callback
     */
    public HTTPCallback<NVGenericMap, NVGenericMap> asyncCompletion(String aiModel, String prompt, int maxTokens, ConsumerCallback<NVGenericMap> callback) {
        return asyncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toPromptParams(aiModel, prompt, maxTokens), callback);
    }

    /**
     * Async chat completion with a preloaded skill content merged with the prompt via {@link #toSkillPrompt(String, String)}
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param skillContent the preloaded skill content, if null or empty the prompt is sent as is
     * @param callback accept is invoked with the raw api response, exception in case of call failure
     * @return the pending http callback
     */
    public HTTPCallback<NVGenericMap, NVGenericMap> asyncCompletion(ConsumerCallback<NVGenericMap> callback, String aiModel, String prompt, int maxTokens, String skillContent) {
        return asyncCompletion(aiModel, toSkillPrompt(prompt, skillContent), maxTokens, callback);
    }

    /**
     * Async chat completion with an image to analyze based on the prompt, the raw api response is delivered to the callback
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param ubaos the image content buffer
     * @param imageType the image type ie: png, jpeg
     * @param callback accept is invoked with the raw api response, exception in case of call failure
     * @return the pending http callback
     */
    public HTTPCallback<NVGenericMap, NVGenericMap> asyncVisionCompletion(ConsumerCallback<NVGenericMap> callback,
                                                                          String aiModel, String prompt, int maxTokens, String imageType, UByteArrayOutputStream ...ubaos) {
        return asyncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, imageType, ubaos), callback);
    }

    /**
     * Async chat completion with an image to analyze based on the prompt, the raw api response is delivered to the callback
     * @param aiModel the ai model to use
     * @param prompt the user prompt
     * @param maxTokens max tokens to generate, 0 for the api default
     * @param images the image content input stream
     * @param imageType the image type ie: png, jpeg
     * @param callback accept is invoked with the raw api response, exception in case of call failure
     * @return the pending http callback
     */
    public HTTPCallback<NVGenericMap, NVGenericMap> asyncVisionCompletion(ConsumerCallback<NVGenericMap> callback, String aiModel, String prompt, int maxTokens, String imageType, InputStream ...images) {
        return asyncCall(AIAPIBuilder.Command.COMPLETION, null, AIAPIBuilder.SINGLETON.toVisionParams(aiModel, prompt, maxTokens, imageType, images), callback);
    }

    /**
     * Merge a skill content with a prompt, the skill is wrapped in a &lt;skill&gt; tag block
     * followed by the prompt, AI service agnostic since the result is a plain prompt
     *
     * @param prompt       the user prompt
     * @param skillContent the skill text usually the content of a skill .md file
     * @return the merged prompt
     */
    public static String toSkillPrompt(String prompt, String skillContent) {
        if (SUS.isEmpty(prompt)) {
            throw new NullPointerException("prompt must not be empty or null");
        }
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
                        prompt = toSkillPrompt(prompt, IOUtil.inputStreamToString(new FileInputStream(skillMD), true));
                    String gptModel = params.stringValue("ai-model");
                    String imageUrl = params.stringValue("image-url", true);
                    NVGenericMap completion = null;
                    if (imageUrl != null) {
                        String imageType = ImageUtil.getImageFormat(imageUrl);
                        UByteArrayOutputStream imageBAOS = IOUtil.inputStreamToByteArray(new FileInputStream(imageUrl), true);
                        completion = AIAPIBuilder.SINGLETON.toVisionParams(gptModel, prompt, 0, imageType, imageBAOS);
                    } else
                        completion = AIAPIBuilder.SINGLETON.toPromptParams(gptModel, prompt, 0);

                    response = apiCaller.syncCall(command, null, completion);
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
