package br.com.acalmar.agenda

import br.com.acalmar.agenda.data.AnamnesisAnswer
import br.com.acalmar.agenda.data.AnamnesisModel
import br.com.acalmar.agenda.data.AnamnesisQuestion
import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.BackupFile
import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.data.ConsentModel
import br.com.acalmar.agenda.data.QuestionType
import br.com.acalmar.agenda.data.Quote
import br.com.acalmar.agenda.data.QuoteItem
import br.com.acalmar.agenda.data.QuoteModel
import br.com.acalmar.agenda.data.Service
import br.com.acalmar.agenda.domain.DEFAULT_RETURN_DAYS
import br.com.acalmar.agenda.domain.businessContact
import br.com.acalmar.agenda.domain.computeRecalls
import br.com.acalmar.agenda.domain.daysAgoLabel
import br.com.acalmar.agenda.domain.decodeSignature
import br.com.acalmar.agenda.domain.encodeSignature
import br.com.acalmar.agenda.domain.quoteSubtotal
import br.com.acalmar.agenda.domain.quoteText
import br.com.acalmar.agenda.domain.quoteTotal
import br.com.acalmar.agenda.domain.recallDueLabel
import br.com.acalmar.agenda.domain.recallMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes das funções da versão 2: retorno, anamnese, termo, orçamento, assinatura e backup. */
class RecordsLogicTest {

    private val today = 20_000L

    private val drenagem = Service(id = 1, name = "Drenagem linfática", returnDays = 30)
    private val limpeza = Service(id = 2, name = "Limpeza de pele", returnDays = 0)

    private fun client(id: Long, name: String = "Cliente $id", snooze: Long? = null, dismissed: Long? = null) =
        Client(id = id, name = name, recallSnoozeUntil = snooze, recallDismissedFor = dismissed)

    private fun appt(
        id: Long,
        client: Long,
        date: Long,
        status: ApptStatus = ApptStatus.CONCLUIDO,
        service: Service? = drenagem
    ) = Appointment(
        id = id,
        clientId = client,
        serviceId = service?.id,
        serviceName = service?.name ?: "Serviço antigo",
        date = date,
        startMin = 9 * 60,
        durationMin = 60,
        priceCents = 7000,
        status = status
    )

    // ---------- Hora de chamar de volta

    @Test
    fun recall_entraQuemPassouDoRetorno() {
        val list = computeRecalls(
            clients = listOf(client(1)),
            appts = listOf(appt(1, client = 1, date = today - 35)),
            services = listOf(drenagem),
            today = today
        )
        assertEquals(1, list.size)
        assertEquals(today - 5, list[0].dueDate)
        assertEquals("atrasado 5 dias", recallDueLabel(list[0].dueDate, today))
    }

    @Test
    fun recall_apareceUmaSemanaAntesENaoAntesDisso() {
        val services = listOf(drenagem)
        val dentroDoAviso = computeRecalls(listOf(client(1)), listOf(appt(1, 1, today - 25)), services, today)
        val cedoDemais = computeRecalls(listOf(client(1)), listOf(appt(1, 1, today - 20)), services, today)
        assertEquals(1, dentroDoAviso.size)
        assertEquals("em 5 dias", recallDueLabel(dentroDoAviso[0].dueDate, today))
        assertTrue(cedoDemais.isEmpty())
    }

