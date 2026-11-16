from datetime import date, datetime, time, timedelta
import math

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.models import Meal


def add_meal(
    session: Session,
    *,
    name: str,
    amount_g: float,
    calories: float,
    protein_g: float,
    carbs_g: float,
    fat_g: float,
    eaten_at: datetime | None = None,
) -> Meal:
    cleaned_name = name.strip()
    numeric_values = (amount_g, calories, protein_g, carbs_g, fat_g)
    if not cleaned_name:
        raise ValueError("O nome da refeição não pode ficar vazio.")
    if len(cleaned_name) > 120:
        raise ValueError("O nome da refeição deve ter no máximo 120 caracteres.")
    if not all(math.isfinite(value) for value in numeric_values):
        raise ValueError("Os valores nutricionais devem ser números finitos.")
    if amount_g <= 0 or any(value < 0 for value in numeric_values[1:]):
        raise ValueError("A porção deve ser positiva e os macros não negativos.")
    if amount_g > 100000 or calories > 100000 or any(
        value > 10000 for value in numeric_values[2:]
    ):
        raise ValueError("Um dos valores ultrapassa o limite permitido.")

    meal_time = eaten_at or datetime.now()
    if meal_time.tzinfo is not None:
        meal_time = meal_time.astimezone().replace(tzinfo=None)

    meal = Meal(
        name=cleaned_name,
        amount_g=amount_g,
        calories=calories,
        protein_g=protein_g,
        carbs_g=carbs_g,
        fat_g=fat_g,
        eaten_at=meal_time,
    )
    session.add(meal)
    session.commit()
    session.refresh(meal)
    return meal


def list_meals(session: Session, day: date | None = None) -> list[Meal]:
    statement = select(Meal).order_by(Meal.eaten_at.desc(), Meal.id.desc())
    if day is not None:
        start = datetime.combine(day, time.min)
        end = start + timedelta(days=1)
        statement = statement.where(Meal.eaten_at >= start, Meal.eaten_at < end)
    return list(session.scalars(statement))


def delete_meal(session: Session, meal_id: int) -> bool:
    meal = session.get(Meal, meal_id)
    if meal is None:
        return False
    session.delete(meal)
    session.commit()
    return True


def summarize_meals(meals: list[Meal]) -> dict[str, float | int]:
    return {
        "meal_count": len(meals),
        "calories": round(sum(meal.calories for meal in meals), 1),
        "protein_g": round(sum(meal.protein_g for meal in meals), 1),
        "carbs_g": round(sum(meal.carbs_g for meal in meals), 1),
        "fat_g": round(sum(meal.fat_g for meal in meals), 1),
    }
