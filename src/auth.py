import hashlib
import hmac
import re
import secrets

from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from src.models import Account, Meal

FITNESS_GOALS = (
    "Ganhar massa muscular",
    "Perder gordura",
    "Manter o peso",
    "Melhorar o desempenho",
    "Ter uma alimentação equilibrada",
)
PASSWORD_ITERATIONS = 600_000
EMAIL_PATTERN = re.compile(r"^[^@\s]{1,64}@[^@\s]{1,255}$")


def has_accounts(session: Session) -> bool:
    return session.scalar(select(Account.id).limit(1)) is not None


def create_account(
    session: Session,
    *,
    email: str,
    password: str,
    goal: str = FITNESS_GOALS[-1],
) -> Account:
    cleaned_email = email.strip().lower()
    if not EMAIL_PATTERN.fullmatch(cleaned_email) or len(cleaned_email) > 254:
        raise ValueError("Informe um email válido.")
    if len(password) < 8:
        raise ValueError("A senha deve ter pelo menos 8 caracteres.")
    if goal not in FITNESS_GOALS:
        raise ValueError("Selecione um objetivo da lista.")

    existing_account = session.scalar(
        select(Account.id).where(Account.email == cleaned_email)
    )
    if existing_account is not None:
        raise ValueError("Este email já está cadastrado.")

    first_account = not has_accounts(session)
    salt = secrets.token_bytes(16)
    account = Account(
        email=cleaned_email,
        password_salt=salt.hex(),
        password_hash=_password_digest(password, salt),
        goal=goal,
        goal_completed=False,
    )
    session.add(account)
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise ValueError("Este email já está cadastrado.") from exc
    session.refresh(account)

    if first_account:
        session.execute(
            update(Meal).where(Meal.account_id.is_(None)).values(account_id=account.id)
        )
        session.commit()
    return account


def update_account_goal(session: Session, *, account_id: int, goal: str) -> Account:
    if goal not in FITNESS_GOALS:
        raise ValueError("Selecione um objetivo da lista.")
    account = session.get(Account, account_id)
    if account is None:
        raise ValueError("A conta não foi encontrada.")
    account.goal = goal
    account.goal_completed = True
    session.commit()
    session.refresh(account)
    return account


def authenticate_account(
    session: Session, *, email: str, password: str
) -> Account | None:
    cleaned_email = email.strip().lower()
    account = session.scalar(
        select(Account).where(Account.email == cleaned_email)
    )
    if account is None:
        return None

    try:
        salt = bytes.fromhex(account.password_salt)
    except ValueError:
        return None
    candidate_hash = _password_digest(password, salt)
    if not hmac.compare_digest(candidate_hash, account.password_hash):
        return None
    return account


def _password_digest(password: str, salt: bytes) -> str:
    return hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt,
        PASSWORD_ITERATIONS,
    ).hex()
