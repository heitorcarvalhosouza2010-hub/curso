# FitFood

Aplicativo desktop para analisar refeições a partir de fotos e acompanhar estimativas de calorias e macronutrientes. As estimativas e recomendações de IA são gerais, podem conter erros e não substituem orientação de nutricionista ou profissional de saúde.

## Recursos

- Análise por IA de fotos de pratos em JPG, PNG ou WEBP, com limite de 10 MB.
- Estimativa visual de alimentos, porção, calorias, proteínas, carboidratos e gorduras, com indicação de incerteza e recomendação educativa relacionada ao objetivo informado.
- Revisão da estimativa antes de registrá-la no diário.
- Cadastro e login locais por email e senha; a escolha do objetivo físico ocorre em uma etapa separada e pode ser alterada depois.
- Histórico diário separado por conta.
- Resumo diário e histórico armazenados em SQLite.
- Interface desktop com CustomTkinter.
- API FastAPI para refeições, resumo e sugestões opcionais de IA.
- Proteção dos endpoints de dados com token Bearer.

## Requisitos

- Python 3.10 ou superior.
- Windows, macOS ou Linux.
- Uma chave de API compatível com a API de chat da OpenAI e um modelo com suporte a imagens para analisar fotos.

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

O banco local é criado automaticamente na pasta de dados do usuário (`%LOCALAPPDATA%\FitFood\fitfood.db` no Windows). Para habilitar a análise de fotos, preencha `AI_API_KEY` no `.env`; no app instalado, use `%LOCALAPPDATA%\FitFood\.env`. Nunca versione esse arquivo. O modelo configurado em `AI_MODEL` precisa aceitar imagens; o padrão `gpt-4o-mini` é compatível com imagens.

O cadastro usa email como identificador local e não verifica a posse do email nem oferece recuperação ou sincronização de senha. A senha é armazenada como hash derivado, não em texto simples. O login bloqueia a interface do aplicativo, mas não protege os arquivos do computador contra acesso direto. As contas e refeições permanecem no dispositivo.

Quando você escolhe analisar uma foto, ela é enviada ao serviço configurado por `AI_API_URL` para processamento. A imagem não é guardada no diário. O resultado é uma estimativa visual: peso, ingredientes ocultos e método de preparo não podem ser identificados com precisão só pela foto. Revise as incertezas antes de registrar. Recomendações são gerais, não prescrevem dietas ou metas e não substituem orientação profissional.

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

## Android

O projeto Android fica em `android/`. A versão 0.4.0 inclui uma interface visual renovada e permite criar conta local, escolher um objetivo, conversar com o chatbot Gemini, analisar fotos de refeições, revisar os valores antes de salvar e consultar o histórico do dia. Também pode estimar uma meta diária de calorias para adultos com a equação Mifflin-St Jeor, usando idade, peso, altura, fórmula metabólica e atividade; o objetivo altera levemente essa referência. É uma estimativa geral, não uma prescrição, e resultados abaixo de 1.200 kcal/dia não são exibidos. Contas, perfil e refeições ficam no celular e não sincronizam com o aplicativo Windows.

O chatbot usa o modelo multimodal Gemini 3.5 Flash-Lite na cota gratuita da API Google Gemini. Para evitar cobranças, crie a chave no [Google AI Studio](https://aistudio.google.com/apikey) e não associe faturamento ao projeto. A cota tem limites que podem mudar ou ficar indisponíveis. Mensagens e fotos são enviadas ao Google; no nível gratuito, o conteúdo pode ser usado para melhorar produtos. O aplicativo pede consentimento antes do primeiro envio e armazena a chave criptografada no Android Keystore.

Para instalar no celular:

1. Abra o [release v0.4.0](https://github.com/heitorcarvalhosouza2010-hub/curso/releases/tag/v0.4.0) no Android e baixe `FitFood-Android-v0.4.0.apk`.
2. Abra o APK baixado. Se solicitado, permita que o navegador ou o app Arquivos instale aplicativos desconhecidos e confirme a instalação.
3. Instale somente o APK publicado no release oficial do projeto.

O APK é uma versão beta distribuída fora da Play Store e requer Android 6.0 ou superior. Configure no app sua chave gratuita do Google AI Studio. Não associe faturamento ao projeto para manter o uso na cota gratuita; a cota é limitada e pode mudar. Mensagens e fotos podem ser usadas pelo Google para melhorar produtos. A compilação e publicação são feitas pelo GitHub Actions.

## Deploy e publicação

O arquivo `render.yaml` prepara um serviço gratuito de demonstração no Render. Conecte o repositório no Render e crie um Blueprint para iniciar o deploy. O endpoint `/health` não exige autenticação; os demais endpoints exigem o `APP_API_TOKEN` gerado pelo Render.

O plano gratuito usa armazenamento efêmero: o SQLite poderá ser apagado quando a instância reiniciar ou for substituída. Não use esse deploy para guardar dados reais. Para uso contínuo, configure um banco gerenciado ou armazenamento persistente antes de publicar a API. Nunca publique `.env`, tokens ou chaves.

A página inicial e materiais de divulgação estão em `docs/`. O instalador e a API pública só devem ser anunciados depois de compilados, testados e publicados.

O instalador beta para Windows e o executável portátil estão no [release v0.1.0](https://github.com/heitorcarvalhosouza2010-hub/curso/releases/tag/v0.1.0). O APK Android atualizado está no [release v0.4.0](https://github.com/heitorcarvalhosouza2010-hub/curso/releases/tag/v0.4.0). O deploy Render ainda está pendente.

## Uso responsável

O aplicativo registra dados informados pelo usuário; não verifica a precisão nutricional dos alimentos. Sugestões de IA não são aconselhamento médico, não prescrevem dietas e podem conter erros. Procure um nutricionista para recomendações personalizadas, especialmente em caso de condições de saúde, alergias ou histórico de transtornos alimentares.
