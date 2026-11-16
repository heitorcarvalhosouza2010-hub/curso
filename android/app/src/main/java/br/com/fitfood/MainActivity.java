package br.com.fitfood;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(20, 125, 80);
    private static final int BACKGROUND = Color.rgb(244, 248, 244);
    private static final int TEXT = Color.rgb(23, 36, 29);

    private final EditText[] fields = new EditText[6];
    private MealDatabase database;
    private TextView summary;
    private LinearLayout mealList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new MealDatabase(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.setFitsSystemWindows(true);
        scrollView.addView(content);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("FitFood");
        title.setTextColor(GREEN);
        title.setTextSize(30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Registre suas refeições e acompanhe os macros de hoje.");
        intro.setTextColor(TEXT);
        intro.setTextSize(16);
        content.addView(intro, marginParams(0, 0, 0, 18));

        String[] labels = {
                "Alimento ou refeição",
                "Porção (g)",
                "Calorias (kcal)",
                "Proteínas (g)",
                "Carboidratos (g)",
                "Gorduras (g)"
        };
        for (int i = 0; i < labels.length; i++) {
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextColor(TEXT);
            label.setTextSize(14);
            content.addView(label, marginParams(0, 8, 0, 2));

            EditText field = new EditText(this);
            field.setSingleLine(true);
            field.setHint(labels[i]);
            if (i > 0) {
                field.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else {
                field.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            }
            fields[i] = field;
            content.addView(field, marginParams(0, 0, 0, 4));
        }

        Button addButton = new Button(this);
        addButton.setText("Adicionar refeição");
        addButton.setAllCaps(false);
        addButton.setTextColor(Color.WHITE);
        addButton.setBackgroundTintList(ColorStateList.valueOf(GREEN));
        addButton.setOnClickListener(view -> addMeal());
        content.addView(addButton, marginParams(0, 14, 0, 12));

        summary = new TextView(this);
        summary.setTextColor(TEXT);
        summary.setTextSize(17);
        summary.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(summary, marginParams(0, 12, 0, 8));

        TextView mealsTitle = new TextView(this);
        mealsTitle.setText("Refeições de hoje");
        mealsTitle.setTextColor(GREEN);
        mealsTitle.setTextSize(20);
        mealsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(mealsTitle, marginParams(0, 12, 0, 4));

        mealList = new LinearLayout(this);
        mealList.setOrientation(LinearLayout.VERTICAL);
        content.addView(mealList);

        TextView notice = new TextView(this);
        notice.setText("Os dados ficam salvos neste celular. Valores informados e ideias gerais não substituem orientação de um profissional.");
        notice.setTextColor(TEXT);
        notice.setTextSize(13);
        content.addView(notice, marginParams(0, 20, 0, 4));

        refreshMeals();
    }

    private void addMeal() {
        String name = fields[0].getText().toString().trim();
        if (name.isEmpty() || name.length() > 120) {
            fields[0].setError("Informe um nome com até 120 caracteres.");
            return;
        }

        double[] values = new double[5];
        for (int i = 1; i < fields.length; i++) {
            String text = fields[i].getText().toString().trim().replace(',', '.');
            try {
                values[i - 1] = Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                fields[i].setError("Informe um número válido.");
                return;
            }
            if (Double.isNaN(values[i - 1]) || Double.isInfinite(values[i - 1])) {
                fields[i].setError("Informe um número finito.");
                return;
            }
        }

        if (values[0] <= 0 || values[1] < 0 || values[2] < 0
                || values[3] < 0 || values[4] < 0) {
            fields[1].setError("A porção deve ser positiva; os demais valores, não negativos.");
            return;
        }
        if (values[0] > 100000 || values[1] > 100000
                || values[2] > 10000 || values[3] > 10000 || values[4] > 10000) {
            Toast.makeText(this, "Um dos valores ultrapassa o limite permitido.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            database.addMeal(name, values[0], values[1], values[2], values[3], values[4]);
        } catch (SQLiteException exception) {
            Toast.makeText(this, "Não foi possível salvar a refeição.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        for (EditText field : fields) {
            field.setText("");
        }
        refreshMeals();
        Toast.makeText(this, "Refeição salva.", Toast.LENGTH_SHORT).show();
    }

    private void refreshMeals() {
        List<MealDatabase.Meal> meals = database.getTodayMeals();
        double calories = 0;
        double protein = 0;
        double carbs = 0;
        double fat = 0;
        for (MealDatabase.Meal meal : meals) {
            calories += meal.calories;
            protein += meal.protein;
            carbs += meal.carbs;
            fat += meal.fat;
        }
        summary.setText(String.format(Locale.getDefault(),
                "Hoje: %d refeições | %.1f kcal | P %.1f g | C %.1f g | G %.1f g",
                meals.size(), calories, protein, carbs, fat));

        mealList.removeAllViews();
        if (meals.isEmpty()) {
            addMealText("Nenhuma refeição registrada hoje.");
            return;
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (MealDatabase.Meal meal : meals) {
            String text = String.format(Locale.getDefault(),
                    "%s - %s (%s g): %.1f kcal | P %.1f g | C %.1f g | G %.1f g",
                    timeFormat.format(new Date(meal.eatenAt)), meal.name,
                    formatNumber(meal.amount), meal.calories, meal.protein, meal.carbs, meal.fat);
            addMealText(text);
        }
    }

    private void addMealText(String text) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(TEXT);
        item.setTextSize(15);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        mealList.addView(item, marginParams(0, 3, 0, 3));
    }

    private String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.getDefault(), "%.0f", value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }
}
