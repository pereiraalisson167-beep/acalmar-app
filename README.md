# Acalmar

Aplicativo Android da **Massoterapia Acalmar** para organizar o dia a dia do atendimento:
agenda, clientes, pacotes de sessões e caixa.

## Instalar ou atualizar

1. No celular, abra a página **[Releases](../../releases/latest)** deste repositório.
2. Baixe o arquivo **acalmar.apk** e toque nele para instalar.
3. Se o Android pedir, permita a instalação por esta fonte.

Cada versão nova instala por cima da anterior e mantém os dados.

## O que a versão 1 faz

- **Agenda**: semana e dia, horários livres, bloqueio de horários, sessões que se repetem,
  concluir / faltou / desmarcou.
- **Clientes**: cadastro, aniversariantes do mês, histórico, atalho para o WhatsApp.
- **Serviços e pacotes**: preços, duração e pacotes de sessões com controle de uso.
- **Caixa**: entradas e saídas do dia, formas de pagamento e valores a receber.
- **Backup**: cópia de todos os dados em um arquivo (por exemplo, no Google Drive).

Os dados ficam somente no celular. O backup deve ser feito pelo menos uma vez por semana, em **Mais > Backup**.

## Para quem desenvolve

- Kotlin, Jetpack Compose (Material 3) e Room. Código em `app/src/main/java/br/com/acalmar/agenda`.
- Cada envio para `main` gera o APK assinado e publica um Release (`.github/workflows/build.yml`).
- O workflow `ui-check.yml` abre o app em um emulador, percorre as telas (`ci/ui/run.py`) e publica as
  imagens no ramo `ui-screens`.
- A chave de assinatura fica em `keystore/`, protegida pela senha do segredo `KEYSTORE_PASSWORD`.
  Sem a mesma chave e senha, as atualizações não instalam por cima da versão anterior.
