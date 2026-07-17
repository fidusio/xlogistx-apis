package io.xlogistx.api.anthropic;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.NVBoolean;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.NVGenericMapList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MESSAGES and COUNT_TOKENS command tests against an in-process mock of the Anthropic API.
 */
public class AnthropicMessagesTest {

    static MockAnthropicServer server;
    static AnthropicAPI api;

    @BeforeAll
    static void setup() throws IOException {
        AnthropicAPIBuilder.ANTHROPIC_RC.setRate("100000/s");
        server = new MockAnthropicServer().start();
        api = AnthropicAPIBuilder.SINGLETON.createAPI("junit-messages", "messages tests",
                HTTPAPIBuilder.Prop.toProp(null, HTTPAuthorization.createAuthorization("x-api-key", "sk-test-key")));
        api.updateURL(server.url());
    }

    @AfterAll
    static void teardown() {
        server.close();
    }

    static NVGenericMap lastBody() {
        return GSONUtil.fromJSONDefault(server.lastRequest().body, NVGenericMap.class);
    }

    static int intValue(NVGenericMap nvgm, String name) {
        return ((Number) nvgm.get(name).getValue()).intValue();
    }

    @Test
    public void completionAppliesDefaults() throws IOException {
        String result = api.completion("say hello");
        assertEquals("hello from mock", result);

        MockAnthropicServer.RecordedRequest request = server.lastRequest();
        assertEquals("POST", request.method);
        assertEquals("/v1/messages", request.path);
        assertEquals("sk-test-key", request.apiKey);
        assertEquals(AnthropicAPIBuilder.DEFAULT_ANTHROPIC_VERSION, request.anthropicVersion);

        NVGenericMap body = lastBody();
        assertEquals(AnthropicAPIBuilder.DEFAULT_MODEL, body.getValue("model"));
        assertEquals(AnthropicAPIBuilder.DEFAULT_MAX_TOKENS, intValue(body, "max_tokens"));
        NVGenericMapList messages = (NVGenericMapList) body.get("messages");
        assertEquals(1, messages.getValue().size());
        assertEquals("user", messages.getValue().get(0).getValue("role"));
        assertEquals("say hello", messages.getValue().get(0).getValue("content"));
    }

    @Test
    public void completionAppliesExplicitParams() throws IOException {
        api.completion("claude-sonnet-5", "say hello", 1234, "you are a test");

        NVGenericMap body = lastBody();
        assertEquals("claude-sonnet-5", body.getValue("model"));
        assertEquals(1234, intValue(body, "max_tokens"));
        assertEquals("you are a test", body.getValue("system"));
    }

    @Test
    public void cachedSystemPromptBecomesBlockWithCacheControl() throws IOException {
        NVGenericMap params = AnthropicAPIBuilder.SINGLETON.toPromptParams(null, "hi", 0, "cached system prompt");
        params.build(new NVBoolean("cache-system", true));
        api.syncCall(AnthropicAPIBuilder.Command.MESSAGES, params);

        NVGenericMap body = lastBody();
        NVGenericMapList system = (NVGenericMapList) body.get("system");
        NVGenericMap systemBlock = system.getValue().get(0);
        assertEquals("cached system prompt", systemBlock.getValue("text"));
        NVGenericMap cacheControl = (NVGenericMap) systemBlock.get("cache_control");
        assertEquals("ephemeral", cacheControl.getValue("type"));
    }

    @Test
    public void multiTurnChatCompletion() throws IOException {
        NVGenericMapList messages = new NVGenericMapList("messages");
        messages.add(AnthropicAPIBuilder.toMessage("user", "hello"));
        messages.add(AnthropicAPIBuilder.toMessage("assistant", "hi, how can I help?"));
        messages.add(AnthropicAPIBuilder.toMessage("user", "tell me a joke"));

        String result = api.chatCompletion(null, messages, 0, "be funny");
        assertEquals("hello from mock", result);

        NVGenericMap body = lastBody();
        assertEquals("be funny", body.getValue("system"));
        NVGenericMapList wireMessages = (NVGenericMapList) body.get("messages");
        assertEquals(3, wireMessages.getValue().size());
        assertEquals("assistant", wireMessages.getValue().get(1).getValue("role"));
        assertEquals("tell me a joke", wireMessages.getValue().get(2).getValue("content"));
    }

