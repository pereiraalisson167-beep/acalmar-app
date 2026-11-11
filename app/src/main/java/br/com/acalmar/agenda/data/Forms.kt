package br.com.acalmar.agenda.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// Modelos guardados como texto (JSON) nas configurações e nas fichas das clientes.

/** Dados do negócio, usados no orçamento e no termo de consentimento. */
@Serializable
data class BusinessInfo(
    val name: String = "Massoterapia Acalmar",
    val professional: String = "Karina",
    val phone: String = "11 99848-4660",
    val instagram: String = ""
) {
    fun toJson(): String = AppJson.encodeToString(serializer(), this)

    companion object {
        fun parse(text: String?): BusinessInfo {
            if (text.isNullOrBlank()) return BusinessInfo()
            return runCatching { AppJson.decodeFromString(serializer(), text) }.getOrNull() ?: BusinessInfo()
        }
    }
}

// ---------- Ficha de anamnese

@Serializable
enum class QuestionType { SIM_NAO, TEXTO }

/** Uma pergunta do modelo da ficha de anamnese. */
@Serializable
data class AnamnesisQuestion(
    val id: String,
    val section: String = "",
    val text: String,
    val type: QuestionType = QuestionType.SIM_NAO,
    /** Aviso curto mostrado na ficha e no atendimento quando a resposta é "sim" (opcional). */
    val alert: String = ""
)

/** Resposta de uma cliente a uma pergunta. */
@Serializable
data class AnamnesisAnswer(
    val questionId: String,
    val yes: Boolean? = null,
    val text: String = ""
) {
    val isBlank: Boolean get() = yes == null && text.isBlank()
}

object AnamnesisModel {
    private val questionList = ListSerializer(AnamnesisQuestion.serializer())
    private val answerList = ListSerializer(AnamnesisAnswer.serializer())

    private fun yesNo(id: String, section: String, text: String, alert: String = "") =
        AnamnesisQuestion(id, section, text, QuestionType.SIM_NAO, alert)

    private fun open(id: String, section: String, text: String) =
        AnamnesisQuestion(id, section, text, QuestionType.TEXTO)

    /** Modelo padrão para massoterapia e estética. Pode ser alterado em Mais > Fichas e termo. */
    fun default(): List<AnamnesisQuestion> {
        val health = "Saúde"
        val habits = "Hábitos"
        val skin = "Pele (limpeza de pele)"
        val care = "Atendimento"
        return listOf(
            yesNo("q01", health, "Está em tratamento médico?", "Em tratamento médico"),
            yesNo("q02", health, "Usa algum medicamento?", "Usa medicamento"),
            yesNo("q03", health, "Tem alergia (medicamentos, cosméticos, óleos, látex)?", "Alergia"),
            yesNo("q04", health, "Tem pressão alta ou baixa?", "Pressão"),
            yesNo("q05", health, "Tem diabetes?", "Diabetes"),
            yesNo("q06", health, "Tem problema cardíaco ou usa marca-passo?", "Coração"),
            yesNo("q07", health, "Tem varizes, problema circulatório ou histórico de trombose?", "Circulação"),
            yesNo("q08", health, "Tem ou já teve câncer?", "Histórico de câncer"),
            yesNo("q09", health, "Tem problema nos rins?", "Rins"),
            yesNo("q10", health, "Fez cirurgia nos últimos 12 meses?", "Cirurgia recente"),
            yesNo("q11", health, "Tem prótese, pino ou placa metálica?", "Prótese ou metal"),
            yesNo("q12", health, "Tem problema de pele (feridas, dermatite, micose)?", "Pele"),
            yesNo("q13", health, "Tem hérnia de disco, problema de coluna ou dor crônica?", "Coluna ou dor crônica"),
            yesNo("q14", health, "Tem epilepsia ou já teve convulsão?", "Epilepsia"),
            yesNo("q15", health, "Está grávida ou amamentando?", "Gestante ou lactante"),
            yesNo("q16", health, "Está com febre, infecção ou inflamação no momento?", "Febre ou infecção"),
            yesNo("q17", habits, "Pratica atividade física?"),
            yesNo("q18", habits, "Fuma?"),
            yesNo("q19", habits, "O intestino funciona todos os dias?"),
            open("q20", habits, "Quanta água bebe por dia?"),
            open("q21", habits, "Como é o seu sono?"),
            yesNo("q22", skin, "Usa ácido no rosto ou fez peeling nos últimos 30 dias?", "Ácido ou peeling"),
            yesNo("q23", skin, "Faz tratamento para acne?", "Tratamento de acne"),
            yesNo("q24", skin, "Tem sensibilidade a algum produto no rosto?", "Pele sensível"),
            open("q25", care, "Qual é a queixa principal ou o objetivo?"),
            open("q26", care, "Onde sente dor ou tensão?"),
            yesNo("q27", care, "Já fez massagem ou tratamento estético antes?"),
            open("q28", care, "Prefere pressão leve, média ou forte?"),
            open("q29", care, "Outras observações")
        )
    }

