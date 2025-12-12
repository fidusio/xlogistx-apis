package io.xlogistx.api.anthropic;

import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.NVGenericMapList;
import org.zoxweb.shared.util.ParamUtil;
import org.zoxweb.shared.util.RateCounter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AnthropicAPI
        extends HTTPAPICaller {
    public static final LogWrapper log = new LogWrapper(AnthropicAPI.class);

    protected AnthropicAPI(String name, String description) {
        super(name, description);
    }

    /**
     * Send a completion request to Claude
     * @param model the Claude model to use (e.g., "claude-sonnet-4-20250514")
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String completion(String model, String prompt, int maxTokens) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, maxTokens)));
    }

    /**
     * Send a completion request to Claude with a system prompt
     * @param model the Claude model to use
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response
     * @param systemPrompt the system prompt to set context/behavior
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String completion(String model, String prompt, int maxTokens, String systemPrompt) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, maxTokens, systemPrompt)));
    }

    /**
     * Send a vision completion request to Claude with an image from InputStream
     * @param model the Claude model to use
     * @param prompt the user prompt describing what to do with the image
     * @param maxTokens maximum tokens in the response
     * @param is the image input stream
     * @param imageType the image MIME type (e.g., "image/png", "image/jpeg")
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String visionCompletion(String model, String prompt, int maxTokens, InputStream is, String imageType) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toVisionParams(model, prompt, maxTokens, is, imageType)));
    }

    /**
     * Send a vision completion request to Claude with an image from byte array
     * @param model the Claude model to use
     * @param prompt the user prompt describing what to do with the image
     * @param maxTokens maximum tokens in the response
     * @param baos the image byte array output stream
     * @param imageType the image MIME type (e.g., "image/png", "image/jpeg")
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String visionCompletion(String model, String prompt, int maxTokens, UByteArrayOutputStream baos, String imageType) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toVisionParams(model, prompt, maxTokens, baos, imageType)));
    }

    /**
     * Send a vision completion request with a system prompt
     * @param model the Claude model to use
     * @param prompt the user prompt describing what to do with the image
     * @param maxTokens maximum tokens in the response
     * @param baos the image byte array output stream
     * @param imageType the image MIME type
     * @param systemPrompt the system prompt to set context/behavior
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String visionCompletion(String model, String prompt, int maxTokens, UByteArrayOutputStream baos, String imageType, String systemPrompt) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toVisionParams(model, prompt, maxTokens, baos, imageType, systemPrompt)));
    }

    /**
     * Get the raw response as NVGenericMap for advanced usage
     * @param model the Claude model to use
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response
     * @return the raw API response
     * @throws IOException in case of API error
     */
    public NVGenericMap rawCompletion(String model, String prompt, int maxTokens) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, maxTokens));
    }

    /**
     * Parse the Anthropic message response to extract text content
     * Anthropic response format:
     * {
     *   "content": [
     *     {
     *       "type": "text",
     *       "text": "The actual response text"
     *     }
     *   ],
     *   ...
     * }
     */
    private static String parseMessageResponse(NVGenericMap response) {
        NVGenericMapList content = (NVGenericMapList) response.get("content");
        if (log.isEnabled()) log.getLogger().info("" + content);

        if (content != null && !content.getValue().isEmpty()) {
            // Get the first content block (usually text)
            for (NVGenericMap block : content.getValue()) {
                String type = block.getValue("type");
                if ("text".equals(type)) {
                    return block.getValue("text");
                }
            }
        }
        return null;
    }

    public static void main(String... args) {
        try {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String apiKey = params.stringValue("api-key");
            String apiURL = params.stringValue("api-url", true);

            AnthropicAPIBuilder.Command command = params.enumValue("command", AnthropicAPIBuilder.Command.values());
            AnthropicAPI apiCaller = AnthropicAPIBuilder.SINGLETON.createAPI("main-app", "Command line api",
                    HTTPAPIBuilder.Prop.toProp(null, new HTTPAuthorization("x-api-key", apiKey, true)));

            if (apiURL != null) {
                apiCaller.updateURL(apiURL);
            }

            RateCounter rc = new RateCounter();
            rc.start();

            switch (command) {
                case MESSAGES:
                    String prompt = params.stringValue("prompt");
                    String model = params.stringValue("model", true);
                    if (model == null) {
                        model = AnthropicAPIBuilder.DEFAULT_MODEL;
                    }
                    String systemPrompt = params.stringValue("system", true);
                    String imageUrl = params.stringValue("image-url", true);

                    NVGenericMap messageParams;
                    if (imageUrl != null) {
                        String imageType = ImageUtil.getImageFormat(imageUrl);
                        UByteArrayOutputStream imageBAOS = IOUtil.inputStreamToByteArray(new FileInputStream(imageUrl), true);
                        messageParams = AnthropicAPIBuilder.SINGLETON.toVisionParams(model, prompt, 4096, imageBAOS, imageType, systemPrompt);
                    } else {
                        messageParams = AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, 4096, systemPrompt);
                    }

                    NVGenericMap response = apiCaller.syncCall(command, messageParams);
                    System.out.println(command + "\n" + response);
                    System.out.println("\nParsed response: " + parseMessageResponse(response));
                    break;
            }

            rc.stop(1);
            System.out.println("It took " + rc);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\nUsage: java AnthropicAPI api-key=YOUR_API_KEY command=MESSAGES prompt=\"Your prompt\" [model=claude-sonnet-4-20250514] [system=\"System prompt\"] [image-url=path/to/image]");
        }
    }
}
