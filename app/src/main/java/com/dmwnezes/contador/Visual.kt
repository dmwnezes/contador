package com.dmwnezes.contador

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

val Noite = Color(0xFF160C26)
val Crepusculo = Color(0xFF2A1440)
val Cartao = Color(0xFF22123A)
val CartaoClaro = Color(0xFF2C1A48)
val Borda = Color(0xFF3A2559)
val Suave = Color(0xFFB9A6CF)
val Rosa = Color(0xFFFF5D8F)
val Ambar = Color(0xFFFFB34D)
val Turquesa = Color(0xFF3EE0CF)
val Madrugada = Color(0xFF0B1030)

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

private data class Estrela(val x: Float, val y: Float, val r: Float, val fase: Float)

private val estrelas = Random(7).let { rnd -> List(36) { Estrela(rnd.nextFloat(), rnd.nextFloat() * 0.75f, 0.8f + rnd.nextFloat() * 1.4f, rnd.nextFloat() * 2f * PI.toFloat()) } }

/**
 * Céu animado do destaque. O sol fica no canto inferior direito, longe do texto.
 * Futuro: sol se pondo com faixas subindo. Hoje: sol alto. Passado: noite com lua e estrelas.
 */
@Composable
fun Ceu(f: Fase, modifier: Modifier = Modifier) {
    val spec = tween<Color>(900)
    val topo by animateColorAsState(if (f == Fase.PASSADO) Madrugada else Crepusculo, spec, label = "topo")
    val base by animateColorAsState(when (f) { Fase.PASSADO -> Color(0xFF1B2A5A); Fase.HOJE -> Color(0xFFA3335F); else -> Color(0xFF5E1D55) }, spec, label = "base")
    // 1 = sol alto, 0 = metade abaixo do horizonte, -1 = escondido.
    val altura by animateFloatAsState(when (f) { Fase.PASSADO -> -1.2f; Fase.HOJE -> 1f; else -> 0f }, tween(1200, easing = FastOutSlowInEasing), label = "sol")
    val noite by animateFloatAsState(if (f == Fase.PASSADO) 1f else 0f, tween(900), label = "noite")

    val inf = rememberInfiniteTransition(label = "ceu")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t")
    val pulso by inf.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulso")

    Canvas(modifier.clipToBounds()) {
        drawRect(Brush.verticalGradient(listOf(topo, base)))

        estrelas.forEach { e ->
            val a = (0.3f + 0.7f * (0.5f + 0.5f * sin(t * 4f * PI.toFloat() + e.fase))) * (0.25f + 0.75f * noite)
            drawCircle(Color.White.copy(alpha = a), e.r.dp.toPx() / 2f, Offset(e.x * size.width, e.y * size.height))
        }

        // Lua crescente (só à noite).
        if (noite > 0.01f) {
            val r = size.width * 0.07f
            val c = Offset(size.width * 0.8f, size.height * 0.52f)
            val sombra = c + Offset(r * 0.45f, -r * 0.2f)
            val lua = Path.combine(
                PathOperation.Difference,
                Path().apply { addOval(Rect(c, r)) },
                Path().apply { addOval(Rect(sombra, r)) },
            )
            drawPath(lua, Color(0xFFFFF4D6).copy(alpha = noite))
        }

        val raio = size.width * 0.2f * pulso
        // Futuro: metade abaixo do horizonte (base do céu). Hoje: sobe mais.
        val centro = Offset(size.width * 0.82f, size.height + raio * 0.1f - altura * raio * 0.6f)
        drawCircle(Brush.radialGradient(listOf(Ambar.copy(alpha = 0.35f * (1f - noite)), Color.Transparent), centro, raio * 2.2f), raio * 2.2f, centro)
        val faixa = 7.dp.toPx()
        val passo = faixa + 4.dp.toPx()
        val desloc = t * passo * 6f % passo
        var y = centro.y - raio - passo + desloc
        while (y < centro.y + raio) {
            clipRect(top = y, bottom = y + faixa) {
                drawCircle(Brush.verticalGradient(listOf(Ambar, Rosa), centro.y - raio, centro.y + raio * 0.4f), raio, centro, alpha = 1f - noite)
            }
            y += passo
        }
    }
}

/** Bloco do relógio: o número desliza ao mudar. Ocupa a largura que o Row der. */
@Composable
fun Bloco(v: Long, rotulo: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 10.dp),
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
            Text("%02d".format(n), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center)
        }
        Text(rotulo.uppercase(), color = Suave, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

/** Barra de progresso arredondada com preenchimento animado. */
@Composable
fun Barra(p: Float, cor: Color, altura: Dp = 6.dp, modifier: Modifier = Modifier) {
    val v by animateFloatAsState(p, tween(900, easing = FastOutSlowInEasing), label = "barra")
    Box(modifier.fillMaxWidth().height(altura).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(cor.copy(alpha = 0.7f), cor))))
    }
}

/** Ícone do contador dentro de um círculo na cor dele. */
@Composable
fun Selo(c: Contador, tamanho: Dp = 36.dp) {
    Box(
        Modifier.size(tamanho).clip(CircleShape).background(cor(c).copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icone(c.icone), null, tint = cor(c), modifier = Modifier.size(tamanho * 0.55f))
    }
}
