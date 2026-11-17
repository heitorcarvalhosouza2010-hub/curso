from pathlib import Path

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from src.config import PROJECT_ROOT, get_database_url
from src.models import Base


def create_database_engine(database_url: str | None = None) -> Engine:
    url = database_url or get_database_url()
    connect_args = {}

    if url.startswith("sqlite:///"):
        relative_path = url.removeprefix("sqlite:///")
        if relative_path != ":memory:":
            path = Path(relative_path)
            if not path.is_absolute():
                path = PROJECT_ROOT / path
            path.parent.mkdir(parents=True, exist_ok=True)
            url = f"sqlite:///{path.as_posix()}"
        connect_args["check_same_thread"] = False

    return create_engine(url, connect_args=connect_args)


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False)


def initialize_database(engine: Engine) -> None:
    Base.metadata.create_all(engine)
    account_columns = {
        column["name"] for column in inspect(engine).get_columns("accounts")
    }
    if "goal_completed" not in account_columns:
        with engine.begin() as connection:
            connection.execute(
                text(
                    "ALTER TABLE accounts ADD COLUMN goal_completed "
                    "BOOLEAN NOT NULL DEFAULT true"
                )
            )
    meal_columns = {column["name"] for column in inspect(engine).get_columns("meals")}
    if "account_id" not in meal_columns:
        with engine.begin() as connection:
            connection.execute(
                text(
                    "ALTER TABLE meals ADD COLUMN account_id INTEGER "
                    "REFERENCES accounts(id) ON DELETE CASCADE"
                )
            )
    meal_indexes = {index["name"] for index in inspect(engine).get_indexes("meals")}
    if "ix_meals_account_id" not in meal_indexes:
        with engine.begin() as connection:
            connection.execute(
                text("CREATE INDEX ix_meals_account_id ON meals (account_id)")
            )
