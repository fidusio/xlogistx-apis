package io.xlogistx.api.anthropic;

import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Minimal in-process Anthropic API mock backed by com.sun.net.httpserver.
 * Routes are keyed on method + path and return canned wire-format responses,
 * the last received request is recorded for assertions.
 */
public class MockAnthropicServer implements Closeable {

    public static class RecordedRequest {
        public final String method;
        public final String path;
        public final String body;
        public final String apiKey;
        public final String anthropicVersion;
        public final String anthropicBeta;

        RecordedRequest(String method, String path, String body, String apiKey, String anthropicVersion, String anthropicBeta) {
            this.method = method;
            this.path = path;
            this.body = body;
            this.apiKey = apiKey;
            this.anthropicVersion = anthropicVersion;
            this.anthropicBeta = anthropicBeta;
        }
    }

    public static final byte[] FILE_CONTENT = "mock file content".getBytes(StandardCharsets.UTF_8);

    public static final String MESSAGE_JSON = "{\"id\":\"msg_01\",\"type\":\"message\",\"role\":\"assistant\"," +
            "\"model\":\"claude-opus-4-8\",\"content\":[{\"type\":\"text\",\"text\":\"hello from mock\"}]," +
            "\"stop_reason\":\"end_turn\",\"stop_sequence\":null," +
            "\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0}}";

    public static final String REFUSAL_JSON = "{\"id\":\"msg_02\",\"type\":\"message\",\"role\":\"assistant\"," +
            "\"model\":\"claude-opus-4-8\",\"content\":[]," +
            "\"stop_reason\":\"refusal\",\"stop_sequence\":null," +
            "\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}";

    public static final String TRUNCATED_JSON = "{\"id\":\"msg_03\",\"type\":\"message\",\"role\":\"assistant\"," +
            "\"model\":\"claude-opus-4-8\",\"content\":[{\"type\":\"text\",\"text\":\"partial answer\"}]," +
            "\"stop_reason\":\"max_tokens\",\"stop_sequence\":null," +
            "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

    public static final String ERROR_401_JSON = "{\"type\":\"error\"," +
            "\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";

    public static final String COUNT_JSON = "{\"input_tokens\":42}";

    public static final String MODEL_INFO_JSON = "{\"id\":\"claude-opus-4-8\",\"type\":\"model\"," +
            "\"display_name\":\"Claude Opus 4.8\",\"created_at\":\"2026-01-01T00:00:00Z\"}";

    public static final String MODELS_LIST_JSON = "{\"data\":[" + MODEL_INFO_JSON + "]," +
            "\"has_more\":false,\"first_id\":\"claude-opus-4-8\",\"last_id\":\"claude-opus-4-8\"}";

    public static final String BATCH_JSON = "{\"id\":\"msgbatch_01\",\"type\":\"message_batch\"," +
            "\"processing_status\":\"in_progress\"," +
            "\"request_counts\":{\"processing\":1,\"succeeded\":0,\"errored\":0,\"canceled\":0,\"expired\":0}," +
            "\"created_at\":\"2026-07-16T00:00:00Z\",\"expires_at\":\"2026-07-17T00:00:00Z\"," +
            "\"archived_at\":null,\"cancel_initiated_at\":null,\"ended_at\":null,\"results_url\":null}";

    public static final String BATCH_CANCELING_JSON = BATCH_JSON.replace("\"in_progress\"", "\"canceling\"");

    public static final String BATCH_LIST_JSON = "{\"data\":[" + BATCH_JSON + "]," +
            "\"has_more\":false,\"first_id\":\"msgbatch_01\",\"last_id\":\"msgbatch_01\"}";

    public static final String BATCH_RESULTS_JSONL =
            "{\"custom_id\":\"req-1\",\"result\":{\"type\":\"succeeded\",\"message\":" + MESSAGE_JSON + "}}\n" +
            "{\"custom_id\":\"req-2\",\"result\":{\"type\":\"errored\",\"error\":{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad request\"}}}}\n";

    public static final String FILE_META_JSON = "{\"id\":\"file_01\",\"type\":\"file\",\"filename\":\"test.txt\"," +
            "\"mime_type\":\"text/plain\",\"size_bytes\":17,\"created_at\":\"2026-07-16T00:00:00Z\",\"downloadable\":true}";

