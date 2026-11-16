# Roteiro para vídeo de apresentação (3–5 minutos)

## 0:00–0:30 — O problema

Apresente a dificuldade de registrar refeições e acompanhar os macros ao longo da rotina de treino. Explique que o FitFood ajuda a organizar esses registros, mas não substitui aconselhamento nutricional.

## 0:30–1:15 — O projeto

Mostre o repositório no GitHub e explique brevemente as partes: interface desktop em Python, banco SQLite local e API FastAPI. Mostre o `.gitignore` e o `.env.example`; não abra nem grave arquivos `.env`, chaves ou tokens.

## 1:15–2:30 — Instalação e uso

No terminal, crie e ative o ambiente virtual, instale `requirements.txt` e execute `python -m src.app`. Registre uma refeição de exemplo e mostre o resumo diário de calorias, proteínas, carboidratos e gorduras.

## 2:30–3:30 — API e testes

Inicie `uvicorn src.api:app --reload`, abra `/docs` e mostre `/health`. Explique que os endpoints de dados exigem `APP_API_TOKEN`. Rode `python -m unittest discover -s tests -v`.

## 3:30–4:30 — IA e próximos passos

Explique que sugestões são opcionais, dependem de uma chave configurada localmente e são apenas gerais. Mostre a landing page e as artes em `docs/`. Informe que o deploy público e o instalador só devem ser anunciados depois de publicados e testados.

## Antes de gravar

- Grave a tela inteira do terminal e do aplicativo, sem mostrar credenciais.
- Use apenas dados fictícios de refeição.
- Confirme que não há `.env` nem banco de dados local no commit.
- Diga claramente que a IA pode errar e não oferece orientação médica.
