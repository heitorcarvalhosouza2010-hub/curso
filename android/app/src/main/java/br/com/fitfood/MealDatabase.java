package br.com.fitfood;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class MealDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "fitfood.db";
    private static final int DATABASE_VERSION = 3;
    private static final int PASSWORD_ITERATIONS = 600000;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}$");

    MealDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        createAccountsTable(database);
        createMealsTable(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createAccountsTable(database);
            database.execSQL("ALTER TABLE meals ADD COLUMN account_id INTEGER "
                    + "REFERENCES accounts(id) ON DELETE CASCADE");
        }
        if (oldVersion < 3) {
            addColumnIfMissing(database, "age", "INTEGER");
            addColumnIfMissing(database, "weight_kg", "REAL");
            addColumnIfMissing(database, "height_cm", "REAL");
            addColumnIfMissing(database, "formula_sex", "TEXT");
            addColumnIfMissing(database, "activity_level", "INTEGER");
        }
    }

    private void createAccountsTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS accounts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "email TEXT NOT NULL UNIQUE, "
                + "password_salt TEXT NOT NULL, "
                + "password_hash TEXT NOT NULL, "
                + "goal TEXT NOT NULL DEFAULT '', "
                + "goal_completed INTEGER NOT NULL DEFAULT 0, "
                + "age INTEGER, "
                + "weight_kg REAL, "
                + "height_cm REAL, "
                + "formula_sex TEXT, "
                + "activity_level INTEGER)");
    }

    private void createMealsTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE meals ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "amount_g REAL NOT NULL, "
                + "calories REAL NOT NULL, "
                + "protein_g REAL NOT NULL, "
                + "carbs_g REAL NOT NULL, "
                + "fat_g REAL NOT NULL, "
                + "eaten_at INTEGER NOT NULL, "
                + "account_id INTEGER REFERENCES accounts(id) ON DELETE CASCADE)");
        database.execSQL("CREATE INDEX ix_meals_account_id ON meals(account_id)");
    }

    private void addColumnIfMissing(SQLiteDatabase database, String name, String type) {
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(accounts)", null)) {
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(1))) {
                    return;
                }
            }
        }
        database.execSQL("ALTER TABLE accounts ADD COLUMN " + name + " " + type);
    }

    boolean hasAccounts() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM accounts LIMIT 1", null)) {
            return cursor.moveToFirst();
        }
    }

    Account createAccount(String email, char[] password) {
        String cleanedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(cleanedEmail).matches() || cleanedEmail.length() > 254) {
            throw new IllegalArgumentException("Informe um email válido.");
        }
        if (password.length < 8) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = toHex(salt);
        String passwordHash = passwordDigest(password, salt);
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            boolean firstAccount;
            try (Cursor cursor = database.rawQuery(
                    "SELECT 1 FROM accounts LIMIT 1", null)) {
                firstAccount = !cursor.moveToFirst();
            }
            try (Cursor cursor = database.rawQuery(
                    "SELECT 1 FROM accounts WHERE email = ? LIMIT 1",
                    new String[]{cleanedEmail})) {
                if (cursor.moveToFirst()) {
                    throw new IllegalArgumentException("Este email já está cadastrado.");
                }
            }
            ContentValues values = new ContentValues();
            values.put("email", cleanedEmail);
            values.put("password_salt", saltHex);
            values.put("password_hash", passwordHash);
            values.put("goal", "");
            values.put("goal_completed", 0);
            long id = database.insertOrThrow("accounts", null, values);
            if (firstAccount) {
                ContentValues owner = new ContentValues();
                owner.put("account_id", id);
                database.update("meals", owner, "account_id IS NULL", null);
            }
            database.setTransactionSuccessful();
            return new Account((int) id, cleanedEmail, "", false,
                    null, null, null, null, null);
        } finally {
            database.endTransaction();
        }
    }

    Account authenticate(String email, char[] password) {
        String cleanedEmail = email.trim().toLowerCase(Locale.ROOT);
        try (Cursor cursor = getReadableDatabase().query(
                "accounts",
                new String[]{"id", "email", "password_salt", "password_hash",
                        "goal", "goal_completed", "age", "weight_kg", "height_cm",
                        "formula_sex", "activity_level"},
                "email = ?",
                new String[]{cleanedEmail},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            byte[] salt = fromHex(cursor.getString(2));
            byte[] candidate = fromHex(passwordDigest(password, salt));
            byte[] stored = fromHex(cursor.getString(3));
            if (!MessageDigest.isEqual(candidate, stored)) {
                return null;
            }
            return accountFromCursor(cursor);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    Account updateGoal(int accountId, String goal) {
        ContentValues values = new ContentValues();
        values.put("goal", goal);
        values.put("goal_completed", 1);
        int updated = getWritableDatabase().update(
                "accounts", values, "id = ?", new String[]{Integer.toString(accountId)});
        if (updated != 1) {
            throw new IllegalStateException("A conta não foi encontrada.");
        }
        return getAccountById(accountId);
    }

    Account updateCalorieProfile(
            int accountId,
            int age,
            double weightKg,
            double heightCm,
            String formulaSex,
            int activityLevel) {
        ContentValues values = new ContentValues();
        values.put("age", age);
        values.put("weight_kg", weightKg);
        values.put("height_cm", heightCm);
        values.put("formula_sex", formulaSex);
        values.put("activity_level", activityLevel);
        int updated = getWritableDatabase().update(
                "accounts", values, "id = ?", new String[]{Integer.toString(accountId)});
        if (updated != 1) {
            throw new IllegalStateException("A conta não foi encontrada.");
        }
        return getAccountById(accountId);
    }

    private Account getAccountById(int accountId) {
        try (Cursor cursor = getReadableDatabase().query(
                "accounts",
                new String[]{"id", "email", "goal", "goal_completed", "age",
                        "weight_kg", "height_cm", "formula_sex", "activity_level"},
                "id = ?",
                new String[]{Integer.toString(accountId)},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("A conta não foi encontrada.");
            }
            return accountFromCursor(cursor);
        }
    }

    private Account accountFromCursor(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        String email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
        String goal = cursor.getString(cursor.getColumnIndexOrThrow("goal"));
        boolean goalCompleted = cursor.getInt(
                cursor.getColumnIndexOrThrow("goal_completed")) != 0;
        int ageIndex = cursor.getColumnIndexOrThrow("age");
        int weightIndex = cursor.getColumnIndexOrThrow("weight_kg");
        int heightIndex = cursor.getColumnIndexOrThrow("height_cm");
        int formulaIndex = cursor.getColumnIndexOrThrow("formula_sex");
        int activityIndex = cursor.getColumnIndexOrThrow("activity_level");
        Integer age = cursor.isNull(ageIndex) ? null : cursor.getInt(ageIndex);
        Double weightKg = cursor.isNull(weightIndex) ? null : cursor.getDouble(weightIndex);
        Double heightCm = cursor.isNull(heightIndex) ? null : cursor.getDouble(heightIndex);
        String formulaSex = cursor.isNull(formulaIndex) ? null : cursor.getString(formulaIndex);
        Integer activityLevel = cursor.isNull(activityIndex) ? null : cursor.getInt(activityIndex);
        return new Account(id, email, goal, goalCompleted, age, weightKg,
                heightCm, formulaSex, activityLevel);
    }

    void addMeal(int accountId, String name, double amount, double calories, double protein,
                 double carbs, double fat) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("amount_g", amount);
        values.put("calories", calories);
        values.put("protein_g", protein);
        values.put("carbs_g", carbs);
        values.put("fat_g", fat);
        values.put("eaten_at", System.currentTimeMillis());
        values.put("account_id", accountId);
        getWritableDatabase().insertOrThrow("meals", null, values);
    }

    List<Meal> getTodayMeals(int accountId) {
        Calendar startOfDay = Calendar.getInstance();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);

        List<Meal> meals = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "meals",
                new String[]{"name", "amount_g", "calories", "protein_g",
                        "carbs_g", "fat_g", "eaten_at"},
                "account_id = ? AND eaten_at >= ?",
                new String[]{Integer.toString(accountId),
                        Long.toString(startOfDay.getTimeInMillis())},
                null,
                null,
                "eaten_at DESC"
        )) {
            while (cursor.moveToNext()) {
                meals.add(new Meal(
                        cursor.getString(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2),
                        cursor.getDouble(3),
                        cursor.getDouble(4),
                        cursor.getDouble(5),
                        cursor.getLong(6)
                ));
            }
        }
        return meals;
    }

    private String passwordDigest(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PASSWORD_ITERATIONS, 256);
        try {
            byte[] digest = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
            return toHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível verificar a senha.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private String toHex(byte[] value) {
        StringBuilder text = new StringBuilder(value.length * 2);
        for (byte item : value) {
            text.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return text.toString();
    }

    private byte[] fromHex(String value) {
        if (value == null || value.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid stored value.");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid stored value.");
            }
            result[i] = (byte) ((high << 4) + low);
        }
        return result;
    }

    static final class Account {
        final int id;
        final String email;
        final String goal;
        final boolean goalCompleted;
        final Integer age;
        final Double weightKg;
        final Double heightCm;
        final String formulaSex;
        final Integer activityLevel;

        Account(
                int id,
                String email,
                String goal,
                boolean goalCompleted,
                Integer age,
                Double weightKg,
                Double heightCm,
                String formulaSex,
                Integer activityLevel) {
            this.id = id;
            this.email = email;
            this.goal = goal;
            this.goalCompleted = goalCompleted;
            this.age = age;
            this.weightKg = weightKg;
            this.heightCm = heightCm;
            this.formulaSex = formulaSex;
            this.activityLevel = activityLevel;
        }

        boolean hasCalorieProfile() {
            return age != null && weightKg != null && heightCm != null
                    && formulaSex != null && activityLevel != null;
        }
    }

    static final class Meal {
        final String name;
        final double amount;
        final double calories;
        final double protein;
        final double carbs;
        final double fat;
        final long eatenAt;

        Meal(String name, double amount, double calories, double protein,
             double carbs, double fat, long eatenAt) {
            this.name = name;
            this.amount = amount;
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fat = fat;
            this.eatenAt = eatenAt;
        }
    }
}
