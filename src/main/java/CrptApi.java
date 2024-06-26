import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

public class CrptApi {
    private final Semaphore semaphore;
    private final OkHttpClient client;
    private final Gson gson;
    private Instant lastAccessTime;

    public CrptApi(int requestLimit) {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.semaphore = new Semaphore(requestLimit);
        this.lastAccessTime = Instant.now();
    }

    public void createDocument(Document document) throws IOException {
        checkAndWaitForNextRequestAllowed();

        String json = gson.toJson(document);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            System.out.println("Запрос к API выполнен. Ответ: " + response.body().string());
        }
    }

    private void checkAndWaitForNextRequestAllowed() throws IOException {
        long now = Instant.now().toEpochMilli();
        if (now - lastAccessTime.toEpochMilli() > Duration.ofSeconds(1).toMillis()) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lastAccessTime = Instant.now();
        } else {
            try {
                semaphore.acquire((int) Duration.ofSeconds(1).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Ошибка при ожидании разрешения на следующий запрос", e);
            }
        }
    }

    public static class Document {
     }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(5);
        Document document = new Document();

        try {
            api.createDocument(document);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

