package io.xlogistx.api.anthropic;

import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPMediaType;
import org.zoxweb.shared.http.HTTPMessageConfig;
import org.zoxweb.shared.http.HTTPMessageConfigInterface;
import org.zoxweb.shared.http.HTTPMethod;
import org.zoxweb.shared.util.*;

import java.io.InputStream;

/**
 * Anthropic API builder: front end command and parameter factory for AnthropicAPI.
 * <p>
 * MESSAGES / COUNT_TOKENS parameter keys (NVGenericMap):
 * <ul>
 *   <li>"model" String, null/empty uses the default model</li>
 *   <li>"max-tokens" NVInt, &lt;= 0 uses the default max tokens</li>
 *   <li>"system" String system prompt, optional</li>
 *   <li>"cache-system" NVBoolean, true adds a prompt-caching breakpoint on the system prompt</li>
 *   <li>"prompt" String single user prompt (ignored when "messages" is set)</li>
 *   <li>"messages" NVGenericMapList multi-turn conversation in Anthropic wire format:
 *       each entry {role, content} where content is a String or a list of content blocks
 *       (text, image, tool_use, tool_result, ...)</li>
 *   <li>"image" UByteArrayOutputStream or InputStream + "image-type" media type, single-turn vision</li>
 *   <li>"document" UByteArrayOutputStream or InputStream, single-turn PDF document</li>
 *   <li>"tools" NVGenericMapList of tool definitions in Anthropic wire format
 *       {name, description, input_schema}</li>
 *   <li>"tool-choice" String: "auto", "any", "none" or a tool name to force</li>
 *   <li>"thinking" String: "adaptive" or "disabled"</li>
 *   <li>"effort" String: "low", "medium", "high", "xhigh" or "max"</li>
 *   <li>"json-schema" NVGenericMap JSON schema for structured output</li>
 *   <li>"stop-sequences" NVStringList or comma separated String</li>
 *   <li>"user-id" String request metadata user id</li>
 * </ul>
 * Other command parameter keys:
 * <ul>
 *   <li>MODELS: optional "model" String, set retrieves one model, unset lists all</li>
 *   <li>BATCH_CREATE: "requests" NVGenericMapList of {custom_id, params} where params is a
 *       MESSAGES wire-format request {model, max_tokens, messages, ...}</li>
 *   <li>BATCH_RETRIEVE, BATCH_RESULTS, BATCH_CANCEL: "batch-id" String</li>
 *   <li>FILE_UPLOAD: "file" UByteArrayOutputStream or InputStream, "file-name" String</li>
 *   <li>FILE_METADATA, FILE_DOWNLOAD, FILE_DELETE: "file-id" String</li>
 * </ul>
 */
