import unittest
from sqlalchemy import text

from src.auth import (
    FITNESS_GOALS,
    authenticate_account,
    create_account,
    update_account_goal,
)
from src.database import (
    create_database_engine,
    create_session_factory,
    initialize_database,
)
from src.models import Account
from src.repository import add_meal, list_meals


class AccountTests(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_database_engine("sqlite:///:memory:")
        initialize_database(self.engine)
        self.session_factory = create_session_factory(self.engine)

    def tearDown(self) -> None:
        self.engine.dispose()

    def test_account_password_is_hashed_and_login_is_case_insensitive(self) -> None:
        with self.session_factory() as session:
            account = create_account(
                session,
                email="  User@example.com ",
                password="passphrase-123",
                goal=FITNESS_GOALS[0],
            )
            self.assertEqual(account.email, "user@example.com")
            self.assertNotEqual(account.password_hash, "passphrase-123")
            self.assertFalse(account.goal_completed)
            self.assertIsNotNone(
                authenticate_account(
                    session,
                    email="USER@example.com",
                    password="passphrase-123",
                )
            )
            self.assertIsNone(
                authenticate_account(
                    session,
                    email="user@example.com",
                    password="wrong-password",
                )
            )
            updated = update_account_goal(
                session,
                account_id=account.id,
                goal=FITNESS_GOALS[1],
            )
            self.assertEqual(updated.goal, FITNESS_GOALS[1])
            self.assertTrue(updated.goal_completed)

    def test_account_registration_rejects_bad_email_password_and_duplicate(self) -> None:
        with self.session_factory() as session:
            for email, password in [
                ("not-an-email", "passphrase-123"),
                ("new@example.com", "short"),
            ]:
                with self.subTest(email=email):
                    with self.assertRaises(ValueError):
                        create_account(
                            session,
                            email=email,
                            password=password,
                            goal=FITNESS_GOALS[0],
                        )

            create_account(
                session,
                email="new@example.com",
                password="passphrase-123",
                goal=FITNESS_GOALS[0],
            )
            with self.assertRaisesRegex(ValueError, "já está cadastrado"):
                create_account(
                    session,
                    email="NEW@example.com",
                    password="passphrase-456",
                    goal=FITNESS_GOALS[1],
                )

    def test_meal_history_is_scoped_to_logged_in_account(self) -> None:
        with self.session_factory() as session:
            first = create_account(
                session,
                email="first@example.com",
                password="passphrase-123",
                goal=FITNESS_GOALS[0],
            )
            second = create_account(
                session,
                email="second@example.com",
                password="passphrase-456",
                goal=FITNESS_GOALS[1],
            )
            add_meal(
                session,
                account_id=first.id,
                name="Almoço",
                amount_g=300,
                calories=500,
                protein_g=25,
                carbs_g=50,
                fat_g=15,
            )

            self.assertEqual(
                len(list_meals(session, account_id=first.id)),
                1,
            )
            self.assertEqual(list_meals(session, account_id=second.id), [])

    def test_existing_database_migrates_and_keeps_old_meals(self) -> None:
        legacy_engine = create_database_engine("sqlite:///:memory:")
        try:
            with legacy_engine.begin() as connection:
                connection.execute(
                    text(
                        "CREATE TABLE meals ("
                        "id INTEGER PRIMARY KEY, "
                        "name VARCHAR(120) NOT NULL, "
                        "amount_g FLOAT NOT NULL, "
                        "calories FLOAT NOT NULL, "
                        "protein_g FLOAT NOT NULL, "
                        "carbs_g FLOAT NOT NULL, "
                        "fat_g FLOAT NOT NULL, "
                        "eaten_at DATETIME NOT NULL)"
                    )
                )
                connection.execute(
                    text(
                        "INSERT INTO meals "
                        "(name, amount_g, calories, protein_g, carbs_g, fat_g, eaten_at) "
                        "VALUES ('Refeição antiga', 250, 400, 20, 40, 10, "
                        "'2026-11-17 12:00:00')"
                    )
                )

            initialize_database(legacy_engine)
            session_factory = create_session_factory(legacy_engine)
            with session_factory() as session:
                account = create_account(
                    session,
                    email="legacy@example.com",
                    password="passphrase-123",
                    goal=FITNESS_GOALS[0],
                )
                meals = list_meals(session, account_id=account.id)
                self.assertEqual(len(meals), 1)
                self.assertEqual(meals[0].name, "Refeição antiga")
        finally:
            legacy_engine.dispose()

    def test_existing_accounts_are_marked_as_having_completed_goal_setup(self) -> None:
        legacy_engine = create_database_engine("sqlite:///:memory:")
        try:
            with legacy_engine.begin() as connection:
                connection.execute(
                    text(
                        "CREATE TABLE accounts ("
                        "id INTEGER PRIMARY KEY, "
                        "email VARCHAR(254) NOT NULL UNIQUE, "
                        "password_salt VARCHAR(32) NOT NULL, "
                        "password_hash VARCHAR(64) NOT NULL, "
                        "goal VARCHAR(50) NOT NULL)"
                    )
                )
                connection.execute(
                    text(
                        "INSERT INTO accounts "
                        "(email, password_salt, password_hash, goal) "
                        "VALUES ('existing@example.com', '00', '00', "
                        "'Manter o peso')"
                    )
                )

            initialize_database(legacy_engine)
            session_factory = create_session_factory(legacy_engine)
            with session_factory() as session:
                migrated_account = session.get(Account, 1)
                self.assertIsNotNone(migrated_account)
                self.assertTrue(migrated_account.goal_completed)
        finally:
            legacy_engine.dispose()


if __name__ == "__main__":
    unittest.main()
