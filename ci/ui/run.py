#!/usr/bin/env python3
"""Percorre as telas principais do app no emulador, salvando uma imagem de cada uma.

Usado pelo workflow "Conferir telas": instala o APK, simula os toques de um dia de uso
(cadastrar cliente, agendar, concluir, vender pacote, caixa, configurações) e registra
qualquer erro que feche o app.
"""
import datetime
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = "ui-out"
PKG = "br.com.acalmar.agenda"
os.makedirs(OUT, exist_ok=True)
steps = open(os.path.join(OUT, "steps.txt"), "w")
failures = []


def adb(*args, timeout=90):
    try:
        return subprocess.run(("adb",) + args, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(args, 1, b"", b"timeout")


def note(msg):
    print(msg, flush=True)
    steps.write(msg + "\n")
    steps.flush()


def nodes():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "ignore")
        try:
            return list(ET.fromstring(raw).iter("node"))
        except ET.ParseError:
            time.sleep(1)
    return []


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(text=None, regex=None):
    found = []
    for n in nodes():
        for value in (n.get("text") or "", n.get("content-desc") or ""):
            if not value:
                continue
            if (regex and re.fullmatch(regex, value)) or (text is not None and value == text):
                found.append(n)
                break
    return found


def tap(text=None, regex=None, last=False, wait=12, optional=False):
    label = text or regex
    deadline = time.time() + wait
    while True:
        found = find(text, regex)
        if found:
            x, y = center(found[-1] if last else found[0])
            adb("shell", "input", "tap", str(x), str(y))
            note(f"toque: {label} ({x},{y})")
            time.sleep(1.5)
            return True
        # Aviso do sistema ("... isn't responding") por cima do app: manda esperar e segue.
        waiting = find("Wait")
        if waiting:
            x, y = center(waiting[0])
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(2)
            continue
        if time.time() > deadline:
            note(f"NAO ENCONTRADO: {label}")
            if not optional:
                failures.append(label)
                texts = [n.get("text") or n.get("content-desc") for n in nodes()]
                note("   na tela: " + " | ".join(t for t in texts if t)[:900])
            return False
        time.sleep(1)


def visible(text):
    return bool(find(text))


def shot(name):
    time.sleep(1.0)
    data = adb("exec-out", "screencap", "-p").stdout
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(data)
    note(f"imagem: {name}")


def type_text(value):
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.8)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.2)


def hide_keyboard():
    out = adb("shell", "dumpsys", "input_method").stdout.decode("utf-8", "ignore")
    if "mInputShown=true" in out:
        back()


def go_tab(name):
    for _ in range(4):
        if tap(name, wait=3, optional=True):
            return True
        back()
    failures.append("aba " + name)
    return False


def tap_field(label, index):
    """Toca em um campo de texto pelo rótulo ou, se não achar, pela posição na tela."""
    if tap(label, wait=3, optional=True):
        return
    fields = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    if index < len(fields):
        x, y = center(fields[index])
        adb("shell", "input", "tap", str(x), str(y))
        note(f"toque: campo {index} ({x},{y})")
        time.sleep(1.5)
    else:
        note(f"NAO ENCONTRADO: campo {label}")
        failures.append(label)


def go_next_workday():
    """Escolhe na agenda o próximo dia com atendimento, para haver horários livres."""
    raw = adb("shell", "date", "+%Y-%m-%d").stdout.decode().strip()
    today = datetime.date.fromisoformat(raw)
    target = today + datetime.timedelta(days=1)
    while target.weekday() == 6:
        target += datetime.timedelta(days=1)

    def week_start(d):
        return d - datetime.timedelta(days=(d.weekday() + 1) % 7)

    if week_start(target) != week_start(today):
        tap("Próxima semana")
    tap(str(target.day))


def pick_time():
    if not tap(regex=r"\d\d:\d\d", wait=4, optional=True):
        tap("Outro horário")
        tap("OK")


def main():
    apk = sys.argv[1]
    note("instalando " + apk)
    result = adb("install", "-r", apk, timeout=300)
    note(result.stdout.decode("utf-8", "ignore").strip()[-200:])
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(8)
    for _ in range(20):
        if visible("Agendar"):
            break
        tap("Wait", wait=2, optional=True)
    shot("01-agenda-hoje")
    go_next_workday()
    shot("01b-agenda-proximo-dia")

    # ---- Novo agendamento, com cadastro rápido de cliente
    tap("Agendar")
    shot("02-agendamento-novo")
    tap("Escolher cliente")
    tap_field("Nome", 0)
    type_text("Maria Silva")
    tap_field("WhatsApp (com DDD)", 1)
    type_text("11998484660")
    shot("03-cliente-rapida")
    tap("Salvar")
    tap("Escolher serviço")
    shot("04-escolher-servico")
    tap("Drenagem linfática")
    pick_time()
    shot("05-agendamento-preenchido")
    tap("Agendar")
    shot("06-agenda-com-horario")

    # ---- Detalhes e conclusão com pagamento
    tap("Maria Silva")
    shot("07-detalhes-atendimento")
    tap("Concluir atendimento")
    shot("08-pagamento")
    tap("Concluir")
    shot("09-agenda-atendido")

    # ---- Clientes e pacote
    go_tab("Clientes")
    shot("10-clientes")
    tap("Maria Silva")
    shot("11-ficha-cliente")
    tap("Vender pacote")
    tap("Escolher serviço")
    tap("Drenagem linfática")
    shot("12-vender-pacote")
    tap("Salvar pacote")
    shot("13-ficha-com-pacote")
    tap("Agendar")
    tap("Escolher serviço")
    tap("Drenagem linfática")
    pick_time()
    tap("Repetir", optional=True)
    tap("Toda semana", optional=True)
    shot("14-agendamento-com-pacote")
    tap("Agendar", last=True)
    shot("15-ficha-apos-repeticao")
    back()

    # ---- Caixa
    go_tab("Caixa")
    shot("16-caixa")
    tap("Lançar")
    shot("17-novo-lancamento")
    tap("Cancelar")

    # ---- Mais
    go_tab("Mais")
    shot("18-mais")
    tap("Serviços e pacotes")
    shot("19-servicos")
    tap("Drenagem linfática")
    shot("20-editar-servico")
    back()
    back()
    tap("Horário de atendimento")
    shot("21-horario")
    back()
    tap("Backup")
    shot("22-backup")
    back()

    # ---- Bloqueio de horário e agenda final
    go_tab("Agenda")
    tap("Bloquear horário")
    shot("23-bloqueio")
    tap("Almoço")
    hide_keyboard()
    tap("Bloquear")
    shot("24-agenda-final")

    crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E").stdout.decode("utf-8", "ignore")
    with open(os.path.join(OUT, "crashes.txt"), "w") as f:
        f.write(crashes)
    running = adb("shell", "pidof", PKG).stdout.decode().strip()
    crashed = "FATAL EXCEPTION" in crashes
    note(f"app em execucao: {bool(running)}; erro fatal: {crashed}; passos nao encontrados: {failures}")
    return 1 if (crashed or failures or not running) else 0


if __name__ == "__main__":
    sys.exit(main())
