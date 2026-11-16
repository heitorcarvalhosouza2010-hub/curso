from datetime import date, datetime
from typing import Annotated, Iterator

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy.orm import Session

from src.ai_service import AIServiceError, suggest_meals
from src.config import get_ai_settings, get_api_token
from src.database import (
    create_database_engine,
    create_session_factory,
    initialize_database,
)
from src.repository import add_meal, delete_meal, list_meals, summarize_meals

engine = create_database_engine()
initialize_database(engine)
SessionFactory = create_session_factory(engine)

app = FastAPI(
    title="FitFood API",
    description=(
        "Registro pessoal de refeições e macronutrientes. Dados não são "
        "aconselhamento médico."
    ),
    version="1.0.0",
)


class MealInput(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    amount_g: float = Field(gt=0, le=100000, allow_inf_nan=False)
    calories: float = Field(ge=0, le=100000, allow_inf_nan=False)
    protein_g: float = Field(ge=0, le=10000, allow_inf_nan=False)
    carbs_g: float = Field(ge=0, le=10000, allow_inf_nan=False)
    fat_g: float = Field(ge=0, le=10000, allow_inf_nan=False)
    eaten_at: datetime | None = None

    @field_validator("name")
    @classmethod
    def trim_and_validate_name(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("Informe o nome do alimento ou refeição.")
        return cleaned


class MealResponse(MealInput):
    model_config = ConfigDict(from_attributes=True)
    id: int


def require_token(
    authorization: Annotated[str | None, Header()] = None,
) -> None:
    expected_token = get_api_token()
    if not expected_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Configure APP_API_TOKEN antes de usar os endpoints de dados.",
        )
    if authorization != f"Bearer {expected_token}":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token ausente ou inválido.",
            headers={"WWW-Authenticate": "Bearer"},
        )


def get_session() -> Iterator[Session]:
    with SessionFactory() as session:
        yield session


SessionDependency = Annotated[Session, Depends(get_session)]
TokenDependency = Annotated[None, Depends(require_token)]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/meals", response_model=list[MealResponse])
def get_meals(
    session: SessionDependency,
    _: TokenDependency,
    day: date | None = None,
) -> list:
    return list_meals(session, day)


@app.post("/meals", response_model=MealResponse, status_code=201)
def post_meal(
    meal: MealInput,
    session: SessionDependency,
    __: TokenDependency,
) -> MealResponse:
    return add_meal(session, **meal.model_dump())


@app.delete("/meals/{meal_id}", status_code=204)
def remove_meal(
    meal_id: int,
    session: SessionDependency,
    __: TokenDependency,
) -> None:
    if not delete_meal(session, meal_id):
        raise HTTPException(status_code=404, detail="Refeição não encontrada.")


@app.get("/summary")
def get_summary(
    session: SessionDependency,
    _: TokenDependency,
    day: date | None = None,
) -> dict[str, float | int | str]:
    target_day = day or date.today()
    totals = summarize_meals(list_meals(session, target_day))
    return {"day": target_day.isoformat(), **totals}


@app.post("/suggestions")
def get_suggestions(
    session: SessionDependency,
    __: TokenDependency,
) -> dict[str, str]:
    api_key, api_url, model = get_ai_settings()
    summary = summarize_meals(list_meals(session, date.today()))
    try:
        suggestion = suggest_meals(
            api_key=api_key,
            api_url=api_url,
            model=model,
            summary=summary,
        )
    except AIServiceError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return {"suggestion": suggestion}
