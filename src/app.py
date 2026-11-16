import threading
from datetime import date
import math

import customtkinter as ctk
from tkinter import messagebox
from sqlalchemy.exc import SQLAlchemyError

from src.ai_service import AIServiceError, suggest_meals
from src.config import get_ai_settings
from src.database import (
    create_database_engine,
    create_session_factory,
    initialize_database,
)
from src.repository import add_meal, list_meals, summarize_meals

engine = create_database_engine()
initialize_database(engine)
SessionFactory = create_session_factory(engine)


class FitFoodApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        self.title("FitFood - Diário alimentar")
        self.geometry("760x700")
        self.minsize(620, 580)

        ctk.CTkLabel(self, text="FitFood", font=ctk.CTkFont(size=28, weight="bold")).pack(
            pady=(18, 2)
        )
        ctk.CTkLabel(
            self,
            text="Registre refeições e acompanhe seus macros do dia.",
        ).pack(pady=(0, 12))

        self.form = ctk.CTkFrame(self)
        self.form.pack(fill="x", padx=20, pady=8)
        self.entries = {}
        fields = [
            ("Alimento/refeição", "name"),
            ("Porção (g)", "amount_g"),
            ("Calorias (kcal)", "calories"),
            ("Proteínas (g)", "protein_g"),
            ("Carboidratos (g)", "carbs_g"),
            ("Gorduras (g)", "fat_g"),
        ]
        for index, (label, key) in enumerate(fields):
            row, column = divmod(index, 2)
            cell = ctk.CTkFrame(self.form, fg_color="transparent")
            cell.grid(row=row, column=column, sticky="ew", padx=10, pady=8)
            ctk.CTkLabel(cell, text=label).pack(anchor="w")
            entry = ctk.CTkEntry(cell, placeholder_text=label)
            entry.pack(fill="x")
            self.entries[key] = entry
            self.form.grid_columnconfigure(column, weight=1)

        ctk.CTkButton(self.form, text="Adicionar refeição", command=self.add).grid(
            row=3, column=0, columnspan=2, padx=10, pady=(4, 12)
        )
        self.summary_label = ctk.CTkLabel(self, text="")
        self.summary_label.pack(pady=8)
        self.meal_list = ctk.CTkTextbox(self, height=230)
        self.meal_list.pack(fill="both", expand=True, padx=20, pady=8)
        self.meal_list.configure(state="disabled")
        self.suggestion_button = ctk.CTkButton(
            self, text="Pedir ideias gerais à IA", command=self.request_suggestion
        )
        self.suggestion_button.pack(pady=(6, 8))
        ctk.CTkLabel(
            self,
            text=(
                "Informações e sugestões são gerais, podem conter erros e não "
                "substituem orientação profissional."
            ),
            wraplength=680,
        ).pack(padx=16, pady=(0, 12))
        self.refresh()

    def add(self) -> None:
        try:
            values = {
                key: entry.get().strip() for key, entry in self.entries.items()
            }
            if not values["name"]:
                raise ValueError("Informe o nome do alimento ou refeição.")
            numeric = {
                key: float(values[key].replace(",", "."))
                for key in ("amount_g", "calories", "protein_g", "carbs_g", "fat_g")
            }
            if not all(math.isfinite(value) for value in numeric.values()):
                raise ValueError("Informe valores numéricos finitos.")
            if numeric["amount_g"] <= 0 or any(
                value < 0 for key, value in numeric.items() if key != "amount_g"
            ):
                raise ValueError("A porção deve ser positiva e os macros não negativos.")
            with SessionFactory() as session:
                add_meal(session, name=values["name"], **numeric)
        except ValueError as exc:
            messagebox.showerror("Dados inválidos", str(exc))
            return
        except SQLAlchemyError as exc:
            messagebox.showerror("Erro ao salvar", f"Não foi possível salvar: {exc}")
            return

        for entry in self.entries.values():
            entry.delete(0, "end")
        self.refresh()

    def refresh(self) -> None:
        with SessionFactory() as session:
            meals = list_meals(session, date.today())
        totals = summarize_meals(meals)
        self.summary_label.configure(
            text=(
                f"Hoje: {totals['meal_count']} refeições | "
                f"{totals['calories']} kcal | "
                f"P {totals['protein_g']} g | C {totals['carbs_g']} g | "
                f"G {totals['fat_g']} g"
            )
        )
        lines = [
            f"{meal.eaten_at:%H:%M} - {meal.name} ({meal.amount_g:g} g): "
            f"{meal.calories:g} kcal | P {meal.protein_g:g} g | "
            f"C {meal.carbs_g:g} g | G {meal.fat_g:g} g"
            for meal in meals
        ]
        self.meal_list.configure(state="normal")
        self.meal_list.delete("1.0", "end")
        self.meal_list.insert("1.0", "\n".join(lines) or "Nenhuma refeição registrada hoje.")
        self.meal_list.configure(state="disabled")

    def request_suggestion(self) -> None:
        self.suggestion_button.configure(state="disabled", text="Consultando...")
        with SessionFactory() as session:
            summary = summarize_meals(list_meals(session, date.today()))
        threading.Thread(
            target=self._suggestion_worker, args=(summary,), daemon=True
        ).start()

    def _suggestion_worker(self, summary: dict[str, float | int]) -> None:
        api_key, api_url, model = get_ai_settings()
        try:
            suggestion = suggest_meals(
                api_key=api_key,
                api_url=api_url,
                model=model,
                summary=summary,
            )
        except AIServiceError as exc:
            self.after(0, self._show_suggestion_error, str(exc))
            return
        self.after(0, self._show_suggestion, suggestion)

    def _show_suggestion_error(self, message: str) -> None:
        self.suggestion_button.configure(state="normal", text="Pedir ideias gerais à IA")
        messagebox.showerror("Sugestão indisponível", message)

    def _show_suggestion(self, suggestion: str) -> None:
        self.suggestion_button.configure(state="normal", text="Pedir ideias gerais à IA")
        messagebox.showinfo("Ideias gerais de refeições", suggestion)


if __name__ == "__main__":
    ctk.set_appearance_mode("system")
    FitFoodApp().mainloop()
