package br.com.fitfood;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(20, 125, 80);
    private static final int DARK_GREEN = Color.rgb(15, 88, 59);
    private static final int BACKGROUND = Color.rgb(244, 248, 244);
    private static final int TEXT = Color.rgb(23, 36, 29);
    private static final int MUTED = Color.rgb(91, 108, 97);
    private static final String[] GOALS = {
            "Ganhar massa muscular",
            "Perder gordura",
            "Manter o peso",
            "Melhorar o desempenho",
            "Ter uma alimentação equilibrada"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MealDatabase database;
    private MealDatabase.Account account;
    private LinearLayout content;
    private LinearLayout historyList;
    private LinearLayout analysisPanel;
    private TextView summary;
    private TextView status;
    private TextView calorieProgressLabel;
    private ProgressBar calorieProgress;
    private EditText analysisName;
    private EditText[] nutritionFields;
    private PlateAnalysis currentAnalysis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new MealDatabase(this);
        showAuthScreen(!database.hasAccounts());
    }

    private void showAuthScreen(boolean register) {
        createScreen("FitFood", "Sua alimentação, entendida pela foto.");
        TextView welcome = text(
                "Analise o prato, revise a estimativa e acompanhe seu diário.",
                16, MUTED, false);
        content.addView(welcome, margins(0, 4, 0, 18));

        LinearLayout card = card();
        content.addView(card, margins(0, 10, 0, 12));
        addHeading(card, register ? "Crie sua conta" : "Que bom ter você de volta", 22);
        addBody(card, "O email e a senha ficam salvos somente neste celular.", 14);

        EditText email = field(card, "Email", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setSingleLine(true);
        EditText password = field(card, "Senha (mínimo 8 caracteres)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);

        addButton(card, register ? "Criar conta" : "Entrar", true, () -> {
            char[] passwordChars = password.getText().toString().toCharArray();
            try {
                if (register) {
                    account = database.createAccount(email.getText().toString(), passwordChars);
                    showGoalScreen();
                } else {
                    account = database.authenticate(email.getText().toString(), passwordChars);
                    if (account == null) {
                        password.setError("Email ou senha incorretos.");
                        return;
                    }
                    if (!account.goalCompleted) {
                        showGoalScreen();
                    } else {
                        showMainScreen();
                    }
                }
            } catch (IllegalArgumentException exception) {
                if (exception.getMessage() != null
                        && exception.getMessage().toLowerCase(Locale.ROOT).contains("senha")) {
                    password.setError(exception.getMessage());
                } else {
                    email.setError(exception.getMessage());
                }
            } catch (RuntimeException exception) {
                Toast.makeText(this, "Não foi possível acessar a conta.",
                        Toast.LENGTH_LONG).show();
            } finally {
                java.util.Arrays.fill(passwordChars, '\0');
            }
        });

        if (database.hasAccounts()) {
            String alternate = register ? "Já tenho uma conta" : "Criar uma conta";
            addButton(card, alternate, false, () -> showAuthScreen(!register));
        }
        addBody(content,
                "As contas não são verificadas nem sincronizadas. O login é local e não "
                        + "substitui a proteção do próprio celular.",
                12);
    }

    private void showGoalScreen() {
        createScreen("Seu objetivo", "Escolha uma direção para as recomendações gerais.");
        TextView explanation = text(
                "Isso ajuda a contextualizar as sugestões da análise. Não é uma prescrição "
                        + "de dieta ou meta de saúde.",
                15, MUTED, false);
        content.addView(explanation, margins(0, 6, 0, 14));

        for (String goal : GOALS) {
            LinearLayout option = card();
            content.addView(option, margins(0, 6, 0, 6));
            addHeading(option, goal, 17);
            addBody(option, "Toque para selecionar", 13);
            option.setOnClickListener(view -> {
                account = database.updateGoal(account.id, goal);
                if (account.hasCalorieProfile()) {
                    showMainScreen();
                } else {
                    showCalorieProfileScreen();
                }
            });
        }
        addButton(content, "Sair da conta", false, this::signOut);
    }

    private void showMainScreen() {
        createScreen("Olá, " + firstName(account.email), "Análise de refeições por foto");
        addBody(content, account.email, 13);

        LinearLayout goalCard = card();
        content.addView(goalCard, margins(0, 14, 0, 8));
        addBody(goalCard, "SEU OBJETIVO", 12);
        addHeading(goalCard, account.goal, 17);
        addButton(goalCard, "Alterar objetivo", false, this::showGoalScreen);
        addButton(goalCard, account.hasCalorieProfile()
                ? "Atualizar dados da meta calórica"
                : "Configurar meta calórica", false, this::showCalorieProfileScreen);
        if (account.hasCalorieProfile()) {
            addBody(goalCard, String.format(Locale.getDefault(),
                    "Meta diária estimada: %d kcal", currentCalorieTarget()), 14);
        } else {
            addBody(goalCard, "Meta diária não configurada.", 14);
        }

        LinearLayout photoCard = card();
        content.addView(photoCard, margins(0, 10, 0, 8));
        addHeading(photoCard, "Analise seu prato", 21);
        addBody(photoCard,
                "Escolha uma foto JPG, PNG ou WEBP de até 10 MB. A IA estima os alimentos "
                        + "e nutrientes; você revisa antes de salvar.",
                14);
        addButton(photoCard, "Escolher foto do prato", true, this::choosePhoto);
        addButton(photoCard, "Configurar chave de IA", false, this::showApiKeyDialog);
        addBody(photoCard,
                "A foto é enviada à API da OpenAI para análise e não é salva no diário. "
                        + "O uso da API pode gerar cobrança na sua conta.",
                12);

        status = text("", 14, MUTED, false);
        content.addView(status, margins(0, 8, 0, 4));

        analysisPanel = new LinearLayout(this);
        analysisPanel.setOrientation(LinearLayout.VERTICAL);
        content.addView(analysisPanel);

        LinearLayout historyCard = card();
        content.addView(historyCard, margins(0, 12, 0, 10));
        addHeading(historyCard, "Seu diário de hoje", 20);
        summary = text("", 14, TEXT, true);
        historyCard.addView(summary, margins(0, 4, 0, 8));
        calorieProgressLabel = text("", 13, MUTED, false);
        historyCard.addView(calorieProgressLabel, margins(0, 2, 0, 2));
        calorieProgress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        calorieProgress.setMax(100);
        historyCard.addView(calorieProgress, margins(0, 2, 0, 8));
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyCard.addView(historyList);

        addButton(content, "Sair da conta", false, this::signOut);
        addBody(content,
                "As estimativas são aproximadas e educativas, não substituem orientação "
                        + "de um nutricionista.",
                12);
        refreshMeals();
    }

    private void showCalorieProfileScreen() {
        createScreen("Meta calórica", "Uma referência diária estimada para o seu objetivo.");
        addBody(content,
                "A estimativa usa idade, peso, altura, fórmula metabólica e atividade. "
                        + "Ganhar massa: cerca de +200 kcal; perder gordura: cerca de "
                        + "-200 kcal sobre a manutenção. Os outros objetivos usam a "
                        + "estimativa de manutenção.",
                14);
        addBody(content,
                "Escolha a fórmula que melhor se aplica ao seu corpo. Se tiver dúvida, "
                        + "pule o cálculo e converse com um profissional.",
                13);
        addBody(content,
                "Somente para adultos; não é prescrição médica. Se estiver grávida, tiver "
                        + "uma condição de saúde ou histórico de transtorno alimentar, "
                        + "converse com um nutricionista antes de usar. Estimativas abaixo "
                        + "de 1.200 kcal/dia não são exibidas. Os dados ficam "
                        + "salvos somente neste celular.",
                13);

        LinearLayout form = card();
        content.addView(form, margins(0, 12, 0, 10));
        EditText ageField = field(form, "Idade (18 a 100 anos)", InputType.TYPE_CLASS_NUMBER);
        EditText weightField = field(form, "Peso atual (kg)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText heightField = field(form, "Altura (cm)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (account.age != null) {
            ageField.setText(Integer.toString(account.age));
        }
        if (account.weightKg != null) {
            weightField.setText(formatNumber(account.weightKg));
        }
        if (account.heightCm != null) {
            heightField.setText(formatNumber(account.heightCm));
        }

        addBody(form, "Fórmula de referência", 13);
        Spinner formulaSpinner = spinner(new String[]{
                "Selecione a fórmula",
                "Feminina",
                "Masculina"
        });
        if (CalorieGoalCalculator.FEMALE_FORMULA.equals(account.formulaSex)) {
            formulaSpinner.setSelection(1);
        } else if (CalorieGoalCalculator.MALE_FORMULA.equals(account.formulaSex)) {
            formulaSpinner.setSelection(2);
        }
        form.addView(formulaSpinner, margins(0, 0, 0, 6));

        addBody(form, "Nível de atividade", 13);
        Spinner activitySpinner = spinner(new String[]{
                "Selecione o nível",
                "Sedentário: pouco ou nenhum exercício",
                "Leve: exercício leve 1 a 3 dias por semana",
                "Moderado: exercício 3 a 5 dias por semana",
                "Alto: exercício intenso 6 a 7 dias por semana"
        });
        if (account.activityLevel != null) {
            activitySpinner.setSelection(account.activityLevel + 1);
        }
        form.addView(activitySpinner, margins(0, 0, 0, 6));

        addButton(form, "Calcular e salvar meta", true, () -> {
            int age;
            double weight;
            double height;
            try {
                age = Integer.parseInt(ageField.getText().toString().trim());
                weight = parseNumber(weightField.getText().toString());
                height = parseNumber(heightField.getText().toString());
            } catch (NumberFormatException exception) {
                ageField.setError("Preencha idade, peso e altura.");
                return;
            }
            if (formulaSpinner.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Selecione uma fórmula metabólica.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (activitySpinner.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Selecione o nível de atividade.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String formula = formulaSpinner.getSelectedItemPosition() == 1
                    ? CalorieGoalCalculator.FEMALE_FORMULA
                    : CalorieGoalCalculator.MALE_FORMULA;
            int activityLevel = activitySpinner.getSelectedItemPosition() - 1;
            try {
                CalorieGoalCalculator.estimate(
                        age, weight, height, formula, activityLevel, account.goal);
                account = database.updateCalorieProfile(
                        account.id, age, weight, height, formula, activityLevel);
                showMainScreen();
                setStatus("Meta calórica estimada e salva neste celular.", false);
            } catch (IllegalArgumentException exception) {
                Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        addButton(form, "Continuar sem meta calórica", false, this::showMainScreen);
    }

    private Spinner spinner(String[] options) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void choosePhoto() {
        String apiKey;
        try {
            apiKey = SecureKeyStore.load(this);
        } catch (GeneralSecurityException exception) {
            setStatus("Não foi possível acessar a chave de IA. Configure-a novamente.", true);
            return;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showApiKeyDialog();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 1001 || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri imageUri = data.getData();
        String apiKey;
        try {
            apiKey = SecureKeyStore.load(this);
        } catch (GeneralSecurityException exception) {
            setStatus("Não foi possível acessar a chave de IA. Configure-a novamente.", true);
            return;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showApiKeyDialog();
            return;
        }

        final int requestAccountId = account.id;
        final String requestGoal = account.goal;
        setStatus("Analisando a foto. Isso pode levar alguns segundos...", false);
        executor.execute(() -> {
            try {
                PlateAnalysis analysis = PlateAIService.analyze(
                        getContentResolver(), imageUri, apiKey, requestGoal);
                runOnUiThread(() -> {
                    if (account != null && account.id == requestAccountId && !isFinishing()) {
                        showAnalysis(analysis);
                        setStatus("Confira e revise os valores antes de salvar.", false);
                    }
                });
            } catch (IOException | JSONException | SecurityException exception) {
                runOnUiThread(() -> {
                    if (account != null && account.id == requestAccountId && !isFinishing()) {
                        setStatus(exception.getMessage(), true);
                    }
                });
            }
        });
    }

    private void showAnalysis(PlateAnalysis analysis) {
        currentAnalysis = analysis;
        analysisPanel.removeAllViews();
        LinearLayout card = card();
        analysisPanel.addView(card, margins(0, 4, 0, 8));
        addHeading(card, "Revise a estimativa", 20);
        addBody(card, "Alimentos identificados: " + analysis.foods, 14);
        addBody(card, "Confiança " + analysis.confidence + " · " + analysis.uncertainty, 13);
        addBody(card, analysis.recommendation, 14);

        analysisName = field(card, "Nome do prato", InputType.TYPE_CLASS_TEXT);
        analysisName.setText(analysis.name);
        analysisName.setSingleLine(true);
        String[] labels = {
                "Porção estimada (g)",
                "Calorias estimadas (kcal)",
                "Proteínas (g)",
                "Carboidratos (g)",
                "Gorduras (g)"
        };
        double[] values = {
                analysis.portionGrams, analysis.calories, analysis.protein,
                analysis.carbs, analysis.fat
        };
        nutritionFields = new EditText[labels.length];
        for (int i = 0; i < labels.length; i++) {
            nutritionFields[i] = field(card, labels[i],
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                            | InputType.TYPE_NUMBER_FLAG_SIGNED);
            nutritionFields[i].setText(String.format(Locale.getDefault(), "%.1f", values[i]));
            nutritionFields[i].setSingleLine(true);
        }
        addButton(card, "Salvar no diário", true, this::saveAnalysis);
        addButton(card, "Descartar análise", false, () -> {
            currentAnalysis = null;
            analysisPanel.removeAllViews();
            setStatus("Análise descartada.", false);
        });
    }

    private void saveAnalysis() {
        if (currentAnalysis == null || analysisName == null || nutritionFields == null) {
            return;
        }
        String name = analysisName.getText().toString().trim();
        if (name.isEmpty() || name.length() > 120) {
            analysisName.setError("Informe um nome com até 120 caracteres.");
            return;
        }

        double[] values = new double[nutritionFields.length];
        for (int i = 0; i < nutritionFields.length; i++) {
            try {
                values[i] = Double.parseDouble(
                        nutritionFields[i].getText().toString().trim().replace(',', '.'));
            } catch (NumberFormatException exception) {
                nutritionFields[i].setError("Informe um número válido.");
                return;
            }
            if (Double.isNaN(values[i]) || Double.isInfinite(values[i])) {
                nutritionFields[i].setError("Informe um número válido.");
                return;
            }
        }
        if (values[0] <= 0 || values[1] < 0 || values[2] < 0
                || values[3] < 0 || values[4] < 0) {
            nutritionFields[0].setError("A porção deve ser positiva; os demais valores, não negativos.");
            return;
        }
        if (values[0] > 100000 || values[1] > 100000
                || values[2] > 10000 || values[3] > 10000 || values[4] > 10000) {
            Toast.makeText(this, "Um dos valores ultrapassa o limite permitido.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            database.addMeal(account.id, name, values[0], values[1], values[2],
                    values[3], values[4]);
            currentAnalysis = null;
            analysisPanel.removeAllViews();
            refreshMeals();
            setStatus("Refeição salva no seu diário.", false);
        } catch (RuntimeException exception) {
            setStatus("Não foi possível salvar a refeição.", true);
        }
    }

    private void showApiKeyDialog() {
        EditText keyField = new EditText(this);
        keyField.setSingleLine(true);
        keyField.setHint("Chave da API");
        keyField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView explanation = text(
                "A chave é criptografada neste celular e enviada diretamente à API da OpenAI. "
                        + "Não a compartilhe. O uso da API pode ter custos.",
                14, MUTED, false);
        dialogContent.addView(explanation, margins(0, 0, 0, 8));
        dialogContent.addView(keyField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configurar análise por IA")
                .setView(dialogContent)
                .setPositiveButton("Salvar chave", null)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Apagar chave", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String key = keyField.getText().toString().trim();
                if (key.isEmpty() || key.length() > 500) {
                    keyField.setError("Informe uma chave válida.");
                    return;
                }
                try {
                    SecureKeyStore.save(this, key);
                    dialog.dismiss();
                    setStatus("Chave salva com segurança neste celular.", false);
                } catch (GeneralSecurityException exception) {
                    keyField.setError("Não foi possível salvar a chave com segurança.");
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                try {
                    SecureKeyStore.delete(this);
                    dialog.dismiss();
                    setStatus("Chave removida deste celular.", false);
                } catch (GeneralSecurityException exception) {
                    keyField.setError("Não foi possível remover a chave.");
                }
            });
        });
        dialog.show();
    }

    private void refreshMeals() {
        List<MealDatabase.Meal> meals = database.getTodayMeals(account.id);
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
                "%d refeições · %.1f kcal · P %.1f g · C %.1f g · G %.1f g",
                meals.size(), calories, protein, carbs, fat));
        if (account.hasCalorieProfile()) {
            int target = currentCalorieTarget();
            calorieProgressLabel.setText(String.format(Locale.getDefault(),
                    "%.0f / %d kcal estimadas (%d%%)",
                    calories, target, Math.min(100, Math.round(calories * 100 / target))));
            calorieProgress.setProgress((int) Math.min(100, Math.round(calories * 100 / target)));
        } else {
            calorieProgressLabel.setText("Configure uma meta para acompanhar seu consumo.");
            calorieProgress.setProgress(0);
        }
        historyList.removeAllViews();
        if (meals.isEmpty()) {
            addBody(historyList, "Nenhuma refeição registrada hoje.", 14);
            return;
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (MealDatabase.Meal meal : meals) {
            TextView item = text(String.format(Locale.getDefault(),
                    "%s  ·  %s (%s g)\n%.1f kcal  |  P %.1f g  C %.1f g  G %.1f g",
                    timeFormat.format(new Date(meal.eatenAt)), meal.name,
                    formatNumber(meal.amount), meal.calories, meal.protein,
                    meal.carbs, meal.fat), 14, TEXT, false);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackground(rounded(Color.WHITE, 12));
            historyList.addView(item, margins(0, 4, 0, 4));
        }
    }

    private void signOut() {
        account = null;
        currentAnalysis = null;
        showAuthScreen(database.hasAccounts() ? false : true);
    }

    private void createScreen(String title, String subtitle) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        content.setFitsSystemWindows(true);
        scrollView.addView(content);
        setContentView(scrollView);

        TextView heading = text(title, 30, DARK_GREEN, true);
        content.addView(heading);
        TextView subheading = text(subtitle, 16, MUTED, false);
        content.addView(subheading, margins(0, 2, 0, 8));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(Color.WHITE, 18));
        return card;
    }

    private EditText field(LinearLayout parent, String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setTextColor(TEXT);
        editText.setHintTextColor(MUTED);
        parent.addView(editText, margins(0, 4, 0, 4));
        return editText;
    }

    private void addHeading(LinearLayout parent, String value, int size) {
        parent.addView(text(value, size, DARK_GREEN, true), margins(0, 0, 0, 6));
    }

    private void addBody(LinearLayout parent, String value, int size) {
        parent.addView(text(value, size, MUTED, false), margins(0, 2, 0, 6));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private void addButton(LinearLayout parent, String label, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : Color.WHITE));
        button.setOnClickListener(view -> action.run());
        parent.addView(button, margins(0, 6, 0, 4));
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private void setStatus(String message, boolean error) {
        if (status != null) {
            status.setText(message == null ? "Não foi possível concluir a operação." : message);
            status.setTextColor(error ? Color.rgb(168, 50, 50) : MUTED);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private String firstName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.getDefault(), "%.0f", value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private double parseNumber(String value) {
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    private int currentCalorieTarget() {
        return CalorieGoalCalculator.estimate(
                account.age,
                account.weightKg,
                account.heightCm,
                account.formulaSex,
                account.activityLevel,
                account.goal);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }
}
