# Acalmar

Aplicativo Android da **Massoterapia Acalmar** para organizar o dia a dia do atendimento:
agenda, clientes, pacotes de sessões e caixa.

## Instalar ou atualizar

1. No celular, abra a página **[Releases](../../releases/latest)** deste repositório.
2. Baixe o arquivo **acalmar.apk** e toque nele para instalar.
3. Se o Android pedir, permita a instalação por esta fonte.

Cada versão nova instala por cima da anterior e mantém os dados.

## O que o app faz

**Agenda**
- Semana e dia, horários livres, bloqueio de horários (período, dia inteiro ou vários dias).
- Sessões que se repetem, concluir / faltou / desmarcou, lembrete pelo WhatsApp.
- Aviso de atenção no horário quando a ficha de anamnese da cliente tem algo importante (ex.: alergia).

**Clientes**
- Cadastro, busca, aniversariantes do mês e como a cliente conheceu.
- Ficha de anamnese (modelo padrão de massoterapia e estética, editável em Mais > Fichas e termo).
- Termo de consentimento assinado com o dedo na tela, com autorização de uso de imagem e PDF para enviar.
- Fotos de antes e depois, guardadas só dentro do app, com montagem lado a lado para enviar.
- Histórico e evolução: anotação do que foi feito em cada sessão.
- Hora de chamar de volta: lista de quem passou do tempo de retorno do serviço, com mensagem pronta no WhatsApp.

**Vendas e caixa**
- Serviços, pacotes de sessões e controle das sessões usadas.
- Orçamentos com o logo, enviados como imagem ou texto pelo WhatsApp.
- Caixa do dia: entradas, saídas, formas de pagamento, resumo do mês e valores a receber.

**Backup**
- Cópia de todos os dados e fotos em um arquivo .zip (por exemplo, no Google Drive).

Os dados ficam somente no celular. O backup deve ser feito pelo menos uma vez por semana, em **Mais > Backup**.

## Para quem desenvolve

- Kotlin, Jetpack Compose (Material 3) e Room. Código em `app/src/main/java/br/com/acalmar/agenda`.
- Cada envio para `main` gera o APK assinado e publica um Release (`.github/workflows/build.yml`).
- O workflow `ui-check.yml` instala a versão publicada em um emulador, cria dados, instala a versão nova
  por cima, percorre as telas (`ci/ui/run.py`) e publica as imagens no ramo `ui-screens`.
- A estrutura de cada versão do banco fica em `app/schemas` (usada pelo Room para atualizar o banco sem
  perder dados). Ao mudar tabelas: aumente a versão em `AppDatabase.kt` e adicione a migração.
- A chave de assinatura fica em `keystore/`, protegida pela senha do segredo `KEYSTORE_PASSWORD`.
  Sem a mesma chave e senha, as atualizações não instalam por cima da versão anterior.
