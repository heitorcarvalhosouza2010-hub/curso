import os
from pathlib import Path

from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parent.parent


def get_user_data_dir() -> Path:
    if os.name == "nt":
        data_root = Path(os.getenv("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    else:
        data_root = Path(
            os.getenv("XDG_DATA_HOME", Path.home() / ".local" / "share")
        )
    data_dir = data_root / "FitFood"
    data_dir.mkdir(parents=True, exist_ok=True)
    return data_dir


load_dotenv(PROJECT_ROOT / ".env")
load_dotenv(get_user_data_dir() / ".env")


def get_database_url() -> str:
    configured_url = os.getenv("DATABASE_URL", "").strip()
    if configured_url:
        return configured_url

    return f"sqlite:///{(get_user_data_dir() / 'fitfood.db').as_posix()}"


def get_api_token() -> str | None:
    token = os.getenv("APP_API_TOKEN", "").strip()
    return token or None


def get_ai_settings() -> tuple[str | None, str, str]:
    api_key = os.getenv("AI_API_KEY", "").strip() or None
    api_url = os.getenv(
        "AI_API_URL", "https://api.openai.com/v1/chat/completions"
    ).strip()
    model = os.getenv("AI_MODEL", "gpt-4o-mini").strip()
    return api_key, api_url, model
