package com.dmwnezes.contador

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Rosa = Color(0xFFFF5D8F)
val Violeta = Color(0xFF6C5CFF)
val Ciano = Color(0xFF2EC5FF)
val Ambar = Color(0xFFFFB34D)
val Verde = Color(0xFF3FD18A)
val Anil = Color(0xFF2E3AFF)

/** Cores do tema atual. Vidro = camada translúcida sobre o fundo com bolhas. */
data class Paleta(
    val escuro: Boolean,
    val fundo: Color,
    val cartao: Color, // superfície sólida (diálogos, menus)
    val texto: Color,
    val suave: Color,
    val vidro: Color,
    val vidroForte: Color,
    val borda: Color,
    val trilho: Color,
    val bolhas: List<Color>,
    val alfaBolhas: Float,
)

fun paleta(escuro: Boolean, acc: Color, bolhas: Boolean, p: Periodo?): Paleta {
    val outras = when (p) {
        Periodo.MANHA -> listOf(Ambar, Ciano)
        Periodo.TARDE -> listOf(Ciano, Verde)
        Periodo.NOITE -> listOf(Violeta, Anil)
        null -> listOf(Violeta, Ciano)
    }
    val alfa = if (!bolhas) 0f else if (escuro) 0.5f else 0.35f
    return if (escuro) Paleta(
        escuro = true, fundo = Color(0xFF0B0912), cartao = Color(0xFF1A1724), texto = Color.White,
        suave = Color.White.copy(alpha = 0.62f), vidro = Color.White.copy(alpha = 0.07f), vidroForte = Color.White.copy(alpha = 0.11f),
        borda = Color.White.copy(alpha = 0.13f), trilho = Color.White.copy(alpha = 0.12f), bolhas = listOf(acc) + outras, alfaBolhas = alfa,
    ) else Paleta(
        escuro = false, fundo = Color(0xFFEEF0F7), cartao = Color(0xFFFAFAFD), texto = Color(0xFF16142A),
        suave = Color(0xFF5A5878), vidro = Color.White.copy(alpha = 0.55f), vidroForte = Color.White.copy(alpha = 0.75f),
        borda = Color.White.copy(alpha = 0.9f), trilho = Color(0x1416142A), bolhas = listOf(acc) + outras, alfaBolhas = alfa,
    )
}

val LocalPaleta = staticCompositionLocalOf { paleta(true, Rosa, true, null) }

enum class Fase { FUTURO, HOJE, PASSADO }

fun fase(dias: Long) = when { dias > 0 -> Fase.FUTURO; dias == 0L -> Fase.HOJE; else -> Fase.PASSADO }

fun cor(c: Contador) = Color(corArgb(c))

fun icone(chave: String): ImageVector = when (chave) {
    "jogo" -> Icons.Rounded.SportsEsports
    "viagem" -> Icons.Rounded.Flight
    "aniversario" -> Icons.Rounded.Cake
    "amor" -> Icons.Rounded.Favorite
    "festa" -> Icons.Rounded.Celebration
    "estudo" -> Icons.Rounded.School
    "trabalho" -> Icons.Rounded.Work
    "esporte" -> Icons.Rounded.SportsSoccer
    "estrela" -> Icons.Rounded.Star
    else -> Icons.Rounded.Event
}

/** Fundo com três manchas de cor desfocadas que flutuam devagar. */
@Composable
fun FundoBolhas(modifier: Modifier = Modifier) {
    val p = LocalPaleta.current
    val inf = rememberInfiniteTransition(label = "bolhas")
    val a by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c0 by animateColorAsState(p.bolhas[0], tween(800), label = "c0")
    val c1 by animateColorAsState(p.bolhas[1], tween(800), label = "c1")
    val c2 by animateColorAsState(p.bolhas[2], tween(800), label = "c2")
    val alfa by animateFloatAsState(p.alfaBolhas, tween(600), label = "alfa")
    Canvas(modifier.background(p.fundo)) {
        if (alfa <= 0.01f) return@Canvas
        fun bolha(cor: Color, x: Float, y: Float, r: Float) {
            val centro = Offset(x * size.width, y * size.height)
            drawCircle(Brush.radialGradient(listOf(cor.copy(alpha = alfa), Color.Transparent), centro, r * size.width), r * size.width, centro)
        }
        bolha(c0, 0.1f + a * 0.12f, 0.12f + a * 0.08f, 0.85f)
        bolha(c1, 0.95f - b * 0.15f, 0.48f - b * 0.06f, 0.8f)
        bolha(c2, 0.3f + b * 0.1f, 0.98f - a * 0.05f, 0.7f)
    }
}

/** Camada de vidro: fundo translúcido + borda clara. */
fun Modifier.vidro(p: Paleta, forma: Shape = RoundedCornerShape(26.dp), forte: Boolean = false) =
    this.clip(forma).background(if (forte) p.vidroForte else p.vidro).border(1.dp, p.borda, forma)

/** Bloco do relógio: o número desliza ao mudar. */
@Composable
fun Bloco(v: Long, rotulo: String, modifier: Modifier = Modifier) {
    val p = LocalPaleta.current
    Column(
        modifier.vidro(p, RoundedCornerShape(16.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = v,
            transitionSpec = {
                (slideInVertically(tween(300)) { -it } + fadeIn(tween(300))) togetherWith
                    (slideOutVertically(tween(300)) { it } + fadeOut(tween(200)))
            },
            label = rotulo,
        ) { n ->
            Text("%02d".format(n), color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center)
        }
        Text(rotulo.uppercase(), color = p.suave, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

/** Barra de progresso arredondada com preenchimento animado e brilho no escuro. */
@Composable
fun Barra(prog: Float, cor: Color, altura: Dp = 6.dp, modifier: Modifier = Modifier) {
    val p = LocalPaleta.current
    val v by animateFloatAsState(prog, tween(900, easing = FastOutSlowInEasing), label = "barra")
    Box(modifier.fillMaxWidth().height(altura).clip(CircleShape).background(p.trilho)) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(CircleShape).background(cor))
    }
}

/** Ícone do contador num quadrado arredondado tingido com a cor dele. */
@Composable
fun Selo(c: Contador, tamanho: Dp = 44.dp, cor: Color = cor(c)) {
    Box(
        Modifier.size(tamanho).clip(RoundedCornerShape(tamanho * 0.32f)).background(cor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone(c.icone), null, tint = cor, modifier = Modifier.size(tamanho * 0.5f))
    }
}
