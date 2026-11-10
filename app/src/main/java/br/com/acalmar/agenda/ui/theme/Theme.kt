package br.com.acalmar.agenda.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.R

/** Paleta tirada do logo "Massoterapia Acalmar". */
object Brand {
    val Cream = Color(0xFFFBF5EE)          // fundo das telas
    val Beige = Color(0xFFF5DBC1)          // bege do logo: topo e destaques suaves
    val BeigeSoft = Color(0xFFF9EBDC)
    val Gold = Color(0xFFD2A67A)           // só detalhes: linhas e ornamentos
    val GoldDeep = Color(0xFFB98450)
    val Bronze = Color(0xFF905B2B)         // botões e destaques
    val BronzeDark = Color(0xFF6E421C)
    val Brown = Color(0xFF4A2F1A)          // textos
    val BrownSoft = Color(0xFF7A5E49)      // textos secundários
    val Card = Color(0xFFFFFDFA)           // cartões
    val Line = Color(0xFFEBD9C5)           // contornos
    val Sage = Color(0xFF4F7044)           // concluído / entradas
    val SageSoft = Color(0xFFE6EEDF)
    val Terracotta = Color(0xFFB0482E)     // faltou / saídas
    val TerracottaSoft = Color(0xFFF8E1DA)
    val Gray = Color(0xFF766C63)
    val GraySoft = Color(0xFFEFE9E3)
}

private val AcalmarColors = lightColorScheme(
    primary = Brand.Bronze,
    onPrimary = Color.White,
    primaryContainer = Brand.Beige,
    onPrimaryContainer = Brand.Brown,
    secondary = Brand.GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Brand.Beige,
    onSecondaryContainer = Brand.Brown,
    tertiary = Brand.Sage,
    onTertiary = Color.White,
    tertiaryContainer = Brand.SageSoft,
    onTertiaryContainer = Color(0xFF263B1F),
    background = Brand.Cream,
    onBackground = Brand.Brown,
    surface = Brand.Cream,
    onSurface = Brand.Brown,
    surfaceVariant = Brand.BeigeSoft,
    onSurfaceVariant = Brand.BrownSoft,
    surfaceTint = Brand.Bronze,
    outline = Brand.Gold,
    outlineVariant = Brand.Line,
    error = Brand.Terracotta,
    onError = Color.White,
    errorContainer = Brand.TerracottaSoft,
    onErrorContainer = Color(0xFF5C1F10),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Brand.Card,
    surfaceContainer = Color(0xFFFAF0E4),
    surfaceContainerHigh = Color(0xFFF8ECDD),
    surfaceContainerHighest = Color(0xFFF4E5D3),
    surfaceBright = Brand.Cream,
    surfaceDim = Color(0xFFEADCCB),
    scrim = Color(0xFF2B1A0E),
    inverseSurface = Brand.Brown,
    inverseOnSurface = Brand.Cream,
    inversePrimary = Brand.Gold
)

/** Fonte dos títulos, no espírito das letras do logo. Números ficam na fonte padrão. */
val Marcellus = FontFamily(Font(R.font.marcellus_regular, FontWeight.Normal))

private val Base = Typography()

private val AcalmarTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontFamily = Marcellus, fontWeight = FontWeight.Normal),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Marcellus, fontWeight = FontWeight.Normal),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Marcellus, fontWeight = FontWeight.Normal),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Marcellus, fontWeight = FontWeight.Normal),
    titleLarge = Base.titleLarge.copy(fontFamily = Marcellus, fontWeight = FontWeight.Normal)
)

private val AcalmarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AcalmarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AcalmarColors,
        typography = AcalmarTypography,
        shapes = AcalmarShapes,
        content = content
    )
}
