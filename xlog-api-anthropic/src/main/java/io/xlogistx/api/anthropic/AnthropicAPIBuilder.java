package io.xlogistx.api.anthropic;

import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.*;
import org.zoxweb.shared.util.*;

import java.io.Closeable;
import java.io.InputStream;

public class AnthropicAPIBuilder
        implements HTTPAPIBuilder {

    public static final LogWrapper log = new LogWrapper(AnthropicAPIBuilder.class).setEnabled(true);
    public static final AnthropicAPIBuilder SINGLETON = new AnthropicAPIBuilder();
    public static final RateController ANTHROPIC_RC = new RateController("ANTHROPIC-RC", "60/m");
    public static final String DOMAIN = "anthropic-api";
    public static final String ANTHROPIC_URL = "https://api.anthropic.com";
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    public static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    public enum Command
            implements GetNameValue<String>, GetDescription {
        MESSAGES("messages", "v1/messages", "Create a message endpoint"),
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
        buildMessagesEndpoint();
    }

    private void buildMessagesEndpoint() {
        HTTPMessageConfigInterface messagesHMCI = HTTPMessageConfig.createAndInit(ANTHROPIC_URL, Command.MESSAGES.getValue(), HTTPMethod.POST, true, HTTPMediaType.APPLICATION_JSON);
        messagesHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        // Anthropic requires these headers
        messagesHMCI.getHeaders().build("anthropic-version", ANTHROPIC_VERSION);

        HTTPAPIEndPoint<NVGenericMap, NVGenericMap> messagesEndpoint = HTTPAPIManager.SINGLETON.buildEndPoint(Command.MESSAGES, DOMAIN, "Create a message", messagesHMCI);
        messagesEndpoint.setRateController(ANTHROPIC_RC);

        messagesEndpoint.setDataDecoder(hrd -> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        if (log.isEnabled()) log.getLogger().info("Endpoint:" + messagesEndpoint.toCanonicalID());

        messagesEndpoint.setDataEncoder((hmci, param) -> {
            try {
                Object imageValue = param.getValue("image");
                byte[] imageBuffer = null;
                int imageOffset = 0;
                int imageLength = -1;

                if (imageValue instanceof UByteArrayOutputStream) {
                    imageBuffer = ((UByteArrayOutputStream) imageValue).getInternalBuffer();
                    imageLength = ((UByteArrayOutputStream) imageValue).size();
                } else if (imageValue instanceof InputStream) {
                    imageBuffer = new byte[((InputStream) imageValue).available()];
                    imageLength = ((InputStream) imageValue).read(imageBuffer);
                    IOUtil.close((Closeable) imageValue);
                }

                String imageBase64 = imageBuffer != null ? SharedBase64.encodeAsString(SharedBase64.Base64Type.DEFAULT,
                        imageBuffer,
                        imageOffset,
                        imageLength) : null;

                NVGenericMap requestContent = new NVGenericMap();
                requestContent.build("model", param.getValue("model"));

                // Handle max_tokens - Anthropic requires this field
                NVInt maxTokens = (NVInt) param.get("max-tokens");
                int maxTokensValue = (maxTokens != null && maxTokens.getValue() > 0) ? maxTokens.getValue() : 4096;
                requestContent.build(new NVInt("max_tokens", maxTokensValue));

                // Handle system prompt if provided
                String systemPrompt = param.getValue("system");
                if (SUS.isNotEmpty(systemPrompt)) {
                    requestContent.build("system", systemPrompt);
                }

                // Build messages array
                NVGenericMapList messages = new NVGenericMapList("messages");
                requestContent.build(messages);

                NVGenericMap userMessage = new NVGenericMap();
                messages.add(userMessage);
                userMessage.build("role", "user");

                // Build content - can be string or array for vision
                if (SUS.isNotEmpty(imageBase64)) {
                    // Vision request - content is an array
                    NVGenericMapList content = new NVGenericMapList("content");
                    userMessage.add(content);

                    // Add image block
                    String imageMediaType = param.getValue("image-type");
                    if (imageMediaType != null && !imageMediaType.contains("/")) {
                        imageMediaType = "image/" + imageMediaType;
                    }

                    content.add(new NVGenericMap()
                            .build("type", "image")
                            .build(new NVGenericMap("source")
                                    .build("type", "base64")
                                    .build("media_type", imageMediaType)
                                    .build("data", imageBase64)));

                    // Add text block
                    content.add(new NVGenericMap()
                            .build("type", "text")
                            .build("text", param.getValue("prompt")));
                } else {
                    // Text-only request - content is a string
                    userMessage.build("content", param.getValue("prompt"));
                }

                String jsonPayload = GSONUtil.toJSONDefault(requestContent);
                hmci.setContent(jsonPayload);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return hmci;
        });
        HTTPAPIManager.SINGLETON.register(messagesEndpoint);
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
                .build("model", model)
                .build("prompt", prompt)
                .build(new NVInt("max-tokens", maxTokens));

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
                .build("model", model)
                .build("prompt", prompt)
                .build(new NVInt("max-tokens", maxTokens));

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
