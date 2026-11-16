package br.com.fitfood;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

final class MealDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "fitfood.db";
    private static final int DATABASE_VERSION = 1;

    MealDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE meals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "amount_g REAL NOT NULL, " +
                        "calories REAL NOT NULL, " +
                        "protein_g REAL NOT NULL, " +
                        "carbs_g REAL NOT NULL, " +
                        "fat_g REAL NOT NULL, " +
                        "eaten_at INTEGER NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException("A migração do banco de dados é necessária.");
    }

    void addMeal(String name, double amount, double calories, double protein,
                 double carbs, double fat) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("amount_g", amount);
        values.put("calories", calories);
        values.put("protein_g", protein);
        values.put("carbs_g", carbs);
        values.put("fat_g", fat);
        values.put("eaten_at", System.currentTimeMillis());
        getWritableDatabase().insertOrThrow("meals", null, values);
    }

    List<Meal> getTodayMeals() {
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
                "eaten_at >= ?",
                new String[]{Long.toString(startOfDay.getTimeInMillis())},
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
