package br.com.fitfood;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class PlateAnalysis {
    final String name;
    final String foods;
    final double portionGrams;
    final double calories;
    final double protein;
    final double carbs;
    final double fat;
    final String confidence;
    final String uncertainty;
    final String recommendation;

    private PlateAnalysis(JSONObject value) throws JSONException {
        name = requiredText(value, "dish_name", 120);
        JSONArray foodArray = value.getJSONArray("foods");
        if (foodArray.length() < 1 || foodArray.length() > 15) {
            throw new JSONException("A lista de alimentos é inválida.");
        }
        StringBuilder foodText = new StringBuilder();
        for (int i = 0; i < foodArray.length(); i++) {
            String food = foodArray.getString(i).trim();
            if (food.isEmpty() || food.length() > 80) {
                throw new JSONException("A lista de alimentos é inválida.");
            }
            if (foodText.length() > 0) {
                foodText.append(", ");
            }
            foodText.append(food);
        }
        foods = foodText.toString();
        portionGrams = requiredNumber(value, "portion_g", 0.1, 100000);
        calories = requiredNumber(value, "calories", 0, 100000);
        protein = requiredNumber(value, "protein_g", 0, 10000);
        carbs = requiredNumber(value, "carbs_g", 0, 10000);
        fat = requiredNumber(value, "fat_g", 0, 10000);
        confidence = requiredText(value, "confidence", 10);
        if (!confidence.equals("baixa") && !confidence.equals("média")
                && !confidence.equals("alta")) {
            throw new JSONException("O nível de confiança é inválido.");
        }
        uncertainty = requiredText(value, "uncertainty", 1000);
        recommendation = requiredText(value, "recommendation", 2000);
    }

    static PlateAnalysis fromJson(JSONObject value) throws JSONException {
        return new PlateAnalysis(value);
    }

    private static String requiredText(JSONObject value, String key, int maxLength)
            throws JSONException {
        String text = value.getString(key).trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new JSONException("A resposta do serviço de IA é inválida.");
        }
        return text;
    }

    private static double requiredNumber(
            JSONObject value, String key, double minimum, double maximum) throws JSONException {
        double number = value.getDouble(key);
        if (Double.isNaN(number) || Double.isInfinite(number)
                || number < minimum || number > maximum) {
            throw new JSONException("A resposta do serviço de IA é inválida.");
        }
        return number;
    }
}
