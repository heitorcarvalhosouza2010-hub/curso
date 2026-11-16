import requests


class AIServiceError(RuntimeError):
    pass


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
