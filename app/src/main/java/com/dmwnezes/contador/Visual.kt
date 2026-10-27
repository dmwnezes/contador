package com.dmwnezes.contador

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

val Noite = Color(0xFF160C26)
val Crepusculo = Color(0xFF2A1440)
val Cartao = Color(0xFF22123A)
val Borda = Color(0xFF3A2559)
val Suave = Color(0xFFB9A6CF)
val Rosa = Color(0xFFFF5D8F)
val Ambar = Color(0xFFFFB34D)
val Turquesa = Color(0xFF3EE0CF)
val Magenta = Color(0xFF6A2058)
val Madrugada = Color(0xFF0B1030)

enum class Fase { FUTURO, HOJE, PASSADO }

fun fase(dias: Long) = when { dias > 0 -> Fase.FUTURO; dias == 0L -> Fase.HOJE; else -> Fase.PASSADO }

fun corDa(f: Fase) = when (f) { Fase.FUTURO -> Ambar; Fase.HOJE -> Rosa; Fase.PASSADO -> Turquesa }

private data class Estrela(val x: Float, val y: Float, val r: Float, val fase: Float)

private val estrelas = Random(7).let { rnd -> List(40) { Estrela(rnd.nextFloat(), rnd.nextFloat() * 0.6f, 0.8f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 2f * PI.toFloat()) } }

/**
 * Céu animado atrás do destaque. Futuro: pôr do sol com faixas subindo.
 * Hoje: sol inteiro e mais quente. Passado: noite com estrelas piscando.
 */
@Composable
fun Ceu(f: Fase, modifier: Modifier = Modifier) {
    val spec = tween<Color>(900)
    val topo by animateColorAsState(if (f == Fase.PASSADO) Madrugada else Crepusculo, spec, label = "topo")
    val meio by animateColorAsState(when (f) { Fase.PASSADO -> Color(0xFF1C1650); Fase.HOJE -> Color(0xFF7A1F5C); else -> Color(0xFF3B1550) }, spec, label = "meio")
    val base by animateColorAsState(when (f) { Fase.PASSADO -> Color(0xFF123A4A); Fase.HOJE -> Color(0xFFC2416B); else -> Magenta }, spec, label = "base")
    val sol by animateFloatAsState(when (f) { Fase.PASSADO -> 1.25f; Fase.HOJE -> 0.38f; else -> 0.62f }, tween(1200, easing = FastOutSlowInEasing), label = "sol")
    val estrelasAlfa by animateFloatAsState(if (f == Fase.PASSADO) 1f else 0.25f, tween(900), label = "estrelas")

    val inf = rememberInfiniteTransition(label = "ceu")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t")
    val pulso by inf.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulso")

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(topo, meio, base)))

        estrelas.forEach { e ->
            val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 2f + e.fase))) * estrelasAlfa
            drawCircle(Color.White.copy(alpha = a), e.r.dp.toPx() / 2f, Offset(e.x * size.width, e.y * size.height))
        }

        // Sol com faixas que sobem devagar.
        val raio = size.width * 0.34f * pulso
        val centro = Offset(size.width / 2f, size.height * (0.55f + sol * 0.45f) + raio * 0.35f)
        drawCircle(Brush.radialGradient(listOf(Ambar.copy(alpha = 0.45f), Color.Transparent), centro, raio * 1.9f), raio * 1.9f, centro)
        val faixa = 9.dp.toPx()
        val folga = 5.dp.toPx()
        val passo = faixa + folga
        val desloc = t * passo * 6f % passo
        clipRect(top = 0f, bottom = centro.y - raio * 0.15f) {
            var y = centro.y - raio - passo + desloc
            while (y < centro.y + raio) {
                clipRect(top = y, bottom = y + faixa) {
                    drawCircle(Brush.verticalGradient(listOf(Ambar, Rosa), centro.y - raio, centro.y + raio * 0.3f), raio, centro)
                }
                y += passo
            }
        }
        // Linha do horizonte.
        drawRect(Color.White.copy(alpha = 0.08f), Offset(0f, centro.y - raio * 0.15f), Size(size.width, 1.dp.toPx()))
    }
}

/** Dígitos que deslizam ao mudar, como um painel de aeroporto. */
@Composable
fun Bloco(v: Long, rotulo: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Noite.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .widthIn(min = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = v,
            transitionSpec = {
                (slideInVertically(tween(320)) { -it } + fadeIn(tween(320))) togetherWith
                    (slideOutVertically(tween(320)) { it } + fadeOut(tween(200)))
            },
            label = rotulo,
        ) { n ->
            Text("%02d".format(n), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }
        Text(rotulo.uppercase(), color = Suave, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}
