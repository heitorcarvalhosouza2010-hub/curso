from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Float, Integer, String
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class Meal(Base):
    __tablename__ = "meals"
    __table_args__ = (
        CheckConstraint("amount_g > 0", name="ck_meals_amount_positive"),
        CheckConstraint("calories >= 0", name="ck_meals_calories_nonnegative"),
        CheckConstraint("protein_g >= 0", name="ck_meals_protein_nonnegative"),
        CheckConstraint("carbs_g >= 0", name="ck_meals_carbs_nonnegative"),
        CheckConstraint("fat_g >= 0", name="ck_meals_fat_nonnegative"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    amount_g: Mapped[float] = mapped_column(Float, nullable=False)
    calories: Mapped[float] = mapped_column(Float, nullable=False)
    protein_g: Mapped[float] = mapped_column(Float, nullable=False)
    carbs_g: Mapped[float] = mapped_column(Float, nullable=False)
    fat_g: Mapped[float] = mapped_column(Float, nullable=False)
    eaten_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=datetime.now, index=True
    )