    @Test
    public void multiTurnWithToolUseAndToolResultBlocks() throws IOException {
        NVGenericMapList messages = new NVGenericMapList("messages");
        messages.add(AnthropicAPIBuilder.toMessage("user", "what is 6*7?"));

        NVGenericMap assistantTurn = new NVGenericMap().build("role", "assistant");
        NVGenericMapList assistantContent = new NVGenericMapList("content");
        assistantContent.add(new NVGenericMap()
                .build("type", "tool_use")
                .build("id", "toolu_01")
                .build("name", "calculator")
                .build(new NVGenericMap("input").build("expression", "6*7")));
        assistantTurn.add(assistantContent);
        messages.add(assistantTurn);

        NVGenericMap toolResultTurn = new NVGenericMap().build("role", "user");
        NVGenericMapList toolResultContent = new NVGenericMapList("content");
        toolResultContent.add(new NVGenericMap()
                .build("type", "tool_result")
                .build("tool_use_id", "toolu_01")
                .build("content", "42"));
        toolResultTurn.add(toolResultContent);
        messages.add(toolResultTurn);

        api.chatCompletion(null, messages, 0, null);

        NVGenericMap body = lastBody();
        NVGenericMapList wireMessages = (NVGenericMapList) body.get("messages");
        assertEquals(3, wireMessages.getValue().size());

        NVGenericMapList wireAssistantContent = (NVGenericMapList) wireMessages.getValue().get(1).get("content");
        NVGenericMap toolUse = wireAssistantContent.getValue().get(0);
        assertEquals("tool_use", toolUse.getValue("type"));
        assertEquals("calculator", toolUse.getValue("name"));
        assertEquals("6*7", ((NVGenericMap) toolUse.get("input")).getValue("expression"));

        NVGenericMapList wireToolResultContent = (NVGenericMapList) wireMessages.getValue().get(2).get("content");
        NVGenericMap toolResult = wireToolResultContent.getValue().get(0);
        assertEquals("tool_result", toolResult.getValue("type"));
        assertEquals("toolu_01", toolResult.getValue("tool_use_id"));
    }

    @Test
    public void toolDefinitionsAreForwarded() throws IOException {
        NVGenericMap params = AnthropicAPIBuilder.SINGLETON.toPromptParams(null, "weather in Paris?", 0);
        NVGenericMapList tools = new NVGenericMapList("tools");
        NVGenericMap inputSchema = new NVGenericMap("input_schema").build("type", "object");
        NVGenericMap properties = new NVGenericMap("properties");
        properties.add(new NVGenericMap("location").build("type", "string"));
        inputSchema.add(properties);
        tools.add(new NVGenericMap()
                .build("name", "get_weather")
                .build("description", "Get the current weather for a location")
                .build(inputSchema));
        params.add(tools);

        api.syncCall(AnthropicAPIBuilder.Command.MESSAGES, params);

        NVGenericMap body = lastBody();
        NVGenericMapList wireTools = (NVGenericMapList) body.get("tools");
        NVGenericMap wireTool = wireTools.getValue().get(0);
        assertEquals("get_weather", wireTool.getValue("name"));
        NVGenericMap wireSchema = (NVGenericMap) wireTool.get("input_schema");
        assertEquals("object", wireSchema.getValue("type"));
        assertEquals("string", ((NVGenericMap) ((NVGenericMap) wireSchema.get("properties")).get("location")).getValue("type"));
    }

    @ParameterizedTest
    @CsvSource({"auto,auto", "any,any", "none,none", "my_tool,tool"})
    public void toolChoiceMapping(String choice, String expectedType) throws IOException {
        NVGenericMap params = AnthropicAPIBuilder.SINGLETON.toPromptParams(null, "hi", 0);
        params.build("tool-choice", choice);
        api.syncCall(AnthropicAPIBuilder.Command.MESSAGES, params);

        NVGenericMap toolChoice = (NVGenericMap) lastBody().get("tool_choice");
        assertEquals(expectedType, toolChoice.getValue("type"));
        if ("tool".equals(expectedType))
            assertEquals(choice, toolChoice.getValue("name"));
    }

    @Test
    public void thinkingEffortSchemaStopSequencesAndUserId() throws IOException {
        NVGenericMap params = AnthropicAPIBuilder.SINGLETON.toPromptParams(null, "extract data", 0);
        params.build("thinking", "adaptive");
        params.build("effort", "xhigh");
        params.build("stop-sequences", "STOP,END");
        params.build("user-id", "user-123");
        NVGenericMap schema = new NVGenericMap("json-schema").build("type", "object");
        NVGenericMap schemaProperties = new NVGenericMap("properties");
        schemaProperties.add(new NVGenericMap("name").build("type", "string"));
        schema.add(schemaProperties);
        params.add(schema);

        api.syncCall(AnthropicAPIBuilder.Command.MESSAGES, params);

        NVGenericMap body = lastBody();
        assertEquals("adaptive", ((NVGenericMap) body.get("thinking")).getValue("type"));

        NVGenericMap outputConfig = (NVGenericMap) body.get("output_config");
        assertEquals("xhigh", outputConfig.getValue("effort"));
        NVGenericMap format = (NVGenericMap) outputConfig.get("format");
        assertEquals("json_schema", format.getValue("type"));
        assertEquals("object", ((NVGenericMap) format.get("schema")).getValue("type"));

        assertTrue(server.lastRequest().body.contains("\"stop_sequences\":[\"STOP\",\"END\"]"));
        assertEquals("user-123", ((NVGenericMap) body.get("metadata")).getValue("user_id"));
    }

