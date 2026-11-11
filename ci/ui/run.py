#!/usr/bin/env python3
"""Percorre as telas do app no emulador, salvando uma imagem de cada uma.

Usado pelo workflow "Conferir telas". Uso:

    run.py <apk novo> [apk publicado]

Com o APK publicado, o roteiro começa nele (cadastra uma cliente e conclui um atendimento),
instala a versão nova por cima e confere que os dados continuam lá. Depois simula um dia de uso
de todas as funções e registra qualquer erro que feche o app.
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
MAX_FAILURES = 8


class Abort(Exception):
    """Falhas demais: o roteiro se perdeu e não adianta continuar."""


def fail(description):
    failures.append(description)
    if len(failures) >= MAX_FAILURES:
        raise Abort()


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


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]"))))


def center(node):
    x1, y1, x2, y2 = bounds(node)
    return (x1 + x2) // 2, (y1 + y2) // 2


def label_of(node):
    return (node.get("text") or "") or (node.get("content-desc") or "")


def matches(node, text=None, regex=None):
    for value in (node.get("text") or "", node.get("content-desc") or ""):
        if not value:
            continue
        if (regex and re.fullmatch(regex, value, re.S)) or (text is not None and value == text):
            return True
    return False


def find(text=None, regex=None):
    return [n for n in nodes() if matches(n, text, regex)]


def screen_size():
    out = adb("shell", "wm", "size").stdout.decode()
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def scroll(up=True):
    """Rola o conteúdo da tela (up=True mostra o que está mais abaixo)."""
    w, h = screen_size()
    y1, y2 = (int(h * 0.68), int(h * 0.32)) if up else (int(h * 0.32), int(h * 0.68))
    adb("shell", "input", "swipe", str(w // 2), str(y1), str(w // 2), str(y2), "450")
    time.sleep(1.0)


def to_top():
    for _ in range(4):
        scroll(up=False)


def press_wait_dialog():
    """Aviso do sistema ("... isn't responding") por cima do app: manda esperar."""
    waiting = find("Wait")
    if waiting:
        x, y = center(waiting[0])
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(2)
        return True
    return False


def tap(text=None, regex=None, last=False, nth=None, wait=12, optional=False, scrolls=4):
    """Toca no elemento com o texto (ou descrição) indicado. Se não estiver à vista, rola a tela."""
    label = text or regex
    deadline = time.time() + wait
    scrolled = 0
    while True:
        found = find(text, regex)
        if found and (nth is None or nth < len(found)):
            node = found[nth] if nth is not None else (found[-1] if last else found[0])
            x, y = center(node)
            adb("shell", "input", "tap", str(x), str(y))
            note(f"toque: {label} ({x},{y})")
            time.sleep(1.5)
            return True
        if press_wait_dialog():
            continue
        if time.time() > deadline:
            if scrolled < scrolls:
                scroll(up=True)
                scrolled += 1
                continue
            note(f"NAO ENCONTRADO: {label}")
            if not optional:
                note("   na tela: " + " | ".join(t for t in (label_of(n) for n in nodes()) if t)[:900])
                fail(label)
            return False
        time.sleep(1)


def visible(text=None, regex=None):
    return bool(find(text, regex))


def check(description, condition):
    if condition:
        note("ok: " + description)
    else:
        note("FALHOU: " + description)
        fail(description)


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
    for _ in range(5):
        if tap(name, wait=3, optional=True, scrolls=0):
            return True
        back()
    fail("aba " + name)
    return False


def tap_field(label, index):
    """Toca em um campo de texto pelo rótulo ou, se não achar, pela posição na tela."""
    if tap(label, wait=3, optional=True, scrolls=0):
        return
    fields = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    if index < len(fields):
        x, y = center(fields[index])
        adb("shell", "input", "tap", str(x), str(y))
        note(f"toque: campo {index} ({x},{y})")
        time.sleep(1.5)
    else:
        note(f"NAO ENCONTRADO: campo {label}")
        fail(label)


def device_today():
    raw = adb("shell", "date", "+%Y-%m-%d").stdout.decode().strip()
    return datetime.date.fromisoformat(raw)


def go_next_workday():
    """Escolhe na agenda o próximo dia com atendimento, para haver horários livres."""
    today = device_today()
    target = today + datetime.timedelta(days=1)
    while target.weekday() == 6:
        target += datetime.timedelta(days=1)

    def week_start(d):
        return d - datetime.timedelta(days=(d.weekday() + 1) % 7)

    if week_start(target) != week_start(today):
        tap("Próxima semana")
    tap(str(target.day))


def pick_time():
    if not tap(regex=r"\d\d:\d\d", wait=4, optional=True, scrolls=1):
        tap("Outro horário")
        tap("OK")


def open_app():
    adb("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(6)
    for _ in range(20):
        if visible("Agendar"):
            return
        press_wait_dialog()
        time.sleep(1)


def install(apk, label):
    note(f"instalando {label}: {apk}")
    result = adb("install", "-r", apk, timeout=300)
    out = (result.stdout + result.stderr).decode("utf-8", "ignore").strip()
    note(out[-300:])
    check(f"instalacao de {label}", "Success" in out)


def dump_screen(title):
    """Registra os elementos da tela atual (útil para telas de outros apps, como a câmera)."""
    parts = []
    for n in nodes():
        rid = (n.get("resource-id") or "").split("/")[-1]
        lab = label_of(n)
        if rid or lab:
            parts.append(f"{lab or '-'}[{rid}]@{n.get('bounds')}")
    note(f"   tela {title}: " + " | ".join(parts)[:1500])


def find_control(*patterns):
    """Botão de outro app pelo identificador, texto ou descrição; os padrões vão do mais exato ao mais geral."""
    found = nodes()
    for pattern in patterns:
        for n in found:
            rid = (n.get("resource-id") or "").split("/")[-1]
            if re.fullmatch(pattern, rid) or re.fullmatch(pattern, label_of(n) or ""):
                return n
    return None


def take_photo(name):
    """Tira uma foto com a câmera do emulador e guarda na ficha com o tipo sugerido."""
    if not tap("Tirar foto", optional=True):
        return False
    time.sleep(5)
    for label in ("While using the app", "Only this time", "Allow", "ALLOW", "OK", "Next", "Skip"):
        tap(label, optional=True, scrolls=0, wait=1)
    dump_screen("camera")
    w, h = screen_size()
    shutter = find_control(r"shutter_button", r"(?i)shutter", r"(?i)take (photo|picture)")
    if shutter is not None:
        x, y = center(shutter)
    else:
        x, y = w // 2, int(h * 0.905)
    adb("shell", "input", "tap", str(x), str(y))
    note(f"disparo da camera ({x},{y})")
    time.sleep(4)
    shot(name + "-camera")
    if not visible("Nova foto"):
        dump_screen("camera depois do disparo")
        done = find_control(r"done_button", r"(?i)done", r"(?i)ok|confirm|accept")
        if done is not None:
            dx, dy = center(done)
        else:
            dx, dy = x, y
        adb("shell", "input", "tap", str(dx), str(dy))
        note(f"confirmar foto ({dx},{dy})")
        time.sleep(4)
    ok = visible("Nova foto")
    shot(name + "-nova-foto")
    if ok:
        tap("Guardar foto")
        time.sleep(3)
    else:
        note("aviso: a camera nao devolveu a foto")
        for _ in range(3):
            if visible("Tirar foto"):
                break
            back()
    return ok


def draw_signature():
    pads = find("Área de assinatura")
    if not pads:
        note("NAO ENCONTRADO: área de assinatura")
        fail("área de assinatura")
        return
    x1, y1, x2, y2 = bounds(pads[0])
    w, h = x2 - x1, y2 - y1
    strokes = [
        (0.12, 0.65, 0.25, 0.25), (0.25, 0.25, 0.32, 0.70), (0.32, 0.70, 0.45, 0.30),
        (0.45, 0.30, 0.55, 0.68), (0.55, 0.68, 0.70, 0.35), (0.70, 0.35, 0.88, 0.60),
    ]
    for ax, ay, bx, by in strokes:
        adb("shell", "input", "swipe", str(x1 + int(w * ax)), str(y1 + int(h * ay)),
            str(x1 + int(w * bx)), str(y1 + int(h * by)), "250")
        time.sleep(0.3)
    note("assinatura desenhada")


# ---------------------------------------------------------------------------------------------
# Roteiro
# ---------------------------------------------------------------------------------------------

def first_day(prefix):
    """Cadastra a Maria pelo agendamento rápido e conclui o primeiro atendimento."""
    go_next_workday()
    shot(prefix + "01-agenda-proximo-dia")
    tap("Agendar")
    shot(prefix + "02-agendamento-novo")
    tap("Escolher cliente")
    tap_field("Nome", 0)
    type_text("Maria Silva")
    tap_field("WhatsApp (com DDD)", 1)
    type_text("11998484660")
    shot(prefix + "03-cliente-rapida")
    tap("Salvar")
    tap("Escolher serviço")
    tap("Drenagem linfática")
    pick_time()
    shot(prefix + "04-agendamento-preenchido")
    tap("Agendar", last=True)
    shot(prefix + "05-agenda-com-horario")
    tap("Maria Silva")
    shot(prefix + "06-detalhes-atendimento")
    tap("Concluir atendimento")
    shot(prefix + "07-concluir")
    tap("Concluir")
    shot(prefix + "08-agenda-atendido")


def main():
    apk = sys.argv[1]
    old_apk = sys.argv[2] if len(sys.argv) > 2 else None
    adb("logcat", "-c")
    try:
        walk(apk, old_apk)
    except Abort:
        note(f"roteiro interrompido depois de {len(failures)} falhas")
        shot("99-onde-parou")
    return report()


def walk(apk, old_apk):

    # ---- Versão publicada: cria dados reais antes de atualizar
    if old_apk:
        install(old_apk, "versao publicada")
        open_app()
        shot("00-versao-publicada")
        first_day("00-")
        install(apk, "versao nova por cima")
    else:
        install(apk, "versao nova")
    open_app()
    shot("10-versao-nova-abertura")
    if old_apk:
        go_next_workday()
        check("atendimento da versao anterior continua na agenda", visible("Maria Silva"))
        shot("11-agenda-apos-atualizar")
    else:
        first_day("")

    # ---- Ficha da cliente
    go_tab("Clientes")
    shot("12-clientes")
    tap("Maria Silva")
    shot("13-ficha-cliente")

    # Anamnese: responde "sim" para alergia (3ª pergunta) e salva.
    tap("Ficha de anamnese")
    shot("14-anamnese")
    tap("Sim", nth=2)
    tap_field("Detalhes", 0)
    type_text("Oleo de amendoas")
    hide_keyboard()
    shot("15-anamnese-preenchida")
    tap("Salvar ficha")
    check("aviso de alergia na ficha da cliente", visible("Alergia"))
    shot("16-ficha-com-aviso")

    # Termo de consentimento: autoriza fotos, assina e gera o PDF.
    tap("Termo de consentimento")
    shot("17-termo")
    switches = [n for n in nodes() if n.get("checkable") == "true"]
    if switches:
        x, y = center(switches[0])
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(1)
    draw_signature()
    shot("18-termo-assinando")
    tap("Salvar termo assinado")
    shot("19-termo-assinado")
    tap("Ver e enviar o PDF")
    time.sleep(3)
    shot("20-termo-pdf")
    tap("Fechar", optional=True, scrolls=0)
    back()
    check("termo assinado aparece na ficha", visible(regex=r"Assinado em .*"))

    # Fotos: tira duas fotos com a câmera do emulador (antes e depois) e compara.
    tap("Fotos de antes e depois")
    shot("21-fotos-vazio")
    first = take_photo("22")
    second = take_photo("23") if first else False
    shot("24-fotos")
    check("duas fotos guardadas (antes e depois)", first and second and visible(regex=r"Foto: Antes") and visible(regex=r"Foto: Depois"))
    if tap("Antes e depois", optional=True, scrolls=0):
        time.sleep(4)
        shot("24b-antes-e-depois")
        tap("Fechar", optional=True, scrolls=0)
    if tap(regex=r"Foto: .*", optional=True, scrolls=0):
        time.sleep(2)
        shot("24c-foto-ampliada")
        back()
    if visible("Tirar foto"):
        back()

    # Orçamento a partir da ficha da cliente.
    tap("Orçamentos")
    shot("25-orcamentos-cliente")
    tap("Novo orçamento")
    tap("Adicionar item")
    shot("26-orcamento-itens")
    tap(regex=r"Pacote de 5 sessões · Drenagem linfática")
    shot("27-orcamento-preenchido")
    tap("Salvar orçamento")
    shot("28-orcamento-pronto")
    back()
    back()

    # Evolução: anota no atendimento concluído e confere no histórico.
    tap(regex=r".* · \d\d:\d\d", last=True)
    shot("29-atendimento-concluido")
    tap("Anotar")
    tap_field("O que foi feito, regiões trabalhadas, como a cliente respondeu, orientações", 0)
    type_text("Drenagem nas pernas, boa resposta")
    hide_keyboard()
    tap("Salvar")
    check("evolucao aparece no atendimento", visible("Drenagem nas pernas, boa resposta"))
    shot("30-atendimento-com-evolucao")
    back()
    check("evolucao aparece no historico", visible("Drenagem nas pernas, boa resposta"))
    shot("30b-historico-evolucao")

    # ---- Pacote e sessões que se repetem
    to_top()
    tap("Vender pacote")
    tap("Escolher serviço")
    tap("Drenagem linfática")
    shot("31-vender-pacote")
    tap("Salvar pacote")
    to_top()
    tap("Agendar")
    tap("Escolher serviço")
    tap("Drenagem linfática")
    pick_time()
    tap("Repetir")
    tap("Toda semana")
    shot("32-agendamento-com-pacote")
    tap("Agendar", last=True)
    shot("33-ficha-apos-repeticao")

    # Falta com desconto do pacote, desmarcar uma sessão e editar outra.
    tap(regex=r".* · \d\d:\d\d · pacote")
    tap("Faltou")
    shot("34-falta-pacote")
    tap("Descontar")
    tap(regex=r".* · \d\d:\d\d · pacote")
    tap("Desmarcou")
    shot("35-desmarcar-serie")
    tap("Só esta")
    tap(regex=r".* · \d\d:\d\d · pacote")
    tap("Editar")
    tap("Salvar alterações")
    shot("36-ficha-final")
    back()

    # ---- Caixa
    go_tab("Caixa")
    shot("37-caixa")
    tap("Lançar")
    shot("38-novo-lancamento")
    tap("Cancelar")

    # ---- Mais: orçamentos, serviços, horário, fichas, negócio e backup
    go_tab("Mais")
    shot("39-mais")
    tap("Orçamentos")
    shot("40-orcamentos")
    tap("Maria Silva")
    shot("41-orcamento-aberto")
    back()
    back()
    tap("Serviços e pacotes")
    shot("42-servicos")
    tap("Drenagem linfática")
    shot("43-editar-servico")
    back()
    back()
    tap("Horário de atendimento")
    shot("44-horario")
    back()
    tap("Fichas e termo")
    shot("45-fichas-e-termo")
    tap("Perguntas da ficha de anamnese")
    shot("46-modelo-anamnese")
    back()
    tap("Texto do termo de consentimento")
    shot("47-texto-termo")
    back()
    back()
    tap("Dados do negócio")
    shot("48-dados-negocio")
    back()

    tap("Backup")
    shot("49-backup")
    tap("Fazer backup agora")
    shot("50-salvar-arquivo")
    tap(regex=r"(?i)save|salvar", scrolls=0)
    time.sleep(3)
    shot("51-backup-feito")
    tap("Restaurar um backup")
    shot("52-escolher-arquivo")
    tap(regex=r"acalmar-backup-.*", scrolls=0)
    shot("53-confirmar-restauracao")
    tap("Restaurar", last=True)
    time.sleep(3)
    shot("54-restaurado")
    back()
    go_tab("Clientes")
    check("dados depois de restaurar o backup", visible("Maria Silva"))
    tap("Maria Silva")
    check("ficha, termo e evolucao depois de restaurar", visible("Alergia") and visible(regex=r"Assinado em .*"))
    shot("55-ficha-apos-restaurar")
    back()

    # ---- Bloqueio de horário
    go_tab("Agenda")
    tap("Bloquear horário")
    shot("56-bloqueio")
    tap("Almoço")
    hide_keyboard()
    tap("Bloquear")
    shot("57-agenda-final")

    # ---- Hora de chamar de volta: adianta o relógio do emulador em 45 dias
    adb("root")
    time.sleep(3)
    future = datetime.datetime.now() + datetime.timedelta(days=45)
    adb("shell", "date", future.strftime("%m%d%H%M%Y.%S"))
    adb("shell", "am", "force-stop", PKG)
    open_app()
    go_tab("Clientes")
    shot("58-clientes-45-dias-depois")
    if tap("Hora de chamar de volta", optional=True, scrolls=0):
        shot("59-chamar-de-volta")
        check("Maria na lista de chamar de volta", visible("Maria Silva"))
        back()
    else:
        note("aviso: lista de chamar de volta nao apareceu (relogio do emulador nao mudou?)")



def report():
    crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E").stdout.decode("utf-8", "ignore")
    with open(os.path.join(OUT, "crashes.txt"), "w") as f:
        f.write(crashes)
    warnings = adb("logcat", "-d", "-s", "Acalmar:W").stdout.decode("utf-8", "ignore")
    with open(os.path.join(OUT, "avisos-do-app.txt"), "w") as f:
        f.write(warnings)
    if "Falha" in warnings:
        note("avisos do app: " + warnings[-1500:])
        failures.append("avisos de falha no log do app")
    running = adb("shell", "pidof", PKG).stdout.decode().strip()
    crashed = "FATAL EXCEPTION" in crashes
    note(f"app em execucao: {bool(running)}; erro fatal: {crashed}; passos nao encontrados: {failures}")
    return 1 if (crashed or failures or not running) else 0


if __name__ == "__main__":
    sys.exit(main())
