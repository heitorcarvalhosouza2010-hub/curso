import threading
from datetime import date
from pathlib import Path
from tkinter import filedialog, messagebox

import customtkinter as ctk
from sqlalchemy.exc import SQLAlchemyError

from src.ai_service import AIServiceError, PlateAnalysis, analyze_plate_image
from src.auth import (
    FITNESS_GOALS,
    authenticate_account,
    create_account,
    has_accounts,
    update_account_goal,
)
from src.config import get_ai_settings
from src.database import (
    create_database_engine,
    create_session_factory,
    initialize_database,
)
from src.models import Account
from src.repository import add_meal, list_meals, summarize_meals

ctk.set_appearance_mode("light")
ctk.set_default_color_theme("green")

engine = create_database_engine()
initialize_database(engine)
SessionFactory = create_session_factory(engine)

IMAGE_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}
MAX_IMAGE_BYTES = 10 * 1024 * 1024
GOAL_DESCRIPTIONS = {
    "Ganhar massa muscular": "Acompanhe proteínas e refeições variadas.",
    "Perder gordura": "Busque consistência, sem metas extremas.",
    "Manter o peso": "Observe seus hábitos e mantenha o equilíbrio.",
    "Melhorar o desempenho": "Pense em energia e recuperação.",
    "Ter uma alimentação equilibrada": "Explore variedade e bons hábitos.",
}


class FitFoodApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        self.title("FitFood - Análise de refeições")
        self.geometry("980x800")
        self.minsize(840, 700)
        self.configure(fg_color="#eef5f3")
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(0, weight=1)

        self.account: Account | None = None
        self.auth_frame = None
        self.goal_frame = None
        self.main_frame = None
        self.selected_photo: Path | None = None
        self.pending_analysis: PlateAnalysis | None = None
        self.analysis_in_progress = False

        with SessionFactory() as session:
            initial_mode = "login" if has_accounts(session) else "register"
        self._show_auth_screen(initial_mode)

    def _show_auth_screen(self, mode: str) -> None:
        if self.main_frame is not None:
            self.main_frame.destroy()
            self.main_frame = None
        if self.goal_frame is not None:
            self.goal_frame.destroy()
            self.goal_frame = None
        if self.auth_frame is not None:
            self.auth_frame.destroy()

        self.auth_mode = mode
        self.auth_frame = ctk.CTkFrame(
            self,
            fg_color="transparent",
        )
        self.auth_frame.grid(
            row=0, column=0, padx=28, pady=28, sticky="nsew"
        )
        self.auth_frame.grid_columnconfigure(0, weight=5)
        self.auth_frame.grid_columnconfigure(1, weight=6)
        self.auth_frame.grid_rowconfigure(0, weight=1)

        self._create_brand_panel(self.auth_frame).grid(
            row=0, column=0, padx=(0, 12), sticky="nsew"
        )
        panel = ctk.CTkFrame(
            self.auth_frame,
            fg_color="#ffffff",
            corner_radius=22,
        )
        panel.grid(row=0, column=1, padx=(12, 0), sticky="nsew")
        panel.grid_columnconfigure(0, weight=1)

        heading = "Bem-vindo de volta" if mode == "login" else "Crie sua conta"
        description = (
            "Entre para voltar ao seu diário."
            if mode == "login"
            else "Crie sua conta com email e senha. O objetivo será escolhido na próxima etapa."
        )
        ctk.CTkLabel(
            panel,
            text=heading,
            font=ctk.CTkFont(size=25, weight="bold"),
            text_color="#173b2c",
        ).grid(row=0, column=0, sticky="w", padx=28, pady=(34, 4))
        ctk.CTkLabel(
            panel,
            text=description,
            text_color="#5d6a66",
        ).grid(row=1, column=0, sticky="w", padx=28, pady=(0, 20))

        form = ctk.CTkFrame(panel, fg_color="transparent")
        form.grid(row=2, column=0, padx=28, pady=4, sticky="ew")
        form.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(
            form,
            text="EMAIL",
            font=ctk.CTkFont(size=11, weight="bold"),
            text_color="#42544c",
        ).grid(
            row=0, column=0, sticky="w", pady=(4, 6)
        )
        self.email_entry = ctk.CTkEntry(
            form,
            placeholder_text="voce@exemplo.com",
            height=46,
            border_color="#dfeae6",
            fg_color="#f8fbfa",
        )
        self.email_entry.grid(row=1, column=0, sticky="ew", pady=(0, 18))

        ctk.CTkLabel(
            form,
            text="SENHA",
            font=ctk.CTkFont(size=11, weight="bold"),
            text_color="#42544c",
        ).grid(
            row=2, column=0, sticky="w", pady=(0, 6)
        )
        self.password_entry = ctk.CTkEntry(
            form,
            placeholder_text="Pelo menos 8 caracteres",
            show="*",
            height=46,
            border_color="#dfeae6",
            fg_color="#f8fbfa",
        )
        self.password_entry.grid(row=3, column=0, sticky="ew", pady=(0, 18))

        action_text = "Criar conta" if mode == "register" else "Entrar no FitFood"
        ctk.CTkButton(
            form,
            text=action_text,
            command=self._submit_auth,
            height=46,
            corner_radius=14,
            fg_color="#1b8f63",
            hover_color="#157a54",
            font=ctk.CTkFont(size=14, weight="bold"),
        ).grid(row=4, column=0, sticky="ew", pady=(2, 8))
        self.password_entry.bind("<Return>", self._submit_auth)

        toggle_text = (
            "Já tenho conta? Entrar"
            if mode == "register"
            else "Ainda não tem conta? Criar uma"
        )
        ctk.CTkButton(
            panel,
            text=toggle_text,
            command=lambda: self._show_auth_screen(
                "login" if mode == "register" else "register"
            ),
            fg_color="transparent",
            hover_color="#edf5f1",
            text_color="#1b7655",
        ).grid(row=3, column=0, padx=22, pady=(2, 4))
        ctk.CTkLabel(
            panel,
            text=(
                "Conta local neste computador. O email não é verificado; "
                "não há recuperação ou sincronização de senha."
            ),
            wraplength=440,
            justify="center",
            text_color="#6b7772",
        ).grid(row=4, column=0, padx=24, pady=(4, 22))
        self.email_entry.focus()

    def _create_brand_panel(self, parent) -> ctk.CTkFrame:
        panel = ctk.CTkFrame(
            parent,
            fg_color="#123c2a",
            corner_radius=24,
        )
        panel.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            panel,
            text="FITFOOD  /  SEU DIÁRIO INTELIGENTE",
            font=ctk.CTkFont(size=11, weight="bold"),
            text_color="#bfe8d1",
        ).grid(row=0, column=0, sticky="w", padx=28, pady=(34, 18))
        ctk.CTkLabel(
            panel,
            text="Entenda melhor\no que tem no seu prato.",
            font=ctk.CTkFont(size=31, weight="bold"),
            text_color="#f5fff8",
            justify="left",
        ).grid(row=1, column=0, sticky="w", padx=28, pady=(0, 12))
        ctk.CTkLabel(
            panel,
            text=(
                "Fotografe sua refeição e receba uma estimativa nutricional "
                "com sugestões gerais para o seu objetivo."
            ),
            font=ctk.CTkFont(size=14),
            text_color="#dfeee8",
            wraplength=360,
            justify="left",
        ).grid(row=2, column=0, sticky="w", padx=28, pady=(0, 28))

        benefits = ctk.CTkFrame(
            panel,
            fg_color="#1d563c",
            corner_radius=18,
        )
        benefits.grid(row=3, column=0, padx=24, pady=(0, 22), sticky="ew")
        benefits.grid_columnconfigure(0, weight=1)
        for row, text in enumerate(
            (
                "01   Identifique alimentos pela foto",
                "02   Veja calorias e macronutrientes",
                "03   Acompanhe seu histórico diário",
            )
        ):
            ctk.CTkLabel(
                benefits,
                text=text,
                font=ctk.CTkFont(size=13, weight="bold"),
                text_color="#f5fff8",
                anchor="w",
            ).grid(row=row, column=0, sticky="ew", padx=16, pady=10)
        ctk.CTkLabel(
            panel,
            text="Estimativas visuais são aproximadas e podem conter erros.",
            text_color="#b9d1c5",
            wraplength=360,
            justify="left",
        ).grid(row=4, column=0, sticky="sw", padx=28, pady=(0, 26))
        panel.grid_rowconfigure(5, weight=1)
        return panel

    def _submit_auth(self, _event=None) -> None:
        email = self.email_entry.get()
        password = self.password_entry.get()
        try:
            with SessionFactory() as session:
                if self.auth_mode == "register":
                    account = create_account(
                        session,
                        email=email,
                        password=password,
                    )
                else:
                    account = authenticate_account(
                        session,
                        email=email,
                        password=password,
                    )
        except ValueError as exc:
            messagebox.showerror("Dados inválidos", str(exc), parent=self)
            return
        except SQLAlchemyError as exc:
            messagebox.showerror(
                "Erro de acesso",
                f"Não foi possível acessar a conta: {exc}",
                parent=self,
            )
            return

        if account is None:
            messagebox.showerror(
                "Não foi possível entrar",
                "Confira o email e a senha e tente novamente.",
                parent=self,
            )
            return
        self.account = account
        if not account.goal_completed:
            self._show_goal_setup()
        else:
            self._build_main_screen()

    def _show_goal_setup(self, selected_goal: str | None = None) -> None:
        if self.auth_frame is not None:
            self.auth_frame.destroy()
            self.auth_frame = None
        if self.main_frame is not None:
            self.main_frame.destroy()
            self.main_frame = None
        if self.goal_frame is not None:
            self.goal_frame.destroy()

        self.selected_goal = selected_goal
        self.goal_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.goal_frame.grid(row=0, column=0, padx=28, pady=28, sticky="nsew")
        self.goal_frame.grid_columnconfigure(0, weight=5)
        self.goal_frame.grid_columnconfigure(1, weight=6)
        self.goal_frame.grid_rowconfigure(0, weight=1)
        self._create_brand_panel(self.goal_frame).grid(
            row=0, column=0, padx=(0, 12), sticky="nsew"
        )

        panel = ctk.CTkFrame(
            self.goal_frame,
            fg_color="#ffffff",
            corner_radius=22,
        )
        panel.grid(row=0, column=1, padx=(12, 0), sticky="nsew")
        panel.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            panel,
            text="Seu objetivo, no seu ritmo",
            font=ctk.CTkFont(size=24, weight="bold"),
            text_color="#173b2c",
        ).grid(row=0, column=0, sticky="w", padx=24, pady=(24, 4))
        ctk.CTkLabel(
            panel,
            text=(
                "Escolha um foco para personalizar sugestões gerais. "
                "Você pode alterá-lo depois."
            ),
            wraplength=450,
            justify="left",
            text_color="#5d6a66",
        ).grid(row=1, column=0, sticky="w", padx=24, pady=(0, 14))

        self.goal_option_buttons = {}
        for row, goal in enumerate(FITNESS_GOALS, start=2):
            button = ctk.CTkButton(
                panel,
                text=f"{goal}\n{GOAL_DESCRIPTIONS[goal]}",
                command=lambda selected=goal: self._select_goal(selected),
                anchor="w",
                height=62,
                corner_radius=13,
                fg_color="#f4f8f6",
                hover_color="#e7f2ec",
                text_color="#173b2c",
                border_width=1,
                border_color="#dfeae6",
                font=ctk.CTkFont(size=13),
            )
            button.grid(
                row=row, column=0, padx=20, pady=4, sticky="ew"
            )
            self.goal_option_buttons[goal] = button
        if selected_goal in self.goal_option_buttons:
            self._select_goal(selected_goal)

        self.goal_continue_button = ctk.CTkButton(
            panel,
            text="Continuar para o diário",
            command=self._save_goal,
            state="normal" if selected_goal else "disabled",
            height=44,
            corner_radius=14,
            fg_color="#1b8f63",
            hover_color="#157a54",
            font=ctk.CTkFont(size=14, weight="bold"),
        )
        self.goal_continue_button.grid(
            row=7, column=0, padx=20, pady=(10, 4), sticky="ew"
        )
        ctk.CTkLabel(
            panel,
            text=(
                "As sugestões são educativas; o objetivo não substitui "
                "avaliação profissional."
            ),
            wraplength=440,
            justify="center",
            text_color="#6b7772",
        ).grid(row=8, column=0, padx=20, pady=(4, 18))

    def _select_goal(self, goal: str) -> None:
        self.selected_goal = goal
        for option, button in self.goal_option_buttons.items():
            selected = option == goal
            button.configure(
                fg_color="#1b8f63" if selected else "#f4f8f6",
                hover_color="#157a54" if selected else "#e7f2ec",
                text_color="#ffffff" if selected else "#173b2c",
                border_color="#1b8f63" if selected else "#dfeae6",
            )
        self.goal_continue_button.configure(state="normal")

    def _save_goal(self) -> None:
        if self.account is None or self.selected_goal is None:
            return
        try:
            with SessionFactory() as session:
                self.account = update_account_goal(
                    session,
                    account_id=self.account.id,
                    goal=self.selected_goal,
                )
        except (ValueError, SQLAlchemyError) as exc:
            messagebox.showerror(
                "Não foi possível salvar o objetivo", str(exc), parent=self
            )
            return
        self._build_main_screen()

    def _build_main_screen(self) -> None:
        if self.auth_frame is not None:
            self.auth_frame.destroy()
            self.auth_frame = None
        if self.goal_frame is not None:
            self.goal_frame.destroy()
            self.goal_frame = None
        self.main_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.main_frame.grid(row=0, column=0, padx=18, pady=14, sticky="nsew")
        self.main_frame.grid_columnconfigure(0, weight=1)

        header = ctk.CTkFrame(
            self.main_frame,
            fg_color="#123c2a",
            corner_radius=22,
        )
        header.grid(row=0, column=0, pady=(0, 10), sticky="ew")
        header.grid_columnconfigure(0, weight=1)
        header.grid_columnconfigure(1, weight=0)
        header.grid_columnconfigure(2, weight=0)
        ctk.CTkLabel(
            header,
            text="FitFood",
            font=ctk.CTkFont(size=28, weight="bold"),
            text_color="#f5fff8",
        ).grid(row=0, column=0, sticky="w", padx=20, pady=(15, 2))
        ctk.CTkLabel(
            header,
            text=f"{self.account.email}  ·  Objetivo: {self.account.goal}",
            text_color="#dfeee8",
        ).grid(row=1, column=0, sticky="w", padx=20, pady=(0, 15))
        self.change_goal_button = ctk.CTkButton(
            header,
            text="Alterar objetivo",
            command=self._edit_goal,
            fg_color="#1d563c",
            hover_color="#28734e",
            text_color="#f5fff8",
        )
        self.change_goal_button.grid(
            row=0, column=1, rowspan=2, padx=(0, 8), pady=12, sticky="e"
        )
        self.logout_button = ctk.CTkButton(
            header,
            text="Sair",
            command=self._logout,
            width=80,
            fg_color="#bfe8d1",
            hover_color="#a7d8bd",
            text_color="#123c2a",
        )
        self.logout_button.grid(
            row=0, column=2, rowspan=2, padx=(0, 18), pady=12, sticky="e"
        )

        self.summary_labels = {}
        summary_container = ctk.CTkFrame(
            self.main_frame, fg_color="#f8fbfa", corner_radius=18
        )
        summary_container.grid(row=1, column=0, pady=(0, 10), sticky="ew")
        for column in range(5):
            summary_container.grid_columnconfigure(column, weight=1)
        for column, (title, key) in enumerate(
            [
                ("Refeições", "meal_count"),
                ("Calorias", "calories"),
                ("Proteínas", "protein_g"),
                ("Carboidratos", "carbs_g"),
                ("Gorduras", "fat_g"),
            ]
        ):
            card = ctk.CTkFrame(
                summary_container,
                fg_color="#ffffff",
                corner_radius=14,
                border_width=1,
                border_color="#dfeae6",
            )
            card.grid(row=0, column=column, padx=5, pady=8, sticky="ew")
            ctk.CTkLabel(
                card, text=title, font=ctk.CTkFont(size=11), text_color="#5d6a66"
            ).pack(anchor="w", padx=9, pady=(9, 0))
            value = ctk.CTkLabel(
                card,
                text="0",
                font=ctk.CTkFont(size=19, weight="bold"),
                text_color="#173b2c",
            )
            value.pack(anchor="w", padx=9, pady=(0, 9))
            self.summary_labels[key] = value

        photo_panel = ctk.CTkFrame(
            self.main_frame, fg_color="#ffffff", corner_radius=18
        )
        photo_panel.grid(row=2, column=0, pady=(0, 10), sticky="ew")
        photo_panel.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            photo_panel,
            text="Analise sua refeição por foto",
            font=ctk.CTkFont(size=18, weight="bold"),
            text_color="#18372d",
        ).grid(row=0, column=0, sticky="w", padx=16, pady=(14, 2))
        ctk.CTkLabel(
            photo_panel,
            text="A estimativa considera o que está visível e pode não identificar ingredientes ocultos.",
            wraplength=760,
            text_color="#5d6a66",
        ).grid(row=1, column=0, sticky="w", padx=16, pady=(0, 8))
        self.choose_photo_button = ctk.CTkButton(
            photo_panel,
            text="Escolher foto do prato",
            command=self._choose_photo,
            fg_color="#eaf4ff",
            hover_color="#dfefff",
            text_color="#1f4d7a",
            border_color="#bed9f5",
            border_width=1,
            height=40,
            corner_radius=13,
        )
        self.choose_photo_button.grid(
            row=2, column=0, padx=16, pady=(0, 6), sticky="ew"
        )
        self.photo_label = ctk.CTkLabel(
            photo_panel,
            text="Nenhuma foto selecionada",
            anchor="w",
            text_color="#4d5a57",
        )
        self.photo_label.grid(
            row=3, column=0, sticky="ew", padx=16, pady=(0, 6)
        )
        self.analyze_button = ctk.CTkButton(
            photo_panel,
            text="Analisar refeição",
            command=self._analyze_photo,
            state="disabled",
            fg_color="#1b8f63",
            hover_color="#157a54",
            height=42,
            corner_radius=14,
        )
        self.analyze_button.grid(
            row=4, column=0, padx=16, pady=(0, 14), sticky="ew"
        )

        result_panel = ctk.CTkFrame(
            self.main_frame, fg_color="#ffffff", corner_radius=18
        )
        result_panel.grid(row=3, column=0, pady=(0, 8), sticky="nsew")
        result_panel.grid_columnconfigure(0, weight=1)
        result_panel.grid_rowconfigure(1, weight=1)
        ctk.CTkLabel(
            result_panel,
            text="Estimativa nutricional e recomendação",
            font=ctk.CTkFont(size=17, weight="bold"),
            text_color="#18372d",
        ).grid(row=0, column=0, sticky="w", padx=14, pady=(12, 6))
        self.result_text = ctk.CTkTextbox(
            result_panel,
            height=150,
            fg_color="#f8fbfa",
            text_color="#173b2c",
            border_color="#dfeae6",
            corner_radius=12,
            wrap="word",
        )
        self.result_text.grid(
            row=1, column=0, padx=12, pady=(0, 12), sticky="nsew"
        )
        self._set_result_text(
            "Escolha uma foto para estimar os alimentos, a porção, as calorias "
            "e os macronutrientes. Revise as limitações antes de registrar."
        )

        self.save_button = ctk.CTkButton(
            self.main_frame,
            text="Registrar estimativa no diário",
            command=self._save_analysis,
            state="disabled",
            height=42,
            corner_radius=14,
            fg_color="#1b8f63",
            hover_color="#157a54",
        )
        self.save_button.grid(row=4, column=0, pady=(0, 8), sticky="ew")

        ctk.CTkLabel(
            self.main_frame,
            text="Refeições de hoje",
            font=ctk.CTkFont(size=17, weight="bold"),
            text_color="#18372d",
        ).grid(row=5, column=0, sticky="w", padx=4, pady=(2, 4))
        self.meal_list = ctk.CTkTextbox(
            self.main_frame,
            height=110,
            fg_color="#ffffff",
            text_color="#173b2c",
            border_color="#dfeae6",
            corner_radius=14,
            wrap="word",
        )
        self.meal_list.grid(row=6, column=0, pady=(0, 6), sticky="nsew")
        self.main_frame.grid_rowconfigure(6, weight=1)
        ctk.CTkLabel(
            self.main_frame,
            text=(
                "Estimativas visuais não são exatas nem substituem orientação "
                "profissional. A foto escolhida é enviada ao serviço de IA "
                "configurado e não é salva no diário."
            ),
            wraplength=820,
            justify="center",
            text_color="#5d6a66",
        ).grid(row=7, column=0, padx=8, pady=(4, 2))
        self._refresh_history()

    def _choose_photo(self) -> None:
        selected = filedialog.askopenfilename(
            parent=self,
            title="Escolha uma foto do prato",
            filetypes=[
                ("Imagens", "*.jpg *.jpeg *.png *.webp"),
                ("Todos os arquivos", "*.*"),
            ],
        )
        if not selected:
            return

        self.pending_analysis = None
        self.selected_photo = None
        self.analyze_button.configure(state="disabled")
        self.save_button.configure(state="disabled")
        self.photo_label.configure(text="Validando imagem selecionada...")
        path = Path(selected)
        if path.suffix.lower() not in IMAGE_TYPES:
            messagebox.showerror(
                "Formato não suportado",
                "Escolha uma imagem JPG, PNG ou WEBP.",
                parent=self,
            )
            self.photo_label.configure(text="Nenhuma foto selecionada")
            self.analyze_button.configure(state="disabled")
            self.save_button.configure(state="disabled")
            return
        try:
            image_size = path.stat().st_size
        except OSError as exc:
            self.photo_label.configure(text="Nenhuma foto selecionada")
            messagebox.showerror(
                "Não foi possível abrir a foto", str(exc), parent=self
            )
            return
        if image_size == 0 or image_size > MAX_IMAGE_BYTES:
            messagebox.showerror(
                "Tamanho de imagem inválido",
                "A foto deve ter tamanho maior que zero e até 10 MB.",
                parent=self,
            )
            self.photo_label.configure(text="Nenhuma foto selecionada")
            self.analyze_button.configure(state="disabled")
            self.save_button.configure(state="disabled")
            return

        self.selected_photo = path
        self.photo_label.configure(text=f"Foto selecionada: {path.name}")
        self.analyze_button.configure(state="normal")
        self.save_button.configure(state="disabled")
        self._set_result_text(
            "Foto pronta. A análise vai estimar a refeição usando seu objetivo "
            "como contexto geral. A imagem será enviada ao serviço de IA."
        )

    def _analyze_photo(self) -> None:
        if self.selected_photo is None or self.account is None:
            return
        mime_type = IMAGE_TYPES[self.selected_photo.suffix.lower()]
        try:
            image = self.selected_photo.read_bytes()
        except OSError as exc:
            messagebox.showerror(
                "Não foi possível abrir a foto", str(exc), parent=self
            )
            return
        if not image or len(image) > MAX_IMAGE_BYTES:
            messagebox.showerror(
                "Tamanho de imagem inválido",
                "A foto deve ter tamanho maior que zero e até 10 MB.",
                parent=self,
            )
            return

        self.pending_analysis = None
        self._set_analysis_busy(True)
        self._set_result_text(
            "Analisando a imagem. As quantidades e os nutrientes são estimativas..."
        )
        threading.Thread(
            target=self._analysis_worker,
            args=(image, mime_type, self.account.goal, self.account.id),
            daemon=True,
        ).start()

    def _analysis_worker(
        self,
        image: bytes,
        mime_type: str,
        goal: str,
        account_id: int,
    ) -> None:
        api_key, api_url, model = get_ai_settings()
        try:
            analysis = analyze_plate_image(
                api_key=api_key,
                api_url=api_url,
                model=model,
                image=image,
                mime_type=mime_type,
                goal=goal,
            )
        except AIServiceError as exc:
            self.after(0, self._show_analysis_error, account_id, str(exc))
            return
        self.after(0, self._show_analysis, account_id, analysis)

    def _show_analysis_error(self, account_id: int, message: str) -> None:
        if self.account is None or self.account.id != account_id:
            return
        self._set_result_text(f"Não foi possível analisar a foto.\n\n{message}")
        self._set_analysis_busy(False)
        messagebox.showerror("Análise indisponível", message, parent=self)

    def _show_analysis(
        self, account_id: int, analysis: PlateAnalysis
    ) -> None:
        if self.account is None or self.account.id != account_id:
            return
        self.pending_analysis = analysis
        self._set_result_text(
            f"Prato identificado: {analysis['dish_name']}\n"
            f"Alimentos visíveis: {', '.join(analysis['foods'])}\n"
            f"Porção total aproximada: {analysis['portion_g']:g} g\n"
            f"Calorias estimadas: {analysis['calories']:g} kcal\n"
            f"Proteínas: {analysis['protein_g']:g} g  |  "
            f"Carboidratos: {analysis['carbs_g']:g} g  |  "
            f"Gorduras: {analysis['fat_g']:g} g\n"
            f"Confiança da identificação: {analysis['confidence']}\n\n"
            f"Limitações: {analysis['uncertainty']}\n\n"
            f"Sugestão geral para seu objetivo: {analysis['recommendation']}"
        )
        self._set_analysis_busy(False)

    def _save_analysis(self) -> None:
        if self.pending_analysis is None or self.account is None:
            return
        analysis = self.pending_analysis
        try:
            with SessionFactory() as session:
                add_meal(
                    session,
                    account_id=self.account.id,
                    name=analysis["dish_name"],
                    amount_g=analysis["portion_g"],
                    calories=analysis["calories"],
                    protein_g=analysis["protein_g"],
                    carbs_g=analysis["carbs_g"],
                    fat_g=analysis["fat_g"],
                )
        except (ValueError, SQLAlchemyError) as exc:
            messagebox.showerror(
                "Não foi possível registrar a refeição", str(exc), parent=self
            )
            return

        self.pending_analysis = None
        self.selected_photo = None
        self.photo_label.configure(text="Nenhuma foto selecionada")
        self.analyze_button.configure(state="disabled")
        self.save_button.configure(state="disabled")
        self._set_result_text(
            "Estimativa registrada no diário. Você pode escolher outra foto "
            "para analisar a próxima refeição."
        )
        self._refresh_history()

    def _set_analysis_busy(self, busy: bool) -> None:
        self.analysis_in_progress = busy
        self.choose_photo_button.configure(
            state="disabled" if busy else "normal"
        )
        self.analyze_button.configure(
            state="disabled" if busy or self.selected_photo is None else "normal",
            text="Analisando foto..." if busy else "Analisar refeição",
        )
        self.save_button.configure(
            state="normal" if not busy and self.pending_analysis is not None else "disabled"
        )
        self.change_goal_button.configure(state="disabled" if busy else "normal")
        self.logout_button.configure(state="disabled" if busy else "normal")

    def _set_result_text(self, text: str) -> None:
        self.result_text.configure(state="normal")
        self.result_text.delete("1.0", "end")
        self.result_text.insert("1.0", text)
        self.result_text.configure(state="disabled")

    def _refresh_history(self) -> None:
        if self.account is None:
            return
        with SessionFactory() as session:
            meals = list_meals(
                session,
                date.today(),
                account_id=self.account.id,
            )
        totals = summarize_meals(meals)
        self.summary_labels["meal_count"].configure(
            text=str(int(totals["meal_count"]))
        )
        self.summary_labels["calories"].configure(
            text=f"{totals['calories']:g} kcal"
        )
        self.summary_labels["protein_g"].configure(
            text=f"{totals['protein_g']:g} g"
        )
        self.summary_labels["carbs_g"].configure(
            text=f"{totals['carbs_g']:g} g"
        )
        self.summary_labels["fat_g"].configure(
            text=f"{totals['fat_g']:g} g"
        )
        lines = [
            f"{meal.eaten_at:%H:%M}  ·  {meal.name} ({meal.amount_g:g} g)  ·  "
            f"{meal.calories:g} kcal  ·  P {meal.protein_g:g} g  ·  "
            f"C {meal.carbs_g:g} g  ·  G {meal.fat_g:g} g"
            for meal in meals
        ]
        self.meal_list.configure(state="normal")
        self.meal_list.delete("1.0", "end")
        self.meal_list.insert(
            "1.0",
            "\n".join(lines) or "Nenhuma refeição registrada hoje.",
        )
        self.meal_list.configure(state="disabled")

    def _edit_goal(self) -> None:
        if self.account is not None and not self.analysis_in_progress:
            self._show_goal_setup(self.account.goal)

    def _logout(self) -> None:
        if self.analysis_in_progress:
            return
        self.account = None
        self.selected_photo = None
        self.pending_analysis = None
        self._show_auth_screen("login")


if __name__ == "__main__":
    FitFoodApp().mainloop()
