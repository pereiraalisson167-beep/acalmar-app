#!/usr/bin/env bash
# Prepara a chave que assina o APK.
#
# - A senha vem do segredo KEYSTORE_PASSWORD do repositório (só o dono a conhece).
# - Na primeira vez, a chave é criada aqui mesmo e gravada em keystore/, protegida por essa senha.
# - Sem o segredo, o build continua com uma assinatura temporária (serve só para teste).
set -euo pipefail

KS="keystore/acalmar-release.p12"
out() { echo "$1" >> "${GITHUB_OUTPUT:-/dev/null}"; }

if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  out "signed=false"
  echo "::warning::Segredo KEYSTORE_PASSWORD não cadastrado: APK com assinatura temporária (apenas para teste)."
  exit 0
fi

if [ "${#KEYSTORE_PASSWORD}" -lt 12 ]; then
  echo "::error::A senha em KEYSTORE_PASSWORD é curta demais (mínimo de 12 caracteres)."
  exit 1
fi

if [ ! -f "$KS" ]; then
  if [ "${ALLOW_KEY_CREATION:-false}" != "true" ]; then
    out "signed=false"
    echo "::warning::A chave de assinatura ainda não existe; ela é criada pelo workflow principal."
    exit 0
  fi
  echo "Criando a chave de assinatura do app..."
  mkdir -p keystore
  keytool -genkeypair -alias acalmar -keyalg RSA -keysize 2048 -validity 18250 \
    -storetype PKCS12 -keystore "$KS" \
    -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD \
    -dname "CN=Acalmar Massoterapia, O=Acalmar, C=BR"
  git config user.name "github-actions[bot]"
  git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
  git add "$KS"
  git commit -m "Adiciona a chave de assinatura do app (protegida por senha)"
  if ! git push origin "HEAD:${GITHUB_REF_NAME}"; then
    git fetch origin "${GITHUB_REF_NAME}"
    git rebase "origin/${GITHUB_REF_NAME}"
    git push origin "HEAD:${GITHUB_REF_NAME}"
  fi
fi

if ! keytool -list -keystore "$KS" -storetype PKCS12 -storepass:env KEYSTORE_PASSWORD -alias acalmar > /dev/null 2>&1; then
  echo "::error::A senha em KEYSTORE_PASSWORD não abre a chave de assinatura existente. Use a mesma senha cadastrada da primeira vez."
  exit 1
fi

echo "Chave de assinatura pronta."
keytool -list -v -keystore "$KS" -storetype PKCS12 -storepass:env KEYSTORE_PASSWORD -alias acalmar 2>/dev/null | grep -E "SHA256:" || true
out "signed=true"
