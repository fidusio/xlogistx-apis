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
        MESSAGES("messages", "v1/messages", "Create a message endpoint"),
        COUNT_TOKENS("count-tokens", "v1/messages/count_tokens", "Count the tokens of a message endpoint"),
        ;
        private final String name;
        private final String uri;
        private final String description;

        Command(String name, String uri, String description) {
            this.name = name;
            this.uri = uri;
            this.description = description;
        }

        public String getName() {
            return name;
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
            HTTPMessageConfigInterface hmci = HTTPMessageConfig.createAndInit(ANTHROPIC_URL, command.getValue(), HTTPMethod.POST, true, HTTPMediaType.APPLICATION_JSON);
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

    public AnthropicAPI createAPI(String name, String description, NVGenericMap props) {
        return HTTPAPIManager.SINGLETON.buildAPICaller(new AnthropicAPI(name, description), DOMAIN, props);
    }

}
