package br.com.acalmar.agenda.domain

import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.data.Quote
import br.com.acalmar.agenda.data.QuoteItem

// Contas e texto do orçamento (sem depender do Android).

fun quoteSubtotal(items: List<QuoteItem>): Long = items.sumOf { it.totalCents }

fun quoteTotal(items: List<QuoteItem>, discountCents: Long): Long =
    (quoteSubtotal(items) - discountCents).coerceAtLeast(0)

fun quoteNumber(id: Long): String = "Nº $id"

/** "5 × Drenagem linfática" ou só a descrição quando a quantidade é 1. */
fun quoteItemLabel(item: QuoteItem): String =
    if (item.quantity > 1) "${item.quantity} × ${item.description}" else item.description

/** Linha de contato do rodapé: "Karina · (11) 99848-4660 · @perfil". */
fun businessContact(business: BusinessInfo): String {
    val insta = business.instagram.trim().let { if (it.isBlank() || it.startsWith("@")) it else "@$it" }
    return listOf(business.professional.trim(), formatPhone(business.phone), insta)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/** Orçamento em texto, pronto para colar ou enviar pelo WhatsApp (asteriscos = negrito). */
fun quoteText(quote: Quote, items: List<QuoteItem>, business: BusinessInfo): String {
    val subtotal = quoteSubtotal(items)
    val total = quoteTotal(items, quote.discountCents)
    return buildString {
        append("*Orçamento ").append(quoteNumber(quote.id)).append("*")
        if (business.name.isNotBlank()) append(" · ").append(business.name.trim())
        append("\n")
        if (quote.clientName.isNotBlank()) append("Para: ").append(quote.clientName.trim()).append("\n")
        append("Data: ").append(formatDateShort(epochToDate(quote.createdOn))).append("\n\n")
        items.forEach { item ->
            append("• ").append(quoteItemLabel(item)).append(": ").append(formatMoney(item.totalCents)).append("\n")
        }
        append("\n")
        if (quote.discountCents > 0) {
            append("Subtotal: ").append(formatMoney(subtotal)).append("\n")
            append("Desconto: ").append(formatMoney(quote.discountCents)).append("\n")
        }
        append("*Total: ").append(formatMoney(total)).append("*\n")
        quote.validUntil?.let { append("\nVálido até ").append(formatDateShort(epochToDate(it))).append(".\n") }
        if (quote.notes.isNotBlank()) append("\n").append(quote.notes.trim()).append("\n")
        val contact = businessContact(business)
        if (contact.isNotBlank()) append("\n").append(contact)
    }.trim()
}
