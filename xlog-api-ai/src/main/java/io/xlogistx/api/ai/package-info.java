/**
 * Thin, provider agnostic client for OpenAI compatible AI HTTP APIs.
 * <p>
 * The package exposes two types:
 * <ul>
 *   <li>{@link io.xlogistx.api.ai.AIAPIBuilder} registers the supported endpoints
 *       ({@code chat/completions}, {@code audio/transcriptions}, {@code audio/speech}, {@code models})
 *       with the zoxweb {@code HTTPAPIManager} under the {@value io.xlogistx.api.ai.AIAPIBuilder#DOMAIN}
 *       domain, knows the base URL of each supported provider ({@link io.xlogistx.api.ai.AIAPIBuilder.AIAPIType})
 *       and builds the request parameter maps consumed by the endpoints.</li>
 *   <li>{@link io.xlogistx.api.ai.AIAPI} is the caller bound to one provider and one API key.
 *       It offers synchronous and asynchronous text and vision completions, audio transcription
 *       and model listing, plus {@link io.xlogistx.api.ai.AIAPI#AIMDDecoder} to turn a raw
 *       response into displayable markdown.</li>
 * </ul>
 * <p>
 * Typical usage:
 * <pre>{@code
 * AIAPI api = AIAPIBuilder.createAIAPI(AIAPIBuilder.AIAPIType.OPEN_AI, null, apiKey);
 * String answer = api.completion("gpt-4o", "Summarize this text ...", 0);
 * String vision = api.completion("gpt-4o", "What is in the picture?", 0, "png", pngStream);
 * String[] models = api.availableModels();
 * }</pre>
 * <p>
 * All providers are addressed through the OpenAI wire format, so Grok and Gemini work through
 * their OpenAI compatible base URLs. Anthropic requires its own authorization header, which
 * {@link io.xlogistx.api.ai.AIAPIBuilder#createAIAPI(io.xlogistx.api.ai.AIAPIBuilder.AIAPIType, String, String)}
 * installs automatically.
 * <p>
 * Calls are throttled by the shared {@link io.xlogistx.api.ai.AIAPIBuilder#GPT_RC} rate controller.
 */
package io.xlogistx.api.ai;
