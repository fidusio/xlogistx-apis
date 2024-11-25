package io.xlogistx.api.gpt;

import io.xlogistx.common.image.ImageUtil;
import org.zoxweb.server.http.HTTPAPICaller;
import org.zoxweb.server.io.IOUtil;
import org.zoxweb.server.io.UByteArrayOutputStream;
import org.zoxweb.shared.http.HTTPAuthScheme;
import org.zoxweb.shared.http.HTTPAuthorization;
import org.zoxweb.shared.util.NVGenericMap;
import org.zoxweb.shared.util.NamedValue;
import org.zoxweb.shared.util.ParamUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class ChatGPT
{
    public static void main(String ...args)
    {
        try
        {
            ParamUtil.ParamMap params = ParamUtil.parse("=", args);
            String gptAPIKey = params.stringValue("gpt-key");

            GPTAPI.Command command = params.enumValue("command", GPTAPI.Command.values());
            HTTPAPICaller apiCaller = GPTAPI.SINGLETON.create(new HTTPAuthorization(HTTPAuthScheme.BEARER, gptAPIKey));
            NVGenericMap response = null;
            switch (command)
            {
                case COMPLETION:
                    String prompt = params.stringValue("prompt");
                    String gptModel = params.stringValue("model");
                    String imageUrl =  params.stringValue("image-url", true);
                    NVGenericMap completion =null;
                    if (imageUrl != null)
                    {
                        String imageType = ImageUtil.getImageFormat(imageUrl);
                        UByteArrayOutputStream imageBAOS = IOUtil.inputStreamToByteArray(new FileInputStream(imageType), true);
                        completion = GPTAPI.SINGLETON.toVisionParams(gptModel, prompt, 0, imageBAOS, imageType);
                    }
                    else
                        completion = GPTAPI.SINGLETON.toPromptParams(gptModel, prompt, 0);

                    response = apiCaller.syncCall(command, completion);
                    System.out.println(command + "\n" + response);

                    break;
                case TRANSCRIBE:
                    File file = new File(params.stringValue("file"));
                    if(!file.exists())
                        throw new FileNotFoundException(file.getName());

                    NamedValue<File> param = new NamedValue<File>();
                    param.setName(file.getName());
                    param.setValue(file);
                    param.getProperties().build("model",  params.stringValue("model", "whisper-1"));

                    response = apiCaller.syncCall(command, param);
                    System.out.println(command + "\n" + response);
                    break;
            }


        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
