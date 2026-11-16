# FitFood

Aplicativo inicial para pessoas que treinam registrarem refeições e acompanharem calorias e macronutrientes. As sugestões de IA são gerais e educativas; o aplicativo não substitui orientação de nutricionista ou profissional de saúde.

## Recursos

- Registro local de refeições com calorias, proteínas, carboidratos e gorduras.
- Resumo diário e histórico armazenados em SQLite.
- Interface desktop com CustomTkinter.
- API FastAPI para refeições, resumo e sugestões opcionais de IA.
- Proteção dos endpoints de dados com token Bearer.

## Requisitos

- Python 3.10 ou superior.
- Windows, macOS ou Linux.
- Uma chave de API compatível com a API de chat da OpenAI somente para usar as sugestões de IA.

## Instalação e execução

No PowerShell, na pasta do projeto:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
python -m src.app
```

No macOS/Linux, ative o ambiente com `source .venv/bin/activate` e execute `python -m src.app`.

O banco local é criado automaticamente na pasta de dados do usuário (`%LOCALAPPDATA%\FitFood\fitfood.db` no Windows). Para habilitar sugestões de IA no desenvolvimento, preencha `AI_API_KEY` no `.env`; no app instalado, use `%LOCALAPPDATA%\FitFood\.env`. Nunca versione esse arquivo.

## API local

Defina um token forte em `APP_API_TOKEN` no `.env` e inicie a API:

```powershell
uvicorn src.api:app --reload
```

Abra `http://127.0.0.1:8000/docs`. Envie o token no cabeçalho `Authorization: Bearer <token>` para acessar refeições, resumo e sugestões. O endpoint `GET /health` não exige autenticação.

Para registrar uma refeição, use `POST /meals` com JSON semelhante a:

```json
{
  "name": "Iogurte com frutas",
  "amount_g": 200,
  "calories": 180,
  "protein_g": 12,
  "carbs_g": 25,
  "fat_g": 4
}
```

## Testes

```powershell
python -m unittest discover -s tests -v
```

## Empacotamento desktop

Instale as dependências de desenvolvimento e gere um executável:

```powershell
pip install -r requirements-dev.txt
pyinstaller --noconsole --onefile --name FitFood --paths . src\app.py
```

O executável ficará em `dist\FitFood.exe`. Com o Inno Setup instalado, abra `installer\FitFood.iss` e compile o instalador. Teste-o em outro diretório antes de distribuir. O banco de dados é salvo na pasta de dados do usuário, não em `Program Files`.

## Deploy e publicação

O arquivo `render.yaml` prepara um serviço gratuito de demonstração no Render. Conecte o repositório no Render e crie um Blueprint para iniciar o deploy. O endpoint `/health` não exige autenticação; os demais endpoints exigem o `APP_API_TOKEN` gerado pelo Render.

O plano gratuito usa armazenamento efêmero: o SQLite poderá ser apagado quando a instância reiniciar ou for substituída. Não use esse deploy para guardar dados reais. Para uso contínuo, configure um banco gerenciado ou armazenamento persistente antes de publicar a API. Nunca publique `.env`, tokens ou chaves.

A página inicial e materiais de divulgação estão em `docs/`. O instalador e a API pública só devem ser anunciados depois de compilados, testados e publicados.

O executável beta para Windows está no [release v0.1.0](https://github.com/heitorcarvalhosouza2010-hub/curso/releases/tag/v0.1.0). O instalador Inno Setup e o deploy Render ainda estão pendentes.

## Uso responsável

O aplicativo registra dados informados pelo usuário; não verifica a precisão nutricional dos alimentos. Sugestões de IA não são aconselhamento médico, não prescrevem dietas e podem conter erros. Procure um nutricionista para recomendações personalizadas, especialmente em caso de condições de saúde, alergias ou histórico de transtornos alimentares.
