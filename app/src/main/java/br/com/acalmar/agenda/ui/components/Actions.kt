package br.com.acalmar.agenda.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.domain.digitsOnly
import br.com.acalmar.agenda.domain.whatsappNumber
import br.com.acalmar.agenda.ui.theme.Brand
import java.time.LocalDate
import java.time.LocalTime

/** Abre a conversa da cliente no WhatsApp. Devolve falso se não for possível. */
fun openWhatsApp(context: Context, phone: String, message: String = ""): Boolean {
    val number = whatsappNumber(phone) ?: return false
    val url = "https://wa.me/$number" + if (message.isBlank()) "" else "?text=" + Uri.encode(message)
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/** Abre o discador do telefone com o número da cliente. */
fun dialPhone(context: Context, phone: String): Boolean {
    val digits = digitsOnly(phone)
    if (digits.isEmpty()) return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

data class StatusStyle(val label: String, val background: Color, val content: Color)

fun statusStyle(status: ApptStatus): StatusStyle = when (status) {
    ApptStatus.AGENDADO -> StatusStyle("Agendado", Brand.Beige, Brand.BronzeDark)
    ApptStatus.CONCLUIDO -> StatusStyle("Atendido", Brand.SageSoft, Brand.Sage)
    ApptStatus.FALTOU -> StatusStyle("Faltou", Brand.TerracottaSoft, Brand.Terracotta)
    ApptStatus.DESMARCADO -> StatusStyle("Desmarcado", Brand.GraySoft, Brand.Gray)
}

fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
