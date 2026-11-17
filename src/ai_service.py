import base64
import json
import math
from typing import TypedDict

import requests


class AIServiceError(RuntimeError):
    pass


class PlateAnalysis(TypedDict):
    dish_name: str
    foods: list[str]
    portion_g: float
    calories: float
    protein_g: float
    carbs_g: float
    fat_g: float
    confidence: str
    uncertainty: str
    recommendation: str


def analyze_plate_image(
    *,
    api_key: str | None,
    api_url: str,
    model: str,
    image: bytes,
    mime_type: str,
    goal: str,
) -> PlateAnalysis:
    if not api_key:
        raise AIServiceError(
            "Análise de fotos não configurada. Defina AI_API_KEY no arquivo .env."
        )
    if not image or len(image) > 10 * 1024 * 1024:
        raise AIServiceError("A imagem deve ter até 10 MB.")
    if mime_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise AIServiceError("Use uma imagem JPG, PNG ou WEBP.")

    image_url = (
        f"data:{mime_type};base64,{base64.b64encode(image).decode('ascii')}"
    )
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "Você analisa fotos de refeições e responde em português. "
                    "Faça uma estimativa visual, nunca afirme exatidão: porções, "
                    "ingredientes, preparo, calorias e nutrientes não podem ser "
                    "determinados com precisão só pela foto. Não invente detalhes "
                    "que não estejam visíveis; indique incertezas e use confiança "
                    "baixa quando a identificação ou a porção forem duvidosas. "
                    "Produza somente um objeto JSON válido, sem Markdown, com as "
                    "chaves dish_name (string até 120 caracteres), foods (lista "
                    "de nomes visíveis), portion_g, calories, protein_g, carbs_g, "
                    "fat_g (números não negativos), confidence (baixa, média ou "
                    "alta), uncertainty (limitações da estimativa) e "
                    "recommendation (uma sugestão geral, educativa e breve). "
                    "A recomendação pode considerar o objetivo informado, mas "
                    "não prescreva dietas, metas calóricas ou tratamentos; não "
                    "diagnostique nem faça alegações médicas. Para necessidades "
                    "individuais, recomende um nutricionista."
                ),
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Analise a refeição da foto. Estime os alimentos "
                            "visíveis, porção total em gramas, calorias, proteínas, "
                            "carboidratos e gorduras. Leve em conta apenas como "
                            "contexto geral o objetivo da pessoa: "
                            f"{goal}. Não presuma ingredientes invisíveis e "
                            "informe as limitações da estimativa."
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {"url": image_url, "detail": "high"},
                    },
                ],
            },
        ],
        "temperature": 0.2,
    }

    try:
        response = requests.post(
            api_url,
            headers={"Authorization": f"Bearer {api_key}"},
            json=payload,
            timeout=60,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise AIServiceError(f"Falha ao analisar a foto: {exc}") from exc

    try:
        body = response.json()
        content = body["choices"][0]["message"]["content"]
        decoded = json.loads(content)
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        raise AIServiceError(
            "O serviço de IA retornou uma análise inválida."
        ) from exc

    return _validate_plate_analysis(decoded)


def _validate_plate_analysis(value: object) -> PlateAnalysis:
    if not isinstance(value, dict):
        raise AIServiceError("O serviço de IA retornou uma análise inválida.")

    dish_name = _required_text(value, "dish_name", max_length=120)
    foods_value = value.get("foods")
    if (
        not isinstance(foods_value, list)
        or not foods_value
        or len(foods_value) > 15
        or any(not isinstance(food, str) or not food.strip() for food in foods_value)
    ):
        raise AIServiceError("A lista de alimentos da análise é inválida.")

    confidence = value.get("confidence")
    if (
        not isinstance(confidence, str)
        or confidence not in {"baixa", "média", "alta"}
    ):
        raise AIServiceError("O nível de confiança da análise é inválido.")

    portion_g = _required_number(value, "portion_g", maximum=100000, minimum=0.1)
    calories = _required_number(value, "calories", maximum=100000)
    protein_g = _required_number(value, "protein_g", maximum=10000)
    carbs_g = _required_number(value, "carbs_g", maximum=10000)
    fat_g = _required_number(value, "fat_g", maximum=10000)
    return {
        "dish_name": dish_name,
        "foods": [food.strip()[:80] for food in foods_value],
        "portion_g": portion_g,
        "calories": calories,
        "protein_g": protein_g,
        "carbs_g": carbs_g,
        "fat_g": fat_g,
        "confidence": confidence,
        "uncertainty": _required_text(value, "uncertainty", max_length=1000),
        "recommendation": _required_text(
            value, "recommendation", max_length=2000
        ),
    }


def _required_text(value: dict, key: str, *, max_length: int) -> str:
    text = value.get(key)
    if not isinstance(text, str) or not text.strip() or len(text.strip()) > max_length:
        raise AIServiceError("O serviço de IA retornou uma análise inválida.")
    return text.strip()


def _required_number(
    value: dict, key: str, *, maximum: float, minimum: float = 0
) -> float:
    number = value.get(key)
    if (
        isinstance(number, bool)
        or not isinstance(number, (int, float))
        or number < minimum
        or number > maximum
        or not math.isfinite(number)
    ):
        raise AIServiceError("O serviço de IA retornou uma análise inválida.")
    return float(number)


def chat_with_assistant(
    *,
    api_key: str | None,
    api_url: str,
    model: str,
    messages: list[dict[str, str]],
) -> str:
    if not api_key:
        raise AIServiceError(
            "Chatbot não configurado. Defina AI_API_KEY no arquivo .env."
        )

    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "Você é o assistente do FitFood e conversa em português, de "
                    "forma acolhedora e objetiva. Ofereça apenas informações gerais "
                    "e educativas sobre alimentação e atividade física. Não "
                    "diagnostique, não prescreva dietas, metas calóricas ou "
                    "tratamentos, e não recomende práticas extremas. Não afirme "
                    "que os dados informados permitem avaliar a saúde da pessoa. "
                    "Para questões médicas ou necessidades individuais, recomende "
                    "procurar um nutricionista ou profissional de saúde."
                ),
            },
            *messages[-20:],
        ],
        "temperature": 0.6,
    }

    try:
        response = requests.post(
            api_url,
            headers={"Authorization": f"Bearer {api_key}"},
            json=payload,
            timeout=30,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise AIServiceError(f"Falha ao consultar o serviço de IA: {exc}") from exc

    try:
        body = response.json()
        answer = body["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        raise AIServiceError("O serviço de IA retornou uma resposta inválida.") from exc

    if not isinstance(answer, str) or not answer.strip():
        raise AIServiceError("O serviço de IA retornou uma resposta vazia.")
    return answer.strip()


def suggest_meals(
    *,
    api_key: str | None,
    api_url: str,
    model: str,
    summary: dict[str, float | int],
) -> str:
    if not api_key:
        raise AIServiceError(
            "Sugestões de IA não configuradas. Defina AI_API_KEY no arquivo .env."
        )

    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "Você oferece apenas ideias gerais e educativas de refeições "
                    "para adultos que praticam atividade física. Não diagnostique, "
                    "não prescreva dietas nem metas calóricas, e não trate condições "
                    "médicas. Recomende um nutricionista para necessidades "
                    "individualizadas. Responda em português, de forma breve."
                ),
            },
            {
                "role": "user",
                "content": (
                    "Com base apenas neste resumo de consumo informado pelo usuário, "
                    "sugira opções gerais de alimentos para variar as próximas "
                    f"refeições. Não conclua que há deficiência ou excesso: {summary}"
                ),
            },
        ],
        "temperature": 0.6,
    }

    try:
        response = requests.post(
            api_url,
            headers={"Authorization": f"Bearer {api_key}"},
            json=payload,
            timeout=30,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise AIServiceError(f"Falha ao consultar o serviço de IA: {exc}") from exc

    try:
        body = response.json()
        suggestion = body["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        raise AIServiceError("O serviço de IA retornou uma resposta inválida.") from exc

    if not isinstance(suggestion, str) or not suggestion.strip():
        raise AIServiceError("O serviço de IA retornou uma sugestão vazia.")
    return suggestion.strip()