public class AnthropicAPIBuilder
        implements HTTPAPIBuilder {

    public static final LogWrapper log = new LogWrapper(AnthropicAPIBuilder.class).setEnabled(true);
    public static final AnthropicAPIBuilder SINGLETON = new AnthropicAPIBuilder();
    public static final RateController ANTHROPIC_RC = new RateController("ANTHROPIC-RC", "60/m");
    public static final String DOMAIN = "anthropic-api";
    public static final String ANTHROPIC_URL = "https://api.anthropic.com";
    public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
    public static final String DEFAULT_MODEL = "claude-opus-4-8";
    public static final int DEFAULT_MAX_TOKENS = 4096;

    private volatile String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
    private volatile String defaultModel = DEFAULT_MODEL;
    private volatile int defaultMaxTokens = DEFAULT_MAX_TOKENS;

    public enum Command
            implements GetNameValue<String>, GetDescription {
        MESSAGES("messages", "v1/messages", HTTPMethod.POST, "Create a message"),
        COUNT_TOKENS("count-tokens", "v1/messages/count_tokens", HTTPMethod.POST, "Count the tokens of a message"),
        MODELS("models", "v1/models", HTTPMethod.GET, "List available models or retrieve one by id"),
        BATCH_CREATE("batch-create", "v1/messages/batches", HTTPMethod.POST, "Create a message batch"),
        BATCH_RETRIEVE("batch-retrieve", "v1/messages/batches", HTTPMethod.GET, "Retrieve a message batch"),
        BATCH_LIST("batch-list", "v1/messages/batches", HTTPMethod.GET, "List message batches"),
        BATCH_RESULTS("batch-results", "v1/messages/batches", HTTPMethod.GET, "Retrieve the results of an ended message batch"),
        BATCH_CANCEL("batch-cancel", "v1/messages/batches", HTTPMethod.POST, "Cancel a message batch"),
        FILE_UPLOAD("file-upload", "v1/files", HTTPMethod.POST, "Upload a file"),
        FILE_LIST("file-list", "v1/files", HTTPMethod.GET, "List uploaded files"),
        FILE_METADATA("file-metadata", "v1/files", HTTPMethod.GET, "Retrieve file metadata"),
        FILE_DOWNLOAD("file-download", "v1/files", HTTPMethod.GET, "Download file content"),
        FILE_DELETE("file-delete", "v1/files", HTTPMethod.DELETE, "Delete a file"),
        ;
        private final String name;
        private final String uri;
        private final HTTPMethod method;
        private final String description;

        Command(String name, String uri, HTTPMethod method, String description) {
            this.name = name;
            this.uri = uri;
            this.method = method;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public HTTPMethod getMethod() {
            return method;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getValue() {
            return uri;
        }
    }


    private AnthropicAPIBuilder() {
        buildEndpoints();
    }

    /**
     * @return the anthropic-version header value sent with every request
     */
    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    /**
     * Override the anthropic-version header value, applied to all subsequent requests
     * @param version the anthropic-version header value (e.g., "2023-06-01")
     * @return this builder
     */
    public AnthropicAPIBuilder setAnthropicVersion(String version) {
        SUS.checkIfNulls("version null", version);
        this.anthropicVersion = version;
        return this;
    }

    /**
     * @return the model used when a request does not specify one
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * Override the default model, applied when a request does not specify one
     * @param model the model id (e.g., "claude-opus-4-8")
     * @return this builder
     */
    public AnthropicAPIBuilder setDefaultModel(String model) {
        SUS.checkIfNulls("model null", model);
        this.defaultModel = model;
        return this;
    }

    /**
     * @return the max_tokens used when a request does not specify a positive value
     */
    public int getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    /**
     * Override the default max_tokens, applied when a request does not specify a positive value
     * @param maxTokens the max_tokens value, must be &gt; 0
     * @return this builder
     */
    public AnthropicAPIBuilder setDefaultMaxTokens(int maxTokens) {
        if (maxTokens <= 0)
            throw new IllegalArgumentException("maxTokens must be > 0: " + maxTokens);
        this.defaultMaxTokens = maxTokens;
        return this;
    }

    /**
     * Register one endpoint per command for framework wiring (domain lookup, url updates, canonical ids).
     * The request encoding and actual API calls are delegated to the com.anthropic:anthropic-java SDK
     * by AnthropicAPI.syncCall, these endpoints are not used to perform the HTTP calls.
     */
    private void buildEndpoints() {
        for (Command command : Command.values()) {
            HTTPMessageConfigInterface hmci = HTTPMessageConfig.createAndInit(ANTHROPIC_URL, command.getValue(), command.getMethod(), true, HTTPMediaType.APPLICATION_JSON);
            hmci.setAccept(HTTPMediaType.APPLICATION_JSON);
            // Anthropic requires these headers
            hmci.getHeaders().build("anthropic-version", anthropicVersion);

            HTTPAPIEndPoint<NVGenericMap, NVGenericMap> endpoint = HTTPAPIManager.SINGLETON.buildEndPoint(command, DOMAIN, command.getDescription(), hmci);
            endpoint.setRateController(ANTHROPIC_RC);
            endpoint.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
            if (log.isEnabled()) log.getLogger().info("Endpoint:" + endpoint.toCanonicalID());
            HTTPAPIManager.SINGLETON.register(endpoint);
        }
    }


    public NVGenericMap toPromptParams(String model, String prompt, int maxTokens) {
        return toVisionParams(model, prompt, maxTokens, (UByteArrayOutputStream) null, null, null);
    }

    public NVGenericMap toPromptParams(String model, String prompt, int maxTokens, String systemPrompt) {
        return toVisionParams(model, prompt, maxTokens, (UByteArrayOutputStream) null, null, systemPrompt);
    }

    public NVGenericMap toVisionParams(String model, String prompt, int maxTokens, UByteArrayOutputStream image, String imageType) {
        return toVisionParams(model, prompt, maxTokens, image, imageType, null);
    }

    public NVGenericMap toVisionParams(String model, String prompt, int maxTokens, UByteArrayOutputStream image, String imageType, String systemPrompt) {
        NVGenericMap ret = new NVGenericMap()
                .build("model", SUS.isNotEmpty(model) ? model : defaultModel)
                .build("prompt", prompt)
                .build(new NVInt("max-tokens", maxTokens > 0 ? maxTokens : defaultMaxTokens));

        if (SUS.isNotEmpty(systemPrompt)) {
            ret.build("system", systemPrompt);
        }

        if (image != null)
            ret.build(new NamedValue<UByteArrayOutputStream>("image", image)).build("image-type", imageType);

        return ret;
    }

    public NVGenericMap toVisionParams(String model, String prompt, int maxTokens, InputStream image, String imageType) {
        return toVisionParams(model, prompt, maxTokens, image, imageType, null);
    }

    public NVGenericMap toVisionParams(String model, String prompt, int maxTokens, InputStream image, String imageType, String systemPrompt) {
        NVGenericMap ret = new NVGenericMap()
                .build("model", SUS.isNotEmpty(model) ? model : defaultModel)
                .build("prompt", prompt)
                .build(new NVInt("max-tokens", maxTokens > 0 ? maxTokens : defaultMaxTokens));

        if (SUS.isNotEmpty(systemPrompt)) {
            ret.build("system", systemPrompt);
        }

        if (image != null)
            ret.build(new NamedValue<InputStream>("image", image)).build("image-type", imageType);

        return ret;
    }

    /**
     * Build a single-turn PDF document request
     * @param model the model, null/empty uses the default
     * @param prompt the user prompt
     * @param maxTokens maximum tokens, &lt;= 0 uses the default
     * @param document the pdf document content
     * @param systemPrompt optional system prompt
     * @return the params map
     */
    public NVGenericMap toDocumentParams(String model, String prompt, int maxTokens, UByteArrayOutputStream document, String systemPrompt) {
        NVGenericMap ret = toPromptParams(model, prompt, maxTokens, systemPrompt);
        if (document != null)
            ret.build(new NamedValue<UByteArrayOutputStream>("document", document));
        return ret;
    }

    /**
     * Build a multi-turn conversation request
     * @param model the model, null/empty uses the default
     * @param maxTokens maximum tokens, &lt;= 0 uses the default
     * @param systemPrompt optional system prompt
     * @param messages the conversation messages in Anthropic wire format, see toMessage
     * @return the params map
     */
    public NVGenericMap toMessagesParams(String model, int maxTokens, String systemPrompt, NVGenericMapList messages) {
        SUS.checkIfNulls("messages null", messages);
        NVGenericMap ret = new NVGenericMap()
                .build("model", SUS.isNotEmpty(model) ? model : defaultModel)
                .build(new NVInt("max-tokens", maxTokens > 0 ? maxTokens : defaultMaxTokens));

        if (SUS.isNotEmpty(systemPrompt)) {
            ret.build("system", systemPrompt);
        }
        messages.setName("messages");
        ret.build(messages);
        return ret;
    }

    /**
     * Build a single conversation message entry
     * @param role "user" or "assistant"
     * @param content the message text
     * @return the message map {role, content}
     */
    public static NVGenericMap toMessage(String role, String content) {
        return new NVGenericMap()
                .build("role", role)
                .build("content", content);
    }

    public AnthropicAPI createAPI(String name, String description, NVGenericMap props) {
        return HTTPAPIManager.SINGLETON.buildAPICaller(new AnthropicAPI(name, description), DOMAIN, props);
    }

}
