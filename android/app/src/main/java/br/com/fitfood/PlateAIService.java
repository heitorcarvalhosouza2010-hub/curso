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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class PlateAIService {
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-3.5-flash-lite:generateContent";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_CHAT_MESSAGES = 20;

    private PlateAIService() {
    }

    static PlateAnalysis analyze(
            ContentResolver resolver, Uri imageUri, String apiKey, String goal)
            throws IOException, JSONException {
        ImageData image = readImage(resolver, imageUri);
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text",
                        "Analise a refeição da foto. Estime os alimentos visíveis, a porção "
                                + "total em gramas, calorias, proteínas, carboidratos e "
                                + "gorduras. Objetivo geral: " + goal + ". Considere as "
                                + "limitações e incertezas da estimativa."))
                .put(image.toGeminiPart());
        String systemPrompt = "Você analisa fotos de refeições e responde em português. "
                + "Faça estimativas visuais, nunca afirme exatidão. Não invente ingredientes "
                + "invisíveis; indique incertezas e use confiança baixa quando necessário. "
                + "Responda somente JSON válido com as chaves dish_name (texto até 120 "
                + "caracteres), foods (lista de nomes visíveis), portion_g, calories, "
                + "protein_g, carbs_g, fat_g (números não negativos), confidence (baixa, "
                + "média ou alta), uncertainty e recommendation. A recomendação é geral, "
                + "educativa e breve; não prescreva dietas, metas calóricas ou tratamentos.";
        JSONObject request = requestBody(systemPrompt, parts, true);
        String responseText = generateContent(request, apiKey);
        try {
            return PlateAnalysis.fromJson(new JSONObject(responseText));
        } catch (JSONException exception) {
            throw new IOException("O serviço de IA retornou uma análise inválida.", exception);
        }
    }

    static String chat(
            ContentResolver resolver,
            List<ChatTurn> messages,
            Uri imageUri,
            String apiKey,
            String goal) throws IOException, JSONException {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Escreva uma mensagem ou escolha uma foto.");
        }
        JSONArray contents = new JSONArray();
        int first = Math.max(0, messages.size() - MAX_CHAT_MESSAGES);
        while (first < messages.size() - 1 && !messages.get(first).user) {
            first++;
        }
        for (int i = first; i < messages.size(); i++) {
            ChatTurn message = messages.get(i);
            JSONArray parts = new JSONArray()
                    .put(new JSONObject().put("text", message.text));
            if (imageUri != null && i == messages.size() - 1 && message.user) {
                parts.put(readImage(resolver, imageUri).toGeminiPart());
            }
            contents.put(new JSONObject()
                    .put("role", message.user ? "user" : "model")
                    .put("parts", parts));
        }
        String systemPrompt = "Você é o chatbot do FitFood, responde em português e é "
                + "acolhedor e objetivo. Ajude com informações gerais e educativas sobre "
                + "alimentação. Para fotos, descreva o que parece visível e ressalte que "
                + "porções e nutrientes são estimativas. O objetivo da pessoa é: " + goal
                + ". Não diagnostique, não prescreva dietas, metas calóricas ou tratamentos. "
                + "Não dê orientação individualizada de saúde; recomende um nutricionista "
                + "para necessidades pessoais.";
        return generateContent(requestBody(systemPrompt, contents, false), apiKey);
    }

    private static JSONObject requestBody(
            String systemPrompt, JSONArray contents, boolean jsonResponse)
            throws JSONException {
        JSONObject generationConfig = new JSONObject().put("temperature", jsonResponse ? 0.2 : 0.6)
                .put("maxOutputTokens", jsonResponse ? 1024 : 512);
        if (jsonResponse) {
            generationConfig.put("responseMimeType", "application/json");
        }
        return new JSONObject()
                .put("systemInstruction", new JSONObject().put(
                        "parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .put("generationConfig", generationConfig);
    }

    private static String generateContent(JSONObject request, String apiKey)
            throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("x-goog-api-key", apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException(errorForStatus(status));
            }
            JSONObject response = new JSONObject(readText(connection.getInputStream()));
            JSONArray parts = response.getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content").getJSONArray("parts");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) {
                    if (result.length() > 0) {
                        result.append('\n');
                    }
                    result.append(part.getString("text"));
                }
            }
            String text = result.toString().trim();
            if (text.isEmpty()) {
                throw new IOException("O chatbot não retornou uma resposta.");
            }
            return text;
        } catch (JSONException exception) {
            throw new IOException("O serviço Gemini retornou uma resposta inválida.", exception);
        } finally {
            connection.disconnect();
        }
    }

    private static ImageData readImage(ContentResolver resolver, Uri imageUri)
            throws IOException {
        String mimeType = resolver.getType(imageUri);
        if (mimeType != null) {
            mimeType = mimeType.toLowerCase(Locale.ROOT);
        }
        if (mimeType == null || (!mimeType.equals("image/jpeg")
                && !mimeType.equals("image/png") && !mimeType.equals("image/webp"))) {
            throw new IOException("Escolha uma foto JPG, PNG ou WEBP.");
        }
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
            return new ImageData(mimeType, output.toByteArray());
        }
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private static String errorForStatus(int status) {
        if (status == 429) {
            return "A cota gratuita do Gemini foi atingida. Aguarde e tente novamente.";
        }
        if (status == 401 || status == 403) {
            return "A chave Gemini foi recusada. Confira sua chave do Google AI Studio.";
        }
        return String.format(Locale.getDefault(),
                "A API Gemini retornou HTTP %d. Tente novamente mais tarde.", status);
    }

    private static final class ImageData {
        final String mimeType;
        final byte[] bytes;

        ImageData(String mimeType, byte[] bytes) {
            this.mimeType = mimeType;
            this.bytes = bytes;
        }

        JSONObject toGeminiPart() throws JSONException {
            return new JSONObject().put("inlineData", new JSONObject()
                    .put("mimeType", mimeType)
                    .put("data", android.util.Base64.encodeToString(
                            bytes, android.util.Base64.NO_WRAP)));
        }
    }
}
