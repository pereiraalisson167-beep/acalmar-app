package br.com.acalmar.agenda.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Convenções: datas são guardadas como "dia desde 1970" (LocalDate.toEpochDay),
// horários como minutos desde a meia-noite e valores em centavos.

@Serializable
enum class ApptStatus { AGENDADO, CONCLUIDO, FALTOU, DESMARCADO }

@Serializable
enum class CashKind { ENTRADA, SAIDA }

@Serializable
enum class PayMethod(val label: String) {
    PIX("Pix"),
    DINHEIRO("Dinheiro"),
    DEBITO("Débito"),
    CREDITO("Crédito"),
    OUTRO("Outro")
}

@Serializable
enum class PhotoKind(val label: String) {
    ANTES("Antes"),
    DEPOIS("Depois"),
    OUTRA("Outra")
}

@Serializable
enum class QuoteStatus(val label: String) {
    ABERTO("Em aberto"),
    APROVADO("Aprovado"),
    RECUSADO("Recusado")
}

@Serializable
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    /** "dd/MM" ou "dd/MM/aaaa"; vazio quando não informado. */
    val birthday: String = "",
    val howFound: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    /** "Hora de chamar de volta": a cliente fica fora da lista até este dia (lembrar depois). */
    val recallSnoozeUntil: Long? = null,
    /** "Hora de chamar de volta": dispensada para o último atendimento, feito nesta data. */
    val recallDismissedFor: Long? = null
)

@Serializable
@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationMin: Int = 60,
    val priceCents: Long = 0,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    /** Tempo de retorno: depois de quantos dias chamar a cliente de volta (0 = não chamar). */
    @ColumnInfo(defaultValue = "30") val returnDays: Int = 30
)

/** Pacote que um serviço oferece (ex.: 5 sessões por R$ 320). */
@Serializable
@Entity(
    tableName = "package_offers",
    foreignKeys = [
        ForeignKey(
            entity = Service::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("serviceId")]
)
data class PackageOffer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceId: Long,
    val sessions: Int,
    val priceCents: Long,
    /** Validade em dias a partir da compra; nulo = sem validade. */
    val validityDays: Int? = null
)

/** Pacote comprado por uma cliente. */
@Serializable
@Entity(
    tableName = "client_packages",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Service::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("clientId"), Index("serviceId")]
)
data class ClientPackage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val serviceId: Long? = null,
    val serviceName: String,
    val totalSessions: Int,
    val priceCents: Long,
    val purchasedOn: Long,
    val expiresOn: Long? = null,
    /** Sessões já feitas antes de o pacote entrar no app. */
    val usedBefore: Int = 0,
    val notes: String = ""
)

@Serializable
@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Service::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ClientPackage::class,
            parentColumns = ["id"],
            childColumns = ["clientPackageId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("clientId"),
        Index("serviceId"),
        Index("clientPackageId"),
        Index("date"),
        Index("seriesId")
    ]
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val serviceId: Long? = null,
    val serviceName: String,
    val date: Long,
    val startMin: Int,
    val durationMin: Int,
    val priceCents: Long,
    val status: ApptStatus = ApptStatus.AGENDADO,
    /** Preenchido quando a sessão faz parte de um pacote da cliente. */
    val clientPackageId: Long? = null,
    /** Em caso de falta: a sessão é descontada do pacote? */
    val chargesPackage: Boolean = true,
    val notes: String = "",
    /** Agendamentos criados juntos como "sessões que se repetem" compartilham este código. */
    val seriesId: String? = null,
    val createdAt: Long = 0,
    /** Evolução: o que foi feito e como a cliente respondeu nesta sessão. */
    @ColumnInfo(defaultValue = "") val evolution: String = ""
)

val Appointment.endMin: Int get() = startMin + durationMin

/** Horário bloqueado na agenda. */
@Serializable
@Entity(tableName = "blocks", indices = [Index("date")])
data class TimeBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val startMin: Int = 0,
    val endMin: Int = 24 * 60,
    val allDay: Boolean = false,
    val reason: String = ""
)

/** Lançamento do caixa. */
@Serializable
@Entity(
    tableName = "cash_entries",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Appointment::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ClientPackage::class,
            parentColumns = ["id"],
            childColumns = ["clientPackageId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("date"), Index("clientId"), Index("appointmentId"), Index("clientPackageId")]
)
data class CashEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val kind: CashKind = CashKind.ENTRADA,
    val amountCents: Long,
    val method: PayMethod = PayMethod.PIX,
    val description: String = "",
    val clientId: Long? = null,
    val appointmentId: Long? = null,
    val clientPackageId: Long? = null,
    val createdAt: Long = 0
)

/** Configurações simples, em pares nome/valor. */
@Serializable
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val name: String,
    val value: String
)

/** Ficha de anamnese de uma cliente: as respostas ficam em JSON (ver Forms.kt). */
@Serializable
@Entity(
    tableName = "anamneses",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Anamnesis(
    @PrimaryKey val clientId: Long,
    val answersJson: String = "",
    val updatedAt: Long = 0
)

/** Termo de consentimento assinado por uma cliente. */
@Serializable
@Entity(
    tableName = "consents",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class Consent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    /** Texto do termo como estava no momento da assinatura. */
    val text: String,
    /** A cliente autoriza o uso das fotos de antes e depois na divulgação? */
    val imageUse: Boolean = false,
    val signedOn: Long,
    val signerName: String = "",
    /** Traços da assinatura feita na tela (ver domain/Signature.kt). */
    val signature: String = "",
    val createdAt: Long = 0
)

/** Foto de uma cliente (antes, depois ou outra). O arquivo fica guardado dentro do app. */
@Serializable
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val fileName: String,
    val takenOn: Long,
    val kind: PhotoKind = PhotoKind.OUTRA,
    val note: String = "",
    val createdAt: Long = 0
)

/** Orçamento. Os itens ficam em JSON (ver Forms.kt). */
@Serializable
@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("clientId")]
)
data class Quote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long? = null,
    /** Nome de quem pediu o orçamento (pode não ser cliente cadastrada). */
    val clientName: String = "",
    val createdOn: Long,
    val validUntil: Long? = null,
    val itemsJson: String = "[]",
    val discountCents: Long = 0,
    val notes: String = "",
    val status: QuoteStatus = QuoteStatus.ABERTO,
    val createdAt: Long = 0
)
