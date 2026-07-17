package io.xlogistx.api.anthropic;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zoxweb.server.http.HTTPAPIBuilder;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.NVGenericMapList;
import org.zoxweb.shared.util.NVInt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MODELS, BATCH_* and FILE_* command tests against an in-process mock of the Anthropic API.
 */
public class AnthropicEndpointsTest {

    static MockAnthropicServer server;
    static AnthropicAPI api;

    @BeforeAll
    static void setup() throws IOException {
        AnthropicAPIBuilder.ANTHROPIC_RC.setRate("100000/s");
        server = new MockAnthropicServer().start();
        api = AnthropicAPIBuilder.SINGLETON.createAPI("junit-endpoints", "endpoints tests",
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

    // ------------------------------------------------------------------
    // Models
    // ------------------------------------------------------------------

    @Test
    public void listModels() throws IOException {
        NVGenericMap response = api.listModels();
        assertEquals("GET", server.lastRequest().method);
        assertEquals("/v1/models", server.lastRequest().path);

        NVGenericMapList data = (NVGenericMapList) response.get("data");
        assertEquals(1, data.getValue().size());
        assertEquals("claude-opus-4-8", data.getValue().get(0).getValue("id"));
    }

    @Test
    public void modelInfo() throws IOException {
        NVGenericMap response = api.modelInfo("claude-opus-4-8");
        assertEquals("GET", server.lastRequest().method);
        assertEquals("/v1/models/claude-opus-4-8", server.lastRequest().path);
        assertEquals("Claude Opus 4.8", response.getValue("display_name"));
    }

    // ------------------------------------------------------------------
    // Batches
    // ------------------------------------------------------------------

    @Test
    public void createBatch() throws IOException {
        NVGenericMapList requests = new NVGenericMapList("requests");

        NVGenericMap wireParams = new NVGenericMap("params")
                .build("model", "claude-haiku-4-5")
                .build(new NVInt("max_tokens", 100));
        NVGenericMapList messages = new NVGenericMapList("messages");
        messages.add(AnthropicAPIBuilder.toMessage("user", "hi"));
        wireParams.add(messages);

        NVGenericMap request = new NVGenericMap().build("custom_id", "req-1");
        request.add(wireParams);
        requests.add(request);

        NVGenericMap response = api.createBatch(requests);
        assertEquals("msgbatch_01", response.getValue("id"));
        assertEquals("POST", server.lastRequest().method);
        assertEquals("/v1/messages/batches", server.lastRequest().path);

        NVGenericMap body = lastBody();
        NVGenericMapList wireRequests = (NVGenericMapList) body.get("requests");
        NVGenericMap wireRequest = wireRequests.getValue().get(0);
        assertEquals("req-1", wireRequest.getValue("custom_id"));
        NVGenericMap forwardedParams = (NVGenericMap) wireRequest.get("params");
        assertEquals("claude-haiku-4-5", forwardedParams.getValue("model"));
        assertEquals(100, ((Number) forwardedParams.get("max_tokens").getValue()).intValue());
        NVGenericMapList forwardedMessages = (NVGenericMapList) forwardedParams.get("messages");
        assertEquals("hi", forwardedMessages.getValue().get(0).getValue("content"));
    }

    @Test
    public void createBatchWithoutParamsThrows() {
        NVGenericMapList requests = new NVGenericMapList("requests");
        requests.add(new NVGenericMap().build("custom_id", "req-1"));
        IOException e = assertThrows(IOException.class, () -> api.createBatch(requests));
        assertTrue(e.getMessage().contains("custom_id and params"), e.getMessage());
    }

    @Test
    public void retrieveBatch() throws IOException {
        NVGenericMap response = api.retrieveBatch("msgbatch_01");
        assertEquals("GET", server.lastRequest().method);
        assertEquals("/v1/messages/batches/msgbatch_01", server.lastRequest().path);
        assertEquals("in_progress", response.getValue("processing_status"));
    }

    @Test
    public void listBatches() throws IOException {
        NVGenericMap response = api.listBatches();
        assertEquals("GET", server.lastRequest().method);
        assertEquals("/v1/messages/batches", server.lastRequest().path);
        NVGenericMapList data = (NVGenericMapList) response.get("data");
        assertEquals("msgbatch_01", data.getValue().get(0).getValue("id"));
    }

    @Test
    public void batchResults() throws IOException {
        NVGenericMap response = api.batchResults("msgbatch_01");
        assertEquals("/v1/messages/batches/msgbatch_01/results", server.lastRequest().path);

        NVGenericMapList results = (NVGenericMapList) response.get("results");
        assertEquals(2, results.getValue().size());

        NVGenericMap succeeded = results.getValue().get(0);
        assertEquals("req-1", succeeded.getValue("custom_id"));
        NVGenericMap succeededResult = (NVGenericMap) succeeded.get("result");
        assertEquals("succeeded", succeededResult.getValue("type"));
        assertEquals("hello from mock", AnthropicAPI.parseMessageResponse((NVGenericMap) succeededResult.get("message")));

        NVGenericMap errored = results.getValue().get(1);
        assertEquals("req-2", errored.getValue("custom_id"));
        assertEquals("errored", ((NVGenericMap) errored.get("result")).getValue("type"));
    }

    @Test
    public void cancelBatch() throws IOException {
        NVGenericMap response = api.cancelBatch("msgbatch_01");
        assertEquals("POST", server.lastRequest().method);
        assertEquals("/v1/messages/batches/msgbatch_01/cancel", server.lastRequest().path);
        assertEquals("canceling", response.getValue("processing_status"));
    }

    @Test
    public void batchCommandsRequireBatchId() {
        IOException e = assertThrows(IOException.class, () -> api.retrieveBatch(null));
        assertTrue(e.getMessage().contains("batch-id"), e.getMessage());
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    @Test
    public void uploadFile() throws IOException {
        UByteArrayOutputStream content = new UByteArrayOutputStream();
        byte[] payload = "upload me".getBytes(StandardCharsets.UTF_8);
        content.write(payload, 0, payload.length);

        NVGenericMap response = api.uploadFile("test.txt", content);
        assertEquals("file_01", response.getValue("id"));
        assertEquals("POST", server.lastRequest().method);
        assertEquals("/v1/files", server.lastRequest().path);
        // multipart body carries the filename and the content
        assertTrue(server.lastRequest().body.contains("filename=\"test.txt\""));
        assertTrue(server.lastRequest().body.contains("upload me"));
        // beta header required by the Files API
        assertNotNull(server.lastRequest().anthropicBeta);
        assertTrue(server.lastRequest().anthropicBeta.contains("files-api"), server.lastRequest().anthropicBeta);
    }

    @Test
    public void listFiles() throws IOException {
        NVGenericMap response = api.listFiles();
        assertEquals("GET", server.lastRequest().method);
        assertEquals("/v1/files", server.lastRequest().path);
        NVGenericMapList data = (NVGenericMapList) response.get("data");
        assertEquals("file_01", data.getValue().get(0).getValue("id"));
    }

    @Test
    public void fileMetadata() throws IOException {
        NVGenericMap response = api.fileMetadata("file_01");
        assertEquals("GET", server.lastRequest().method);
        assertEquals("/v1/files/file_01", server.lastRequest().path);
        assertEquals("test.txt", response.getValue("filename"));
    }

    @Test
    public void downloadFile() throws IOException {
        UByteArrayOutputStream content = api.downloadFile("file_01");
        assertEquals("/v1/files/file_01/content", server.lastRequest().path);
        assertArrayEquals(MockAnthropicServer.FILE_CONTENT, content.toByteArray());
    }

    @Test
    public void deleteFile() throws IOException {
        NVGenericMap response = api.deleteFile("file_01");
        assertEquals("DELETE", server.lastRequest().method);
        assertEquals("/v1/files/file_01", server.lastRequest().path);
        assertEquals("file_deleted", response.getValue("type"));
    }

    @Test
    public void fileCommandsRequireFileId() {
        IOException e = assertThrows(IOException.class, () -> api.fileMetadata(null));
        assertTrue(e.getMessage().contains("file-id"), e.getMessage());
    }
}
