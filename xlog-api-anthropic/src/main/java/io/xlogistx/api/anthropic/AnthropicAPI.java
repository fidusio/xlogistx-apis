package io.xlogistx.api.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.MultipartField;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.*;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.beta.files.FileUploadParams;
import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Anthropic API proxy: exposes the xlogistx HTTPAPICaller front end (NVGenericMap in/out, Command routing)
 * while delegating the actual API calls to the official com.anthropic:anthropic-java SDK.
 * <p>
 * See AnthropicAPIBuilder for the supported commands and parameter keys.
 */
public class AnthropicAPI
        extends HTTPAPICaller {
    public static final LogWrapper log = new LogWrapper(AnthropicAPI.class);

    private volatile AnthropicClient sdkClient;
    private volatile String baseURL;

    protected AnthropicAPI(String name, String description) {
        super(name, description);
    }

    // ------------------------------------------------------------------
    // Messages convenience methods
    // ------------------------------------------------------------------

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
     * Send a multi-turn chat completion request to Claude
     * @param model the Claude model to use, null uses the builder's default
     * @param messages the conversation messages in Anthropic wire format, see AnthropicAPIBuilder.toMessage
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
     * @param systemPrompt optional system prompt
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String chatCompletion(String model, NVGenericMapList messages, int maxTokens, String systemPrompt) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toMessagesParams(model, maxTokens, systemPrompt, messages)));
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
     * Send a PDF document completion request to Claude
     * @param model the Claude model to use, null uses the builder's default
     * @param prompt the user prompt describing what to do with the document
     * @param maxTokens maximum tokens in the response, &lt;= 0 uses the builder's default
     * @param document the pdf document content
     * @param systemPrompt optional system prompt
     * @return the completion text response
     * @throws IOException in case of API error
     */
    public String documentCompletion(String model, String prompt, int maxTokens, UByteArrayOutputStream document, String systemPrompt) throws IOException {
        return parseMessageResponse(syncCall(AnthropicAPIBuilder.Command.MESSAGES,
                AnthropicAPIBuilder.SINGLETON.toDocumentParams(model, prompt, maxTokens, document, systemPrompt)));
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
     * Send a streaming message request, text deltas are pushed to the consumer as they arrive
     * @param params MESSAGES parameters, see AnthropicAPIBuilder
     * @param onText consumer invoked with each text delta, may be null
     * @return the full concatenated text response
     * @throws IOException in case of API error
     */
    public String streamCompletion(NVGenericMap params, Consumer<String> onText) throws IOException {
        rateLimit();
        try {
            StringBuilder fullText = new StringBuilder();
            try (StreamResponse<RawMessageStreamEvent> stream = sdkClient().messages().createStreaming(toMessageCreateParams(params))) {
                Iterator<RawMessageStreamEvent> events = stream.stream().iterator();
                while (events.hasNext()) {
                    events.next().contentBlockDelta().ifPresent(deltaEvent ->
                            deltaEvent.delta().text().ifPresent(textDelta -> {
                                fullText.append(textDelta.text());
                                if (onText != null)
                                    onText.accept(textDelta.text());
                            }));
                }
            }
            return fullText.toString();
        } catch (AnthropicException e) {
            throw new IOException("Anthropic API call failed: " + e.getMessage(), e);
        }
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
    // Models convenience methods
    // ------------------------------------------------------------------

    /**
     * @return the available models as {data: [...]}
     * @throws IOException in case of API error
     */
    public NVGenericMap listModels() throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.MODELS, new NVGenericMap());
    }

    /**
     * @param modelId the model id (e.g., "claude-opus-4-8")
     * @return the model info
     * @throws IOException in case of API error
     */
    public NVGenericMap modelInfo(String modelId) throws IOException {
        SUS.checkIfNulls("modelId null", modelId);
        return syncCall(AnthropicAPIBuilder.Command.MODELS, new NVGenericMap().build("model", modelId));
    }

    // ------------------------------------------------------------------
    // Batches convenience methods
    // ------------------------------------------------------------------

    /**
     * Create a message batch
     * @param requests list of {custom_id, params} where params is a wire-format message request
     * @return the created batch
     * @throws IOException in case of API error
     */
    public NVGenericMap createBatch(NVGenericMapList requests) throws IOException {
        SUS.checkIfNulls("requests null", requests);
        requests.setName("requests");
        return syncCall(AnthropicAPIBuilder.Command.BATCH_CREATE, new NVGenericMap().build(requests));
    }

    public NVGenericMap retrieveBatch(String batchId) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.BATCH_RETRIEVE, new NVGenericMap().build("batch-id", batchId));
    }

    public NVGenericMap listBatches() throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.BATCH_LIST, new NVGenericMap());
    }

    /**
     * Retrieve the results of an ended batch
     * @param batchId the batch id
     * @return {results: [...]} one entry per batch request
     * @throws IOException in case of API error
     */
    public NVGenericMap batchResults(String batchId) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.BATCH_RESULTS, new NVGenericMap().build("batch-id", batchId));
    }

    public NVGenericMap cancelBatch(String batchId) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.BATCH_CANCEL, new NVGenericMap().build("batch-id", batchId));
    }

    // ------------------------------------------------------------------
    // Files convenience methods
    // ------------------------------------------------------------------

    /**
     * Upload a file (beta Files API)
     * @param fileName the file name
     * @param content the file content
     * @return the file metadata including the file id
     * @throws IOException in case of API error
     */
    public NVGenericMap uploadFile(String fileName, UByteArrayOutputStream content) throws IOException {
        SUS.checkIfNulls("fileName or content null", fileName, content);
        return syncCall(AnthropicAPIBuilder.Command.FILE_UPLOAD, new NVGenericMap()
                .build("file-name", fileName)
                .build(new NamedValue<UByteArrayOutputStream>("file", content)));
    }

    public NVGenericMap listFiles() throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.FILE_LIST, new NVGenericMap());
    }

    public NVGenericMap fileMetadata(String fileId) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.FILE_METADATA, new NVGenericMap().build("file-id", fileId));
    }

    /**
     * Download a file's content (beta Files API)
     * @param fileId the file id
     * @return the file content
     * @throws IOException in case of API error
     */
    public UByteArrayOutputStream downloadFile(String fileId) throws IOException {
        NVGenericMap response = syncCall(AnthropicAPIBuilder.Command.FILE_DOWNLOAD, new NVGenericMap().build("file-id", fileId));
        Object content = response.getValue("content");
        if (content instanceof UByteArrayOutputStream)
            return (UByteArrayOutputStream) content;
        throw new IOException("Anthropic API unexpected file download response: " + response);
    }

    public NVGenericMap deleteFile(String fileId) throws IOException {
        return syncCall(AnthropicAPIBuilder.Command.FILE_DELETE, new NVGenericMap().build("file-id", fileId));
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
            NVGenericMap params = (NVGenericMap) param;
            rateLimit();
            try {
                switch (apiCommand) {
                    case MESSAGES:
                        return (O) toNVGenericMap(sdkClient().messages().create(toMessageCreateParams(params)));
                    case COUNT_TOKENS:
                        return (O) countTokensCall(params);
                    case MODELS:
                        return (O) modelsCall(params);
                    case BATCH_CREATE:
                        return (O) batchCreateCall(params);
                    case BATCH_RETRIEVE:
                        return (O) toNVGenericMap(sdkClient().messages().batches().retrieve(requiredValue(params, "batch-id")));
                    case BATCH_LIST:
                        return (O) toNVGenericMap(sdkClient().messages().batches().list().response());
                    case BATCH_RESULTS:
                        return (O) batchResultsCall(params);
                    case BATCH_CANCEL:
                        return (O) toNVGenericMap(sdkClient().messages().batches().cancel(requiredValue(params, "batch-id")));
                    case FILE_UPLOAD:
                        return (O) fileUploadCall(params);
                    case FILE_LIST:
                        return (O) toNVGenericMap(sdkClient().beta().files().list().response());
                    case FILE_METADATA:
                        return (O) toNVGenericMap(sdkClient().beta().files().retrieveMetadata(requiredValue(params, "file-id")));
                    case FILE_DOWNLOAD:
                        return (O) fileDownloadCall(params);
                    case FILE_DELETE:
                        return (O) toNVGenericMap(sdkClient().beta().files().delete(requiredValue(params, "file-id")));
                }
            } catch (AnthropicException e) {
                throw new IOException("Anthropic API call failed: " + e.getMessage(), e);
            }
        }
        // not an SDK-proxied command, fall back to the generic HTTP endpoint path
        return super.syncCall(command, param);
    }

    // ------------------------------------------------------------------
    // Request mapping
    // ------------------------------------------------------------------

    private MessageCreateParams toMessageCreateParams(NVGenericMap params) throws IOException {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(resolveModel(params))
                .maxTokens(resolveMaxTokens(params));

        // system prompt, optionally with a prompt-caching breakpoint
        String systemPrompt = params.getValue("system");
        if (SUS.isNotEmpty(systemPrompt)) {
            GetNameValue<?> cacheSystem = params.get("cache-system");
            if (cacheSystem != null && Boolean.TRUE.equals(cacheSystem.getValue()))
                builder.systemOfTextBlockParams(Collections.singletonList(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));
            else
                builder.system(systemPrompt);
        }

        // conversation: multi-turn "messages" (wire format) or single "prompt" with optional image/document
        Object messagesValue = params.get("messages");
        if (messagesValue instanceof NVGenericMapList) {
            for (NVGenericMap message : ((NVGenericMapList) messagesValue).getValue())
                builder.addMessage(toSDKModel(message, MessageParam.class));
        } else {
            String prompt = params.getValue("prompt");
            String imageBase64 = toBase64(params.getValue("image"));
            String documentBase64 = toBase64(params.getValue("document"));
            if (imageBase64 != null || documentBase64 != null) {
                List<ContentBlockParam> content = new ArrayList<>();
                if (documentBase64 != null)
                    content.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                            .source(Base64PdfSource.builder()
                                    .data(documentBase64)
                                    .build())
                            .build()));
                if (imageBase64 != null) {
                    String imageMediaType = params.getValue("image-type");
                    if (imageMediaType != null && !imageMediaType.contains("/"))
                        imageMediaType = "image/" + imageMediaType;
                    content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                            .source(Base64ImageSource.builder()
                                    .data(imageBase64)
                                    .mediaType(Base64ImageSource.MediaType.of(imageMediaType))
                                    .build())
                            .build()));
                }
                content.add(ContentBlockParam.ofText(TextBlockParam.builder()
                        .text(prompt)
                        .build()));
                builder.addUserMessageOfBlockParams(content);
            } else {
                builder.addUserMessage(prompt);
            }
        }

        // tool definitions, wire format {name, description, input_schema}
        Object toolsValue = params.get("tools");
        if (toolsValue instanceof NVGenericMapList) {
            for (NVGenericMap tool : ((NVGenericMapList) toolsValue).getValue())
                builder.addTool(toSDKModel(tool, Tool.class));
        }

        // tool choice: auto, any, none or a tool name to force
        String toolChoice = params.getValue("tool-choice");
        if (SUS.isNotEmpty(toolChoice)) {
            switch (toolChoice.toLowerCase()) {
                case "auto":
                    builder.toolChoice(ToolChoice.ofAuto(ToolChoiceAuto.builder().build()));
                    break;
                case "any":
                    builder.toolChoice(ToolChoice.ofAny(ToolChoiceAny.builder().build()));
                    break;
                case "none":
                    builder.toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()));
                    break;
                default:
                    builder.toolChoice(ToolChoice.ofTool(ToolChoiceTool.builder().name(toolChoice).build()));
                    break;
            }
        }

        // thinking: adaptive or disabled
        String thinking = params.getValue("thinking");
        if (SUS.isNotEmpty(thinking)) {
            if ("adaptive".equalsIgnoreCase(thinking))
                builder.thinking(ThinkingConfigAdaptive.builder().build());
            else if ("disabled".equalsIgnoreCase(thinking))
                builder.thinking(ThinkingConfigDisabled.builder().build());
            else
                throw new IOException("Unsupported thinking config: " + thinking + ", use adaptive or disabled");
        }

        // output config: effort and/or structured output json schema
        String effort = params.getValue("effort");
        Object schemaValue = params.get("json-schema");
        if (SUS.isNotEmpty(effort) || schemaValue instanceof NVGenericMap) {
            OutputConfig.Builder outputConfig = OutputConfig.builder();
            if (SUS.isNotEmpty(effort))
                outputConfig.effort(OutputConfig.Effort.of(effort.toLowerCase()));
            if (schemaValue instanceof NVGenericMap)
                outputConfig.format(JsonOutputFormat.builder()
                        .schema(toSDKModel((NVGenericMap) schemaValue, JsonOutputFormat.Schema.class))
                        .build());
            builder.outputConfig(outputConfig.build());
        }

        // stop sequences: NVStringList or comma separated string
        GetNameValue<?> stopSequences = params.get("stop-sequences");
        if (stopSequences instanceof NVStringList) {
            for (String stopSequence : ((NVStringList) stopSequences).getValue())
                builder.addStopSequence(stopSequence);
        } else if (stopSequences != null && stopSequences.getValue() instanceof String) {
            for (String stopSequence : SharedStringUtil.parseString((String) stopSequences.getValue(), ",", true))
                builder.addStopSequence(stopSequence);
        }

        // request metadata
        String userId = params.getValue("user-id");
        if (SUS.isNotEmpty(userId))
            builder.metadata(Metadata.builder().userId(userId).build());

        return builder.build();
    }

    private NVGenericMap countTokensCall(NVGenericMap params) throws IOException {
        MessageCountTokensParams.Builder builder = MessageCountTokensParams.builder()
                .model(resolveModel(params));

        Object messagesValue = params.get("messages");
        if (messagesValue instanceof NVGenericMapList) {
            for (NVGenericMap message : ((NVGenericMapList) messagesValue).getValue())
                builder.addMessage(toSDKModel(message, MessageParam.class));
        } else {
            builder.addMessage(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content((String) params.getValue("prompt"))
                    .build());
        }

        String systemPrompt = params.getValue("system");
        if (SUS.isNotEmpty(systemPrompt))
            builder.system(systemPrompt);

        return toNVGenericMap(sdkClient().messages().countTokens(builder.build()));
    }

    private NVGenericMap modelsCall(NVGenericMap params) throws IOException {
        String modelId = params.getValue("model");
        if (SUS.isNotEmpty(modelId))
            return toNVGenericMap(sdkClient().models().retrieve(modelId));
        return toNVGenericMap(sdkClient().models().list().response());
    }

    private NVGenericMap batchCreateCall(NVGenericMap params) throws IOException {
        Object requestsValue = params.get("requests");
        if (!(requestsValue instanceof NVGenericMapList))
            throw new IOException("Missing requests parameter for batch create");

        BatchCreateParams.Builder builder = BatchCreateParams.builder();
        for (NVGenericMap request : ((NVGenericMapList) requestsValue).getValue()) {
            String customId = request.getValue("custom_id");
            Object requestParams = request.get("params");
            if (SUS.isEmpty(customId) || !(requestParams instanceof NVGenericMap))
                throw new IOException("Batch request requires custom_id and params: " + request);
            builder.addRequest(BatchCreateParams.Request.builder()
                    .customId(customId)
                    .params(toSDKModel((NVGenericMap) requestParams, BatchCreateParams.Request.Params.class))
                    .build());
        }
        return toNVGenericMap(sdkClient().messages().batches().create(builder.build()));
    }

    private NVGenericMap batchResultsCall(NVGenericMap params) throws IOException {
        NVGenericMapList results = new NVGenericMapList("results");
        try (StreamResponse<MessageBatchIndividualResponse> stream =
                     sdkClient().messages().batches().resultsStreaming(requiredValue(params, "batch-id"))) {
            Iterator<MessageBatchIndividualResponse> responses = stream.stream().iterator();
            while (responses.hasNext())
                results.add(toNVGenericMap(responses.next()));
        }
        return new NVGenericMap().build(results);
    }

    private NVGenericMap fileUploadCall(NVGenericMap params) throws IOException {
        Object fileValue = params.getValue("file");
        if (fileValue instanceof InputStream)
            fileValue = IOUtil.inputStreamToByteArray((InputStream) fileValue, true);
        if (!(fileValue instanceof UByteArrayOutputStream))
            throw new IOException("Missing file parameter for file upload");
        UByteArrayOutputStream content = (UByteArrayOutputStream) fileValue;
        String fileName = params.getValue("file-name");
        if (SUS.isEmpty(fileName))
            throw new IOException("Missing file-name parameter for file upload");

        return toNVGenericMap(sdkClient().beta().files().upload(FileUploadParams.builder()
                .file(MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(content.getInternalBuffer(), 0, content.size()))
                        .filename(fileName)
                        .build())
                .build()));
    }

    private NVGenericMap fileDownloadCall(NVGenericMap params) throws IOException {
        String fileId = requiredValue(params, "file-id");
        try (HttpResponse response = sdkClient().beta().files().download(fileId)) {
            UByteArrayOutputStream content = IOUtil.inputStreamToByteArray(response.body(), true);
            return new NVGenericMap()
                    .build("file-id", fileId)
                    .build(new NamedValue<UByteArrayOutputStream>("content", content));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Anthropic API file download failed: " + e.getMessage(), e);
        }
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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

    private static String requiredValue(NVGenericMap params, String name) throws IOException {
        String ret = params.getValue(name);
        if (SUS.isEmpty(ret))
            throw new IOException("Missing " + name + " parameter");
        return ret;
    }

    private static String toBase64(Object value) throws IOException {
        if (value instanceof InputStream)
            // read the stream fully; available() is only an estimate and a single read() may return partial data
            value = IOUtil.inputStreamToByteArray((InputStream) value, true);

        if (value instanceof UByteArrayOutputStream) {
            UByteArrayOutputStream ubaos = (UByteArrayOutputStream) value;
            if (ubaos.size() > 0)
                return SharedBase64.encodeAsString(SharedBase64.Base64Type.DEFAULT, ubaos.getInternalBuffer(), 0, ubaos.size());
        }
        return null;
    }

    /**
     * Convert an Anthropic wire-format NVGenericMap to an SDK model via its Jackson mapper
     */
    private static <T> T toSDKModel(NVGenericMap wireFormat, Class<T> type) throws IOException {
        try {
            return ObjectMappers.jsonMapper().readValue(GSONUtil.toJSONDefault(wireFormat), type);
        } catch (Exception e) {
            throw new IOException("Invalid " + type.getSimpleName() + " parameter " + wireFormat + " : " + e.getMessage(), e);
        }
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
    public static String parseMessageResponse(NVGenericMap response) throws IOException {
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
                    HTTPAPIBuilder.Prop.toProp(null, HTTPAuthorization.createAuthorization("x-api-key", apiKey)));

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
                case MODELS: {
                    String model = params.stringValue("model", true);
                    System.out.println(command + "\n" + (model != null ? apiCaller.modelInfo(model) : apiCaller.listModels()));
                    break;
                }
                case BATCH_RETRIEVE: {
                    System.out.println(command + "\n" + apiCaller.retrieveBatch(params.stringValue("batch-id")));
                    break;
                }
                case BATCH_LIST: {
                    System.out.println(command + "\n" + apiCaller.listBatches());
                    break;
                }
                case BATCH_RESULTS: {
                    System.out.println(command + "\n" + apiCaller.batchResults(params.stringValue("batch-id")));
                    break;
                }
                case BATCH_CANCEL: {
                    System.out.println(command + "\n" + apiCaller.cancelBatch(params.stringValue("batch-id")));
                    break;
                }
                case FILE_LIST: {
                    System.out.println(command + "\n" + apiCaller.listFiles());
                    break;
                }
                case FILE_METADATA: {
                    System.out.println(command + "\n" + apiCaller.fileMetadata(params.stringValue("file-id")));
                    break;
                }
                case FILE_DELETE: {
                    System.out.println(command + "\n" + apiCaller.deleteFile(params.stringValue("file-id")));
                    break;
                }
                default:
                    System.out.println("Command not supported from the command line: " + command);
                    break;
            }

            rc.stop(1);
            System.out.println("It took " + rc);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\nUsage: java AnthropicAPI api-key=YOUR_API_KEY command=MESSAGES prompt=\"Your prompt\" [model=" + AnthropicAPIBuilder.DEFAULT_MODEL + "] [max-tokens=" + AnthropicAPIBuilder.DEFAULT_MAX_TOKENS + "] [system=\"System prompt\"] [image-url=path/to/image]");
        }
    }
}