    public static final String FILE_LIST_JSON = "{\"data\":[" + FILE_META_JSON + "]," +
            "\"has_more\":false,\"first_id\":\"file_01\",\"last_id\":\"file_01\"}";

    public static final String FILE_DELETED_JSON = "{\"id\":\"file_01\",\"type\":\"file_deleted\"}";

    public static final String SSE_STREAM =
            "event: message_start\n" +
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_04\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-4-8\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":1}}}\n\n" +
            "event: content_block_start\n" +
            "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello \"}}\n\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"streaming \"}}\n\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"world\"}}\n\n" +
            "event: content_block_stop\n" +
            "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n" +
            "event: message_delta\n" +
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":5}}\n\n" +
            "event: message_stop\n" +
            "data: {\"type\":\"message_stop\"}\n\n";

    private HttpServer server;
    private volatile RecordedRequest lastRequest;

    public MockAnthropicServer start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] requestBody = readFully(exchange.getRequestBody());
            String body = new String(requestBody, StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            lastRequest = new RecordedRequest(method, path, body,
                    exchange.getRequestHeaders().getFirst("x-api-key"),
                    exchange.getRequestHeaders().getFirst("anthropic-version"),
                    exchange.getRequestHeaders().getFirst("anthropic-beta"));

            int status = 200;
            String contentType = "application/json";
            byte[] response;

            if ("/v1/messages".equals(path) && "POST".equals(method)) {
                if (body.contains("\"stream\":true")) {
                    exchange.getResponseHeaders().set("content-type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream os = exchange.getResponseBody();
                    os.write(SSE_STREAM.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    exchange.close();
                    return;
                }
                if (body.contains("trigger-error")) {
                    status = 401;
                    response = bytes(ERROR_401_JSON);
                } else if (body.contains("trigger-refusal")) {
                    response = bytes(REFUSAL_JSON);
                } else if (body.contains("trigger-truncate")) {
                    response = bytes(TRUNCATED_JSON);
                } else {
                    response = bytes(MESSAGE_JSON);
                }
            } else if ("/v1/messages/count_tokens".equals(path)) {
                response = bytes(COUNT_JSON);
            } else if ("/v1/models".equals(path)) {
                response = bytes(MODELS_LIST_JSON);
            } else if (path.startsWith("/v1/models/")) {
                response = bytes(MODEL_INFO_JSON);
            } else if ("/v1/messages/batches".equals(path) && "POST".equals(method)) {
                response = bytes(BATCH_JSON);
            } else if ("/v1/messages/batches".equals(path) && "GET".equals(method)) {
                response = bytes(BATCH_LIST_JSON);
            } else if (path.startsWith("/v1/messages/batches/") && path.endsWith("/results")) {
                contentType = "application/x-jsonl";
                response = bytes(BATCH_RESULTS_JSONL);
            } else if (path.startsWith("/v1/messages/batches/") && path.endsWith("/cancel")) {
                response = bytes(BATCH_CANCELING_JSON);
            } else if (path.startsWith("/v1/messages/batches/")) {
                response = bytes(BATCH_JSON);
            } else if ("/v1/files".equals(path) && "POST".equals(method)) {
                response = bytes(FILE_META_JSON);
            } else if ("/v1/files".equals(path) && "GET".equals(method)) {
                response = bytes(FILE_LIST_JSON);
            } else if (path.startsWith("/v1/files/") && path.endsWith("/content")) {
                contentType = "application/octet-stream";
                response = FILE_CONTENT;
            } else if (path.startsWith("/v1/files/") && "DELETE".equals(method)) {
                response = bytes(FILE_DELETED_JSON);
            } else if (path.startsWith("/v1/files/") && "GET".equals(method)) {
                response = bytes(FILE_META_JSON);
            } else {
                status = 404;
                response = bytes("{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"not found: " + path + "\"}}");
            }

            exchange.getResponseHeaders().set("content-type", contentType);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return this;
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public RecordedRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public void close() {
        if (server != null)
            server.stop(0);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readFully(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1)
            baos.write(buffer, 0, read);
        return baos.toByteArray();
    }
}
