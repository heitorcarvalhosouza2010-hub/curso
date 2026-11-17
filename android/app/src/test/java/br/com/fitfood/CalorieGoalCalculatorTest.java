package br.com.fitfood;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class CalorieGoalCalculatorTest {
    @Test
    public void estimatesDifferentTargetsForGoals() {
        int maintenance = CalorieGoalCalculator.estimate(
                30, 70, 170, CalorieGoalCalculator.FEMALE_FORMULA, 0, "Manter o peso");
        int fatLoss = CalorieGoalCalculator.estimate(
                30, 70, 170, CalorieGoalCalculator.FEMALE_FORMULA, 0, "Perder gordura");
        int muscleGain = CalorieGoalCalculator.estimate(
                30, 70, 170, CalorieGoalCalculator.FEMALE_FORMULA, 0,
                "Ganhar massa muscular");

        assertEquals(1750, maintenance);
        assertEquals(1550, fatLoss);
        assertEquals(1950, muscleGain);
    }

    @Test
    public void rejectsUnderageAndInvalidProfileValues() {
        assertThrows(IllegalArgumentException.class, () -> CalorieGoalCalculator.estimate(
                17, 70, 170, CalorieGoalCalculator.FEMALE_FORMULA, 0, "Manter o peso"));
        assertThrows(IllegalArgumentException.class, () -> CalorieGoalCalculator.estimate(
                30, 70, 170, CalorieGoalCalculator.FEMALE_FORMULA, 4, "Manter o peso"));
        assertThrows(IllegalArgumentException.class, () -> CalorieGoalCalculator.estimate(
                40, 35, 130, CalorieGoalCalculator.FEMALE_FORMULA, 0, "Perder gordura"));
    }
}
