package br.com.acalmar.agenda.data

/**
 * Dados iniciais, tirados da tabela de preços da Karina. Entram só na primeira abertura do app
 * e podem ser editados em Mais > Serviços e pacotes.
 */
object Seed {
    private data class Item(val name: String, val priceCents: Long, val pack5Cents: Long? = null)

    private val items = listOf(
        Item("Drenagem linfática", 7000, 32000),
        Item("Massagem relaxante", 7500, 35000),
        Item("Liberação miofascial", 7500, 35000),
        Item("Relaxante com ventosa · corpo inteiro", 8000),
        Item("Relaxante com ventosa · partes do corpo", 4000),
        Item("Limpeza de pele", 9500)
    )

    suspend fun insert(dao: AppDao) {
        items.forEachIndexed { index, item ->
            val id = dao.insertService(
                Service(name = item.name, durationMin = 60, priceCents = item.priceCents, sortOrder = index + 1)
            )
            if (item.pack5Cents != null) {
                dao.insertOffers(listOf(PackageOffer(serviceId = id, sessions = 5, priceCents = item.pack5Cents)))
            }
        }
    }
}
