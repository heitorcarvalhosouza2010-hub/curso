package br.com.fitfood;

import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class PlateAIService {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private PlateAIService() {
    }

    static PlateAnalysis analyze(
            ContentResolver resolver, Uri imageUri, String apiKey, String goal)
            throws IOException, JSONException {
        String mimeType = resolver.getType(imageUri);
        if (mimeType != null) {
            mimeType = mimeType.toLowerCase(Locale.ROOT);
        }
        if (mimeType == null || (!mimeType.equals("image/jpeg")
                && !mimeType.equals("image/png") && !mimeType.equals("image/webp"))) {
            throw new IOException("Escolha uma foto JPG, PNG ou WEBP.");
        }
        byte[] image = readImage(resolver, imageUri);
        String imageUrl = "data:" + mimeType + ";base64,"
                + android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP);

        JSONObject requestBody = createRequest(imageUrl, goal);
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            byte[] body = requestBody.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException(errorForStatus(status));
            }
            JSONObject response = new JSONObject(readText(connection.getInputStream()));
            String content = response.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim();
            if (content.startsWith("```")) {
                content = content.replaceFirst("^```(?:json)?\\s*", "")
                        .replaceFirst("\\s*```$", "");
            }
            return PlateAnalysis.fromJson(new JSONObject(content));
        } catch (JSONException exception) {
            throw new IOException("O serviço de IA retornou uma análise inválida.", exception);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readImage(ContentResolver resolver, Uri imageUri) throws IOException {
        try (InputStream input = resolver.openInputStream(imageUri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IOException("Não foi possível abrir a foto selecionada.");
            }
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("A foto deve ter até 10 MB.");
                }
                output.write(buffer, 0, count);
            }
            if (total == 0) {
                throw new IOException("A foto selecionada está vazia.");
            }
            return output.toByteArray();
        }
    }

    private static JSONObject createRequest(String imageUrl, String goal)
            throws JSONException {
        String systemPrompt = "Você analisa fotos de refeições e responde em português. "
                + "Faça estimativas visuais, nunca afirme exatidão. Não invente ingredientes "
                + "invisíveis; indique incertezas e use confiança baixa quando necessário. "
                + "Responda somente JSON válido com as chaves dish_name (texto até 120 "
                + "caracteres), foods (lista de nomes visíveis), portion_g, calories, "
                + "protein_g, carbs_g, fat_g (números não negativos), confidence (baixa, "
                + "média ou alta), uncertainty e recommendation. A recomendação é geral, "
                + "educativa e breve; não prescreva dietas, metas calóricas ou tratamentos.";
        String userPrompt = "Analise a refeição da foto. Estime alimentos visíveis, porção "
                + "total em gramas, calorias, proteínas, carboidratos e gorduras. Objetivo "
                + "geral informado: " + goal + ". Considere as limitações da estimativa.";

        JSONArray content = new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", userPrompt))
                .put(new JSONObject().put("type", "image_url").put(
                        "image_url", new JSONObject().put("url", imageUrl).put("detail", "high")));
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                .put(new JSONObject().put("role", "user").put("content", content));
        return new JSONObject().put("model", MODEL).put("temperature", 0.2)
                .put("messages", messages);
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private static String errorForStatus(int status) {
        if (status == 401) {
            return "A chave de IA foi recusada. Confira a chave salva no app.";
        }
        if (status == 429) {
            return "Limite da API atingido. Confira o uso e o faturamento da sua conta.";
        }
        return String.format(Locale.getDefault(),
                "A API de IA retornou HTTP %d. Tente novamente mais tarde.", status);
    }
}
