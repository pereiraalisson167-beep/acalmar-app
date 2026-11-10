#!/usr/bin/env bash
# Quando o build falha, publica um resumo dos erros como comentário no commit,
# para que a causa possa ser lida sem baixar o log inteiro.
set -uo pipefail

LOG="${1:-build.log}"
{
  echo "### Build falhou: ${GITHUB_WORKFLOW:-workflow} (run ${GITHUB_RUN_ID:-?})"
  echo
  echo '```'
  if [ -f "$LOG" ]; then
    grep -nE '^e: |^w: \[ksp\]|\[ksp\]|error:|Unresolved reference|^FAILURE|\* What went wrong|AAPT:|Execution failed|FAILED$' "$LOG" \
      | cut -c1-700 | head -n 220
    echo '---- what went wrong ----'
    awk '/\* What went wrong:/{f=1} /\* Try:/{f=0} f' "$LOG" | cut -c1-700 | head -n 80
    echo '---- fim do log ----'
    tail -n 30 "$LOG" | cut -c1-400
  else
    echo "(arquivo $LOG não encontrado)"
  fi
  echo '```'
} > report.md

head -c 60000 report.md > report-trim.md
gh api "repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}/comments" -F body=@report-trim.md > /dev/null \
  && echo "Resumo do erro publicado no commit ${GITHUB_SHA}."