    @Test
    public void visionCompletionEncodesImage() throws IOException {
        byte[] imageBytes = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
        String result = api.visionCompletion(null, "describe", 0, new ByteArrayInputStream(imageBytes), "png");
        assertEquals("hello from mock", result);

        NVGenericMap body = lastBody();
        NVGenericMapList messages = (NVGenericMapList) body.get("messages");
        NVGenericMapList content = (NVGenericMapList) messages.getValue().get(0).get("content");
        assertEquals(2, content.getValue().size());

        NVGenericMap imageBlock = content.getValue().get(0);
        assertEquals("image", imageBlock.getValue("type"));
        NVGenericMap source = (NVGenericMap) imageBlock.get("source");
        assertEquals("base64", source.getValue("type"));
        assertEquals("image/png", source.getValue("media_type"));
        assertEquals("iVBORwECAwQ=", source.getValue("data"));

        assertEquals("text", content.getValue().get(1).getValue("type"));
        assertEquals("describe", content.getValue().get(1).getValue("text"));
    }

    @Test
    public void documentCompletionEncodesPdf() throws IOException {
        UByteArrayOutputStream pdf = new UByteArrayOutputStream();
        pdf.write("%PDF-1.4 fake".getBytes(), 0, 13);

        String result = api.documentCompletion(null, "summarize this", 0, pdf, null);
        assertEquals("hello from mock", result);

        NVGenericMap body = lastBody();
        NVGenericMapList messages = (NVGenericMapList) body.get("messages");
        NVGenericMapList content = (NVGenericMapList) messages.getValue().get(0).get("content");
        NVGenericMap documentBlock = content.getValue().get(0);
        assertEquals("document", documentBlock.getValue("type"));
        NVGenericMap source = (NVGenericMap) documentBlock.get("source");
        assertEquals("application/pdf", source.getValue("media_type"));
        assertEquals("base64", source.getValue("type"));
    }

    @Test
    public void streamingCollectsTextDeltas() throws IOException {
        List<String> deltas = new ArrayList<String>();
        NVGenericMap params = AnthropicAPIBuilder.SINGLETON.toPromptParams(null, "stream me", 100);
        String fullText = api.streamCompletion(params, deltas::add);

        assertEquals("Hello streaming world", fullText);
        assertEquals(3, deltas.size());
        assertEquals("Hello ", deltas.get(0));
        assertTrue(server.lastRequest().body.contains("\"stream\":true"));
    }

    @Test
    public void countTokens() throws IOException {
        int tokens = api.countTokens(null, "how many tokens am I");
        assertEquals(42, tokens);
        assertEquals("/v1/messages/count_tokens", server.lastRequest().path);
        assertEquals(AnthropicAPIBuilder.DEFAULT_MODEL, lastBody().getValue("model"));
    }

    @Test
    public void apiErrorThrowsIOException() {
        IOException e = assertThrows(IOException.class, () -> api.completion("trigger-error"));
        assertTrue(e.getMessage().contains("authentication_error"), e.getMessage());
    }

    @Test
    public void refusalThrowsIOException() {
        IOException e = assertThrows(IOException.class, () -> api.completion("trigger-refusal"));
        assertTrue(e.getMessage().contains("refusal"), e.getMessage());
    }

    @Test
    public void truncatedResponseStillReturnsText() throws IOException {
        assertEquals("partial answer", api.completion("trigger-truncate"));
    }

    @Test
    public void invalidThinkingValueThrows() {
        NVGenericMap params = AnthropicAPIBuilder.SINGLETON.toPromptParams(null, "hi", 0);
        params.build("thinking", "banana");
        IOException e = assertThrows(IOException.class, () -> api.syncCall(AnthropicAPIBuilder.Command.MESSAGES, params));
        assertTrue(e.getMessage().contains("thinking"), e.getMessage());
    }

    @Test
    public void rawCompletionExposesWireFormat() throws IOException {
        NVGenericMap response = api.rawCompletion(null, "say hello", 0);
        assertEquals("msg_01", response.getValue("id"));
        assertEquals("end_turn", response.getValue("stop_reason"));
        NVGenericMap usage = (NVGenericMap) response.get("usage");
        assertEquals(10, intValue(usage, "input_tokens"));
        assertEquals(5, intValue(usage, "output_tokens"));
    }
}