    fun parseTemplate(text: String?): List<AnamnesisQuestion> {
        if (text.isNullOrBlank()) return default()
        return runCatching { AppJson.decodeFromString(questionList, text) }.getOrNull() ?: default()
    }

    fun templateJson(questions: List<AnamnesisQuestion>): String = AppJson.encodeToString(questionList, questions)

    fun parseAnswers(text: String?): Map<String, AnamnesisAnswer> {
        if (text.isNullOrBlank()) return emptyMap()
        val list = runCatching { AppJson.decodeFromString(answerList, text) }.getOrNull() ?: return emptyMap()
        return list.associateBy { it.questionId }
    }

    /** Só as respostas preenchidas são gravadas. */
    fun answersJson(answers: Collection<AnamnesisAnswer>): String =
        AppJson.encodeToString(answerList, answers.filter { !it.isBlank })

    /** Avisos de atenção: perguntas com aviso cadastrado e respondidas com "sim". */
    fun alerts(template: List<AnamnesisQuestion>, answers: Map<String, AnamnesisAnswer>): List<String> =
        template.filter { it.type == QuestionType.SIM_NAO && it.alert.isNotBlank() && answers[it.id]?.yes == true }
            .map { it.alert.trim() }
            .distinct()

    /** Quantas perguntas do modelo têm resposta. */
    fun answeredCount(template: List<AnamnesisQuestion>, answers: Map<String, AnamnesisAnswer>): Int =
        template.count { answers[it.id]?.isBlank == false }

    fun newId(existing: List<AnamnesisQuestion>): String {
        var n = existing.size + 1
        val ids = existing.map { it.id }.toSet()
        while ("p$n" in ids) n++
        return "p$n"
    }
}

// ---------- Termo de consentimento

object ConsentModel {
    /**
     * Texto padrão. {cliente}, {profissional} e {negocio} são trocados pelos nomes na hora de assinar.
     * É um modelo: a profissional deve ler e ajustar em Mais > Fichas e termo.
     */
    val DEFAULT_TEXT: String = """
Eu, {cliente}, declaro que:

1. As informações que forneci na ficha de anamnese são verdadeiras, e me comprometo a avisar {profissional} sobre qualquer mudança no meu estado de saúde, no uso de medicamentos, gestação ou procedimentos realizados.

2. Recebi informações sobre os atendimentos oferecidos por {negocio} (massagens, drenagem linfática, liberação miofascial, ventosaterapia e limpeza de pele), seus objetivos, benefícios esperados e limitações, e pude esclarecer minhas dúvidas.

3. Entendo que os atendimentos têm finalidade de bem-estar e estética, não substituem consulta, diagnóstico ou tratamento médico, e que os resultados variam de pessoa para pessoa.

4. Estou ciente de que podem ocorrer reações passageiras, como vermelhidão, sensibilidade ou dor muscular leve e, na ventosaterapia, marcas arroxeadas na pele que desaparecem em alguns dias.

5. Posso pedir a interrupção do atendimento a qualquer momento, se sentir dor ou desconforto.

6. Comprometo-me a seguir as orientações recebidas antes e depois de cada atendimento.

7. Devo avisar com pelo menos 24 horas de antecedência quando não puder comparecer. Sem esse aviso, a sessão do pacote poderá ser considerada realizada.

8. Autorizo o registro dos meus dados e das informações de saúde desta ficha exclusivamente para o meu atendimento, conforme a Lei Geral de Proteção de Dados (Lei nº 13.709/2018).
""".trim()

    const val IMAGE_QUESTION = "Autorizo o uso das minhas fotos de antes e depois na divulgação do trabalho, sem identificação do meu nome."
    const val IMAGE_YES = "Uso de imagem: AUTORIZO o uso das minhas fotos de antes e depois na divulgação do trabalho, sem identificação do meu nome."
    const val IMAGE_NO = "Uso de imagem: NÃO AUTORIZO o uso das minhas fotos na divulgação do trabalho."

    /** Troca os marcadores do texto pelos nomes. */
    fun fill(template: String, clientName: String, business: BusinessInfo): String =
        template
            .replace("{cliente}", clientName.trim().ifBlank { "cliente" })
            .replace("{profissional}", business.professional.trim().ifBlank { "a profissional" })
            .replace("{negocio}", business.name.trim().ifBlank { "o estabelecimento" })

    fun imageLine(imageUse: Boolean): String = if (imageUse) IMAGE_YES else IMAGE_NO
}

// ---------- Orçamento

@Serializable
data class QuoteItem(
    val description: String,
    val quantity: Int = 1,
    val unitCents: Long = 0
) {
    val totalCents: Long get() = quantity.toLong() * unitCents
}

object QuoteModel {
    private val itemList = ListSerializer(QuoteItem.serializer())

    fun parseItems(text: String?): List<QuoteItem> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { AppJson.decodeFromString(itemList, text) }.getOrNull() ?: emptyList()
    }

    fun itemsJson(items: List<QuoteItem>): String = AppJson.encodeToString(itemList, items)
}
