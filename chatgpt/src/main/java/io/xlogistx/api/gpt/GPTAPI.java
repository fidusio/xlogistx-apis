package io.xlogistx.api.gpt;

import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.http.HTTPAPIEndPoint;
import org.zoxweb.server.http.HTTPAPIManager;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.server.logging.LogWrapper;
import org.zoxweb.server.util.GSONUtil;
import org.zoxweb.shared.http.*;
import org.zoxweb.shared.util.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

public class GPTAPI
{

    public static final LogWrapper log = new LogWrapper(GPTAPI.class).setEnabled(true);
    public static final GPTAPI SINGLETON = new GPTAPI();
    public static final RateController GPT_RC = new RateController("GPT-RC", "100/m");
    public static final String DOMAIN = "gpt-api";
    public static final String TRANSCRIBE_MODEL = "whisper-1";

    public static final String GTP_URL = "https://api.openai.com";

    public enum Command
            implements GetName
    {
        COMPLETION("completion"),
        TRANSCRIBE("transcribe"),
        TEXT_TO_SPEECH("text-to-speech"),
        ;
        private final String name;
        Command(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }
    }


    private GPTAPI()
    {
        buildSpeechToTextAPI();
        buildCompletionEndPoint();
    }


    private void buildSpeechToTextAPI()
    {

        HTTPMessageConfigInterface speechToTextHMCI = HTTPMessageConfig.createAndInit(GTP_URL, "v1/audio/transcriptions", HTTPMethod.POST, true, HTTPMediaType.MULTIPART_FORM_DATA);
        HTTPAPIEndPoint<NamedValue<?>, NVGenericMap> speechToText = HTTPAPIManager.SINGLETON.buildEndPoint(Command.TRANSCRIBE, DOMAIN, "Convert speech to text", speechToTextHMCI);
        speechToText.setRateController(GPT_RC);

        speechToText.setDataDecoder(hrd-> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        if(log.isEnabled()) log.getLogger().info("Endpoint:" + speechToText.toCanonicalID());
        speechToText.setDataEncoder((hmci, param) ->
        {
            NamedValue<InputStream> nvc = new NamedValue<>();
            try
            {
                InputStream is = null;
                long length = 0;
                if (param.getValue() instanceof InputStream)
                {
                    is = (InputStream) param.getValue();
                    length = is instanceof ByteArrayInputStream ? ((ByteArrayInputStream)is).available() : param.getProperties().getValue("length");
                }
                else if (param.getValue() instanceof File)
                {
                    is = Files.newInputStream(((File) param.getValue()).toPath());
                    length = ((File) param.getValue()).length();
                }
                else if (param.getValue() instanceof String)
                {
                    File file = new File((String)param.getValue());
                    is = Files.newInputStream(file.toPath());
                    length = file.length();
                }
                nvc.setValue(is)
                        .setName("file")
                        .getProperties()
                        .build(HTTPConst.CNP.FILENAME, param.getName())
                        .build(new NVLong(HTTPConst.CNP.CONTENT_LENGTH, length));
                        //.build(new NVEnum(HTTPConst.CNP.MEDIA_TYPE, HTTPMediaType.lookupByExtension(file.getName())))
                String model = param.getProperties().getValue("model");
                hmci.getParameters().build(nvc).build("model", model != null ? model : TRANSCRIBE_MODEL);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return hmci;
        });
        HTTPAPIManager.SINGLETON.register(speechToText);


    }
    private void buildCompletionEndPoint()
    {
        HTTPMessageConfigInterface completionsPromptHMCI = HTTPMessageConfig.createAndInit(GTP_URL, "v1/chat/completions", HTTPMethod.POST, true, HTTPMediaType.APPLICATION_JSON);
        completionsPromptHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        HTTPAPIEndPoint<NVGenericMap, NVGenericMap> completionEndPoint = HTTPAPIManager.SINGLETON.buildEndPoint(Command.COMPLETION, DOMAIN, "Analyze Image based on prompt", completionsPromptHMCI);
        completionEndPoint.setRateController(GPT_RC);

        completionEndPoint.setDataDecoder(hrd-> GSONUtil.fromJSONDefault(hrd.getDataAsString(), NVGenericMap.class));
        if(log.isEnabled()) log.getLogger().info("Endpoint:" + completionEndPoint.toCanonicalID());
        completionEndPoint.setDataEncoder((hmci, param) ->
        {
            try
            {
                UByteArrayOutputStream image = param.getValue("image");
//                UByteArrayOutputStream baos = new UByteArrayOutputStream();
//                ImageIO.write(image, "png", baos);

                String imageBase64 = image != null ? SharedBase64.encodeAsString(SharedBase64.Base64Type.DEFAULT,
                        image.getInternalBuffer(),
                        0,
                        image.size()) : null;

                NVGenericMap requestContent = new NVGenericMap();
                requestContent.build("model", param.getValue("model"));
                NVGenericMapList messages = new NVGenericMapList("messages");
                requestContent.build(messages);
                NVGenericMap imageAnalysis = new NVGenericMap();
                messages.add(imageAnalysis);
                imageAnalysis.build("role", "user");
                NVGenericMapList content = new NVGenericMapList("content");
                imageAnalysis.add(content);
                content.add(new NVGenericMap().build("type", "text")
                        .build("text", param.getValue("prompt")));

                if (SUS.isNotEmpty(imageBase64))
                    content.add(new NVGenericMap().build("type", "image_url")
                                    .build(new NVGenericMap("image_url")
                                    .build("url", "data:image/" + param.getValue("image-type") + ";base64,{" + imageBase64 + "}")));


//                NVInt maxTokens = (NVInt) param.get("max-tokens");
//
//                if (maxTokens != null && maxTokens.getValue() > 200)
//                    requestContent.build(new NVInt("max_tokens", maxTokens.getValue()));
                // model o1-mini used max_completion_tokens instead of max-tokens
                hmci.setContent(GSONUtil.toJSONDefault(requestContent));
            }
            catch(Exception e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return hmci;
        });
        HTTPAPIManager.SINGLETON.register(completionEndPoint);
    }


    private void buildTextToSpeechEndPoint()
    {
        HTTPMessageConfigInterface textToSpeechHMCI = HTTPMessageConfig.createAndInit(GTP_URL, "v1/chat/completions", HTTPMethod.POST, true, HTTPMediaType.APPLICATION_JSON);
        //textToSpeechHMCI.setAccept(HTTPMediaType.APPLICATION_JSON);
        HTTPAPIEndPoint<NVGenericMap, byte[]> textToSpeechEndPoint = HTTPAPIManager.SINGLETON.buildEndPoint(Command.TEXT_TO_SPEECH, DOMAIN, "Analyze Image based on prompt", textToSpeechHMCI);
        textToSpeechEndPoint.setRateController(GPT_RC);

        textToSpeechEndPoint.setDataDecoder(hrd-> hrd.getData());
        if(log.isEnabled()) log.getLogger().info("Endpoint:" + textToSpeechEndPoint.toCanonicalID());
        textToSpeechEndPoint.setDataEncoder((hmci, param) ->
        {
            try
            {

//                UByteArrayOutputStream baos = new UByteArrayOutputStream();
//                ImageIO.write(image, "png", baos);

                NVStringList modalities = param.getNV("modalities");
                NVGenericMap audio = param.getNV("audio");




                NVGenericMap requestContent = new NVGenericMap();
                requestContent.build("model", param.getValue("model"));
                requestContent.build(audio);
                requestContent.build(modalities);
                NVGenericMapList messages = new NVGenericMapList("messages");
                requestContent.build(messages);
                NVGenericMap imageAnalysis = new NVGenericMap();
                messages.add(imageAnalysis);
                imageAnalysis.build("role", "user");
                NVGenericMapList content = new NVGenericMapList("content");
                imageAnalysis.add(content);
                content.add(new NVGenericMap().build("type", "text")
                        .build("text", param.getValue("prompt")));




                NVInt maxTokens = (NVInt) param.get("max-tokens");

                if (maxTokens != null && maxTokens.getValue() > 200)
                    requestContent.build(new NVInt("max_tokens", maxTokens.getValue()));

                hmci.setContent(GSONUtil.toJSONDefault(requestContent));
            }
            catch(Exception e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return hmci;
        });
        HTTPAPIManager.SINGLETON.register(textToSpeechEndPoint);
    }



    public NVGenericMap toPromptParams(String gptModel, String prompt, int maxTokens)
    {
        return toVisionParams(gptModel, prompt, maxTokens,  null, null);
    }



    public NVGenericMap toVisionParams(String gptModel, String prompt, int maxTokens, UByteArrayOutputStream image, String imageType)
    {
        NVGenericMap ret =  new NVGenericMap()
                .build("model", gptModel)
                .build("prompt", prompt)
                .build(new NVInt("max-tokens", maxTokens))
                ;

        if(image != null)
            ret.build(new NamedValue<UByteArrayOutputStream>("image", image)).build("image-type", imageType);

        return ret;
    }

    public HTTPAPICaller create(HTTPAuthorization authorization)
    {
        return HTTPAPIManager.SINGLETON.createAPICaller(DOMAIN, "default", authorization);
    }

}
