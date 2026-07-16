package io.xlogistx.api.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.MessageTokensCount;
import com.anthropic.models.messages.TextBlockParam;
import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic API proxy: exposes the xlogistx HTTPAPICaller front end (NVGenericMap in/out, Command routing)
 * while delegating the actual API calls to the official com.anthropic:anthropic-java SDK.
 */
public class AnthropicAPI
        extends HTTPAPICaller {
    public static final LogWrapper log = new LogWrapper(AnthropicAPI.class);

    private volatile AnthropicClient sdkClient;
    private volatile String baseURL;

    protected AnthropicAPI(String name, String description) {
        super(name, description);
    }

    /**
     * Send a completion request to Claude using the builder's default model and max_tokens
     * @param prompt the user prompt
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String completion(String prompt) throws IOException {
        return completion(null, prompt, 0);
    }

    /**
     * Send a completion request to Claude using the builder's default model
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String completion(String prompt, int maxTokens) throws IOException {
        return completion(null, prompt, maxTokens);
    }

    /**
     * Send a completion request to Claude
     * @param model the Claude model to use (e.g., "claude-opus-4-8"), null uses the builder's default
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String completion(String model, String prompt, int maxTokens) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, maxTokens)));
    }

    /**
     * Send a completion request to Claude with a system prompt
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
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
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt describing what to do with the image
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
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
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt describing what to do with the image
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
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
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt describing what to do with the image
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
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
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
     * @return the raw API response
     * @throws IOException in case of API error
     */
    public NVGenericMap rawCompletion(String model, String prompt, int maxTokens) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, maxTokens));
    }

    /**
     * Count the tokens of a prompt for the given model without invoking the model
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt
     * @return the input token count
     * @throws IOException in case of API error
     */
    public int countTokens(String model, String prompt) throws IOException {
        NVGenericMap response = syncCall(AnthropicAPIBuilder.Command.COUNT_TOKENS,
                AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, 0));
        GetNameValue<?> inputTokens = response.get("input_tokens");
        if (inputTokens != null && inputTokens.getValue() instanceof Number)
            return ((Number) inputTokens.getValue()).intValue();
        throw new IOException("Anthropic API unexpected count_tokens response: " + response);
    }

    // ------------------------------------------------------------------
    // Proxy routing: NVGenericMap front end -> anthropic-java SDK back end
    // ------------------------------------------------------------------

    @Override
    public <I, O> O syncCall(GetName command, I param) throws IOException {
        return syncCall(command != null ? command.getName() : null, param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I, O> O syncCall(String command, I param) throws IOException {
        AnthropicAPIBuilder.Command apiCommand = SharedUtil.lookupEnum(command, AnthropicAPIBuilder.Command.values());
        if (apiCommand != null && param instanceof NVGenericMap) {
            rateLimit();
            try {
                switch (apiCommand) {
                    case MESSAGES:
                        return (O) createMessage((NVGenericMap) param);
                    case COUNT_TOKENS:
                        return (O) countTokens((NVGenericMap) param);
                }
            } catch (AnthropicException e) {
                throw new IOException("Anthropic API call failed: " + e.getMessage(), e);
            }
        }
        // not an SDK-proxied command, fall back to the generic HTTP endpoint path
        return super.syncCall(command, param);
    }

    private NVGenericMap createMessage(NVGenericMap params) throws IOException {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(resolveModel(params))
                .maxTokens(resolveMaxTokens(params));

        String systemPrompt = params.getValue("system");
        if (SUS.isNotEmpty(systemPrompt))
            builder.system(systemPrompt);

        String prompt = params.getValue("prompt");
        String imageBase64 = toImageBase64(params.getValue("image"));
        if (SUS.isNotEmpty(imageBase64)) {
            String imageMediaType = params.getValue("image-type");
            if (imageMediaType != null && !imageMediaType.contains("/"))
                imageMediaType = "image/" + imageMediaType;

            List<ContentBlockParam> content = new ArrayList<>();
            content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(imageBase64)
                            .mediaType(Base64ImageSource.MediaType.of(imageMediaType))
                            .build())
                    .build()));
            content.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(prompt)
                    .build()));
            builder.addUserMessageOfBlockParams(content);
        } else {
            builder.addUserMessage(prompt);
        }

        Message message = sdkClient().messages().create(builder.build());
        return toNVGenericMap(message);
    }

    private NVGenericMap countTokens(NVGenericMap params) throws IOException {
        MessageCountTokensParams.Builder builder = MessageCountTokensParams.builder()
                .model(resolveModel(params))
                .addMessage(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content((String) params.getValue("prompt"))
                        .build());

        String systemPrompt = params.getValue("system");
        if (SUS.isNotEmpty(systemPrompt))
            builder.system(systemPrompt);

        MessageTokensCount count = sdkClient().messages().countTokens(builder.build());
        return toNVGenericMap(count);
    }

    // ------------------------------------------------------------------
    // SDK client lifecycle
    // ------------------------------------------------------------------

    private AnthropicClient sdkClient() throws IOException {
        AnthropicClient ret = sdkClient;
        if (ret == null) {
            synchronized (this) {
                ret = sdkClient;
                if (ret == null) {
                    HTTPAuthorization authorization = getHTTPAuthorization();
                    if (authorization == null || SUS.isEmpty(authorization.getToken()))
                        throw new IOException("Missing Anthropic api key, set the HTTPAuthorization first");
                    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                            .apiKey(authorization.getToken());
                    if (SUS.isNotEmpty(baseURL))
                        builder.baseUrl(baseURL);
                    ret = builder.build();
                    sdkClient = ret;
                }
            }
        }
        return ret;
    }

    @Override
    public HTTPAPICaller setHTTPAuthorization(HTTPAuthorization authorization) {
        // reset the SDK client so the new credentials are picked up on the next call
        sdkClient = null;
        return super.setHTTPAuthorization(authorization);
    }

    @Override
    public synchronized <V extends HTTPAPICaller> V updateURL(String url) {
        // reset the SDK client so the new base url is picked up on the next call
        baseURL = url;
        sdkClient = null;
        return super.updateURL(url);
    }

    private static void rateLimit() throws IOException {
        long delay = AnthropicAPIBuilder.ANTHROPIC_RC.nextWait();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while rate limiting", e);
            }
        }
    }

    private static String resolveModel(NVGenericMap params) {
        String model = params.getValue("model");
        return SUS.isNotEmpty(model) ? model : AnthropicAPIBuilder.SINGLETON.getDefaultModel();
    }

    private static long resolveMaxTokens(NVGenericMap params) {
        GetNameValue<?> maxTokens = params.get("max-tokens");
        if (maxTokens instanceof NVInt && ((NVInt) maxTokens).getValue() > 0)
            return ((NVInt) maxTokens).getValue();
        return AnthropicAPIBuilder.SINGLETON.getDefaultMaxTokens();
    }

    private static String toImageBase64(Object imageValue) throws IOException {
        if (imageValue instanceof InputStream)
            // read the stream fully; available() is only an estimate and a single read() may return partial data
            imageValue = IOUtil.inputStreamToByteArray((InputStream) imageValue, true);

        if (imageValue instanceof UByteArrayOutputStream) {
            UByteArrayOutputStream ubaos = (UByteArrayOutputStream) imageValue;
            if (ubaos.size() > 0)
                return SharedBase64.encodeAsString(SharedBase64.Base64Type.DEFAULT, ubaos.getInternalBuffer(), 0, ubaos.size());
        }
        return null;
    }

    /**
     * Convert an SDK response object to NVGenericMap preserving the API wire format
     */
    private static NVGenericMap toNVGenericMap(Object sdkResponse) throws IOException {
        try {
            String json = ObjectMappers.jsonMapper().writeValueAsString(sdkResponse);
            return GSONUtil.fromJSONDefault(json, NVGenericMap.class);
        } catch (Exception e) {
            throw new IOException("Failed to convert Anthropic SDK response: " + e.getMessage(), e);
        }
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
     *   "stop_reason": "end_turn",
     *   ...
     * }
     * Error format:
     * {
     *   "type": "error",
     *   "error": {
     *     "type": "invalid_request_error",
     *     "message": "The error description"
     *   }
     * }
     * @throws IOException if the response is an API error, a refusal, or has no parsable content
     */
    private static String parseMessageResponse(NVGenericMap response) throws IOException {
        if (response == null)
            throw new IOException("Anthropic API returned no response");

        // API error envelope
        if ("error".equals(response.getValue("type"))) {
            Object error = response.get("error");
            if (error instanceof NVGenericMap) {
                NVGenericMap errorMap = (NVGenericMap) error;
                throw new IOException("Anthropic API error " + errorMap.getValue("type") + ": " + errorMap.getValue("message"));
            }
            throw new IOException("Anthropic API error: " + response);
        }

        String stopReason = response.getValue("stop_reason");
        if ("refusal".equals(stopReason))
            throw new IOException("Anthropic API refused the request, stop_reason: refusal");

        Object contentValue = response.get("content");
        if (!(contentValue instanceof NVGenericMapList))
            throw new IOException("Anthropic API response has no content: " + response);

        NVGenericMapList content = (NVGenericMapList) contentValue;
        if (log.isEnabled()) log.getLogger().info("" + content);

        if ("max_tokens".equals(stopReason) && log.isEnabled())
            log.getLogger().info("Anthropic API response truncated, stop_reason: max_tokens, increase maxTokens");

        // Get the first text content block, skipping non-text blocks such as thinking
        for (NVGenericMap block : content.getValue()) {
            String type = block.getValue("type");
            if ("text".equals(type)) {
                return block.getValue("text");
            }
        }

        throw new IOException("Anthropic API response has no text content, stop_reason: " + stopReason);
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
                case MESSAGES: {
                    String prompt = params.stringValue("prompt");
                    String model = params.stringValue("model", true);
                    if (model == null) {
                        model = AnthropicAPIBuilder.SINGLETON.getDefaultModel();
                    }
                    int maxTokens = params.intValue("max-tokens", AnthropicAPIBuilder.SINGLETON.getDefaultMaxTokens());
                    String systemPrompt = params.stringValue("system", true);
                    String imageUrl = params.stringValue("image-url", true);

                    NVGenericMap messageParams;
                    if (imageUrl != null) {
                        String imageType = ImageUtil.getImageFormat(imageUrl);
                        UByteArrayOutputStream imageBAOS = IOUtil.inputStreamToByteArray(new FileInputStream(imageUrl), true);
                        messageParams = AnthropicAPIBuilder.SINGLETON.toVisionParams(model, prompt, maxTokens, imageBAOS, imageType, systemPrompt);
                    } else {
                        messageParams = AnthropicAPIBuilder.SINGLETON.toPromptParams(model, prompt, maxTokens, systemPrompt);
                    }

                    NVGenericMap response = apiCaller.syncCall(command, messageParams);
                    System.out.println(command + "\n" + response);
                    System.out.println("\nParsed response: " + parseMessageResponse(response));
                    break;
                }
                case COUNT_TOKENS: {
                    String prompt = params.stringValue("prompt");
                    String model = params.stringValue("model", true);
                    System.out.println(command + ": " + apiCaller.countTokens(model, prompt) + " input tokens");
                    break;
                }
            }

            rc.stop(1);
            System.out.println("It took " + rc);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\nUsage: java AnthropicAPI api-key=YOUR_API_KEY command=MESSAGES prompt=\"Your prompt\" [model=" + AnthropicAPIBuilder.DEFAULT_MODEL + "] [max-tokens=" + AnthropicAPIBuilder.DEFAULT_MAX_TOKENS + "] [system=\"System prompt\"] [image-url=path/to/image]");
        }
    }
}