    @Test
    fun recall_ignoraQuemJaTemHorarioMarcado() {
        val list = computeRecalls(
            clients = listOf(client(1)),
            appts = listOf(appt(1, 1, today - 40), appt(2, 1, today + 3, ApptStatus.AGENDADO)),
            services = listOf(drenagem),
            today = today
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun recall_faltaOuDesmarcadoNaoContaComoAtendimento() {
        val list = computeRecalls(
            clients = listOf(client(1)),
            appts = listOf(
                appt(1, 1, today - 40),
                appt(2, 1, today - 10, ApptStatus.FALTOU),
                appt(3, 1, today - 5, ApptStatus.DESMARCADO)
            ),
            services = listOf(drenagem),
            today = today
        )
        assertEquals(1, list.size)
        assertEquals(today - 40, list[0].lastDate)
    }

    @Test
    fun recall_servicoSemRetornoNaoAvisa() {
        val list = computeRecalls(listOf(client(1)), listOf(appt(1, 1, today - 90, service = limpeza)), listOf(limpeza), today)
        assertTrue(list.isEmpty())
    }

    @Test
    fun recall_servicoApagadoUsaRetornoPadrao() {
        val semServico = appt(1, 1, today - DEFAULT_RETURN_DAYS.toLong(), service = null)
        val list = computeRecalls(listOf(client(1)), listOf(semServico), emptyList(), today)
        assertEquals(1, list.size)
        assertEquals("é hoje", recallDueLabel(list[0].dueDate, today))
    }

    @Test
    fun recall_lembrarDepoisENaoChamar() {
        val appts = listOf(appt(1, 1, today - 40))
        val services = listOf(drenagem)
        assertTrue(computeRecalls(listOf(client(1, snooze = today + 3)), appts, services, today).isEmpty())
        assertEquals(1, computeRecalls(listOf(client(1, snooze = today)), appts, services, today).size)
        assertTrue(computeRecalls(listOf(client(1, dismissed = today - 40)), appts, services, today).isEmpty())
        // Depois de um atendimento novo, "não chamar" deixa de valer.
        val comNovo = appts + appt(2, 1, today - 31)
        assertEquals(1, computeRecalls(listOf(client(1, dismissed = today - 40)), comNovo, services, today).size)
    }

    @Test
    fun recall_ordenaPeloMaisAtrasado() {
        val list = computeRecalls(
            clients = listOf(client(1, "Bia"), client(2, "Ana")),
            appts = listOf(appt(1, 1, today - 50), appt(2, 2, today - 35)),
            services = listOf(drenagem),
            today = today
        )
        assertEquals(listOf("Bia", "Ana"), list.map { it.client.name })
    }

    @Test
    fun recall_textos() {
        assertEquals("ontem", daysAgoLabel(today - 1, today))
        assertEquals("há 32 dias", daysAgoLabel(today - 32, today))
        assertEquals("é amanhã", recallDueLabel(today + 1, today))
        val item = computeRecalls(listOf(client(1, "Maria Silva")), listOf(appt(1, 1, today - 35)), listOf(drenagem), today)[0]
        val message = recallMessage(item, today)
        assertTrue(message.startsWith("Olá, Maria!"))
        assertTrue(message.contains("5 semanas"))
        assertTrue(message.contains("Drenagem linfática"))
    }

    // ---------- Ficha de anamnese

    @Test
    fun anamnese_modeloPadraoTemPerguntasUnicas() {
        val questions = AnamnesisModel.default()
        assertTrue(questions.size >= 20)
        assertEquals(questions.size, questions.map { it.id }.toSet().size)
    }

    @Test
    fun anamnese_avisosSoParaSimComAviso() {
        val template = listOf(
            AnamnesisQuestion("a", text = "Tem alergia?", alert = "Alergia"),
            AnamnesisQuestion("b", text = "Fuma?"),
            AnamnesisQuestion("c", text = "Está grávida?", alert = "Gestante"),
            AnamnesisQuestion("d", text = "Objetivo", type = QuestionType.TEXTO)
        )
        val answers = mapOf(
            "a" to AnamnesisAnswer("a", yes = true),
            "b" to AnamnesisAnswer("b", yes = true),
            "c" to AnamnesisAnswer("c", yes = false),
            "d" to AnamnesisAnswer("d", text = "Relaxar")
        )
        assertEquals(listOf("Alergia"), AnamnesisModel.alerts(template, answers))
        assertEquals(4, AnamnesisModel.answeredCount(template, answers))
    }

    @Test
    fun anamnese_respostasEmBrancoNaoSaoGravadas() {
        val json = AnamnesisModel.answersJson(
            listOf(AnamnesisAnswer("a", yes = true), AnamnesisAnswer("b"), AnamnesisAnswer("c", text = "  "))
        )
        val back = AnamnesisModel.parseAnswers(json)
        assertEquals(setOf("a"), back.keys)
        assertEquals(true, back["a"]?.yes)
    }

    @Test
    fun anamnese_modeloInvalidoVoltaAoPadrao() {
        assertEquals(AnamnesisModel.default(), AnamnesisModel.parseTemplate("isto não é json"))
        assertEquals(AnamnesisModel.default(), AnamnesisModel.parseTemplate(null))
        assertTrue(AnamnesisModel.parseAnswers("{quebrado").isEmpty())
    }

    // ---------- Termo de consentimento

    @Test
    fun termo_trocaOsNomes() {
        val text = ConsentModel.fill(ConsentModel.DEFAULT_TEXT, "Maria Silva", BusinessInfo())
        assertTrue(text.startsWith("Eu, Maria Silva, declaro"))
        assertTrue(text.contains("Karina"))
        assertTrue(text.contains("Massoterapia Acalmar"))
        assertFalse(text.contains("{"))
    }

    // ---------- Orçamento

    @Test
    fun orcamento_contas() {
        val items = listOf(QuoteItem("Drenagem linfática", 5, 7000), QuoteItem("Ventosa (partes)", 2, 4000))
        assertEquals(43000L, quoteSubtotal(items))
        assertEquals(38000L, quoteTotal(items, 5000))
        assertEquals(0L, quoteTotal(items, 99_999))
        assertEquals(items, QuoteModel.parseItems(QuoteModel.itemsJson(items)))
    }

    @Test
    fun orcamento_texto() {
        val items = listOf(QuoteItem("Drenagem linfática", 5, 6400))
        val quote = Quote(id = 7, clientName = "Maria", createdOn = 20_000, validUntil = 20_015, discountCents = 2000, itemsJson = "")
        val text = quoteText(quote, items, BusinessInfo(instagram = "massoterapia.acalmar"))
        assertTrue(text.startsWith("*Orçamento Nº 7*"))
        assertTrue(text.contains("5 × Drenagem linfática"))
        assertTrue(text.contains("Desconto"))
        assertTrue(text.contains("Válido até"))
        assertTrue(text.contains("@massoterapia.acalmar"))
        assertEquals("Karina · (11) 99848-4660", businessContact(BusinessInfo()))
    }

    // ---------- Assinatura

    @Test
    fun assinatura_idaEVolta() {
        val strokes = listOf(listOf(0.1f to 0.2f, 0.5f to 0.6f), emptyList(), listOf(1.4f to -0.3f))
        val data = decodeSignature(encodeSignature(2.5f, strokes))
        assertEquals(2.5f, data.aspect, 0.001f)
        assertEquals(2, data.strokes.size)
        assertEquals(0.5f, data.strokes[0][1].first, 0.001f)
        // Pontos fora da área são trazidos para dentro dela.
        assertEquals(1f, data.strokes[1][0].first, 0.001f)
        assertEquals(0f, data.strokes[1][0].second, 0.001f)
        assertTrue(decodeSignature("lixo").isEmpty)
        assertTrue(decodeSignature("").isEmpty)
    }

    // ---------- Backup

    @Test
    fun backup_daPrimeiraVersaoContinuaValido() {
        // Arquivo como a versão 1 gravava: sem fichas, fotos, orçamentos e campos novos.
        val v1 = """
            {"app":"acalmar","schema":1,"exportedAt":1,
             "clients":[{"id":3,"name":"Maria Silva","phone":"11998484660"}],
             "services":[{"id":1,"name":"Drenagem linfática","durationMin":60,"priceCents":7000}],
             "appointments":[{"id":9,"clientId":3,"serviceId":1,"serviceName":"Drenagem linfática",
                              "date":20000,"startMin":540,"durationMin":60,"priceCents":7000}]}
        """.trimIndent()
        val file = BackupFile.parse(v1)
        assertEquals(1, file.clients.size)
        assertEquals(null, file.clients[0].recallSnoozeUntil)
        assertEquals(30, file.services[0].returnDays)
        assertEquals("", file.appointments[0].evolution)
        assertTrue(file.photos.isEmpty() && file.quotes.isEmpty() && file.anamneses.isEmpty())
    }
}
