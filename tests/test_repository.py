import unittest
from datetime import date, datetime
from pathlib import Path
from tempfile import TemporaryDirectory

from src.database import (
    create_database_engine,
    create_session_factory,
    initialize_database,
)
from src.repository import add_meal, delete_meal, list_meals, summarize_meals


class MealRepositoryTests(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_database_engine("sqlite:///:memory:")
        initialize_database(self.engine)
        self.session_factory = create_session_factory(self.engine)

    def tearDown(self) -> None:
        self.engine.dispose()

    def test_daily_summary_totals_only_selected_day(self) -> None:
        with self.session_factory() as session:
            add_meal(
                session,
                name="Aveia",
                amount_g=100,
                calories=380,
                protein_g=13,
                carbs_g=68,
                fat_g=7,
                eaten_at=datetime(2026, 1, 1, 8),
            )
            add_meal(
                session,
                name="Fruta",
                amount_g=120,
                calories=70,
                protein_g=1,
                carbs_g=18,
                fat_g=0.2,
                eaten_at=datetime(2026, 1, 2, 8),
            )

            meals = list_meals(session, date(2026, 1, 1))
            self.assertEqual(len(meals), 1)
            self.assertEqual(
                summarize_meals(meals),
                {
                    "meal_count": 1,
                    "calories": 380,
                    "protein_g": 13,
                    "carbs_g": 68,
                    "fat_g": 7,
                },
            )

    def test_delete_meal_reports_missing_ids(self) -> None:
        with self.session_factory() as session:
            self.assertFalse(delete_meal(session, 999))
            meal = add_meal(
                session,
                name="Iogurte",
                amount_g=150,
                calories=100,
                protein_g=10,
                carbs_g=8,
                fat_g=3,
            )
            self.assertTrue(delete_meal(session, meal.id))
            self.assertEqual(list_meals(session), [])

    def test_add_meal_rejects_non_finite_values(self) -> None:
        with self.session_factory() as session:
            with self.assertRaisesRegex(ValueError, "finitos"):
                add_meal(
                    session,
                    name="Refeição",
                    amount_g=100,
                    calories=float("nan"),
                    protein_g=1,
                    carbs_g=1,
                    fat_g=1,
                )

    def test_database_engine_creates_parent_for_absolute_sqlite_path(self) -> None:
        with TemporaryDirectory() as temp_dir:
            database_path = Path(temp_dir) / "nested" / "fitfood.db"
            engine = create_database_engine(f"sqlite:///{database_path.as_posix()}")
            try:
                initialize_database(engine)
                self.assertTrue(database_path.exists())
            finally:
                engine.dispose()


if __name__ == "__main__":
    unittest.main()
