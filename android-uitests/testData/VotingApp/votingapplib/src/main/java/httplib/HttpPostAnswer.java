package httplib;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by axap on 3/12/18.
 */

public class HttpPostAnswer {
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    public String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        System.out.println("Request body : " +request);
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
        catch (Exception e){
            System.out.print(e);
        }
        return null;
    }
}
