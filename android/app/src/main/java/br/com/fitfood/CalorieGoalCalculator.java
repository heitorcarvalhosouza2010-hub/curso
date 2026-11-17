package br.com.fitfood;

final class CalorieGoalCalculator {
    static final String FEMALE_FORMULA = "feminina";
    static final String MALE_FORMULA = "masculina";

    private static final double[] ACTIVITY_MULTIPLIERS = {1.2, 1.375, 1.55, 1.725};

    private CalorieGoalCalculator() {
    }

    static int estimate(
            int age,
            double weightKg,
            double heightCm,
            String formula,
            int activityLevel,
            String goal) {
        if (age < 18 || age > 100) {
            throw new IllegalArgumentException("A estimativa está disponível somente para adultos.");
        }
        if (!isFiniteInRange(weightKg, 30, 350)
                || !isFiniteInRange(heightCm, 100, 250)) {
            throw new IllegalArgumentException("Confira o peso e a altura informados.");
        }
        if (!FEMALE_FORMULA.equals(formula) && !MALE_FORMULA.equals(formula)) {
            throw new IllegalArgumentException("Selecione a fórmula metabólica.");
        }
        if (activityLevel < 0 || activityLevel >= ACTIVITY_MULTIPLIERS.length) {
            throw new IllegalArgumentException("Selecione o nível de atividade.");
        }

        double basalRate = 10 * weightKg + 6.25 * heightCm - 5 * age
                + (MALE_FORMULA.equals(formula) ? 5 : -161);
        double dailyMaintenance = basalRate * ACTIVITY_MULTIPLIERS[activityLevel];
        if ("Ganhar massa muscular".equals(goal)) {
            dailyMaintenance += 200;
        } else if ("Perder gordura".equals(goal)) {
            dailyMaintenance -= 200;
        } else if (!isMaintenanceGoal(goal)) {
            throw new IllegalArgumentException("Selecione um objetivo válido.");
        }
        if (!isFiniteInRange(dailyMaintenance, 1, 10000)) {
            throw new IllegalArgumentException("Não foi possível estimar uma meta para este perfil.");
        }
        if (dailyMaintenance < 1200) {
            throw new IllegalArgumentException(
                    "A estimativa ficou abaixo de 1.200 kcal/dia. Peça orientação a um nutricionista.");
        }
        return (int) (Math.round(dailyMaintenance / 50.0) * 50);
    }

    private static boolean isMaintenanceGoal(String goal) {
        return "Manter o peso".equals(goal)
                || "Melhorar o desempenho".equals(goal)
                || "Ter uma alimentação equilibrada".equals(goal);
    }

    private static boolean isFiniteInRange(double value, double minimum, double maximum) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= minimum && value <= maximum;
    }
}
