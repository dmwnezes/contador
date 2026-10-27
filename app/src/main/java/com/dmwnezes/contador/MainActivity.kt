package com.dmwnezes.contador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ptBR = Locale("pt", "BR")
private val fmtData = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ptBR)
private val fmtHoje = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val armazem = Armazem(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Rosa, onPrimary = Noite, surface = Cartao, background = Noite)) {
                App(armazem)
            }
        }
    }
}

@Composable
fun App(armazem: Armazem) {
    var estado by remember { mutableStateOf(armazem.ler()) }
    fun mudar(novo: Estado) { estado = novo; armazem.salvar(novo) }

    // Relógio que bate a cada segundo; também vira o dia sozinho.
    var agora by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { agora = LocalDateTime.now(); delay(1000) } }
    val hoje = agora.toLocalDate()

    var criando by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf<Contador?>(null) }
    val haptico = LocalHapticFeedback.current

    val destaque = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()
    val lista = ordenar(estado.contadores, hoje)
    val corFundo by animateColorAsState(destaque?.let { corDa(fase(diasAte(it.data, hoje))) } ?: Rosa, tween(900), label = "brilho")

    Scaffold(
        containerColor = Noite,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { haptico.performHapticFeedback(HapticFeedbackType.LongPress); criando = true },
                containerColor = Rosa, contentColor = Noite,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Novo contador", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(corFundo.copy(alpha = 0.16f), Color.Transparent), radius = 1100f, center = androidx.compose.ui.geometry.Offset(540f, 0f)))
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "CONTADOR",
                        style = TextStyle(brush = Brush.horizontalGradient(listOf(Ambar, Rosa)), fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 3.sp),
                    )
                    Text(hoje.format(fmtHoje).replaceFirstChar { it.uppercase() }, color = Suave, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    if (destaque != null) Destaque(destaque, agora) else Vazio()
                    Spacer(Modifier.height(16.dp))
                }
                if (lista.isNotEmpty()) item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("MEUS CONTADORES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Text("${lista.size} no total", color = Suave, fontSize = 12.sp)
                    }
                }
                items(lista, key = { it.id }) { c ->
                    Linha(
                        c, hoje, ativo = c.id == destaque?.id,
                        modifier = Modifier.animateItem(fadeInSpec = tween(350), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(250)),
                        aoTocar = {
                            haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            mudar(estado.copy(destaque = c.id))
                        },
                        aoExcluir = { excluindo = c },
                    )
                }
            }
        }
    }

    if (criando) NovoDialogo(
        aoFechar = { criando = false },
        aoSalvar = { nome, data ->
            val id = System.currentTimeMillis().toString()
            mudar(Estado(estado.contadores + Contador(id, nome, data), id))
            criando = false
        },
    )

    excluindo?.let { c ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            containerColor = Cartao,
            title = { Text("Excluir contador?") },
            text = { Text("\"${c.nome}\" será removido.") },
            confirmButton = {
                TextButton(onClick = {
                    val resto = estado.contadores.filter { it.id != c.id }
                    mudar(Estado(resto, if (estado.destaque == c.id) resto.firstOrNull()?.id else estado.destaque))
                    excluindo = null
                }) { Text("Excluir", color = Rosa) }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("Cancelar", color = Suave) } },
        )
    }
}

@Composable
fun Destaque(c: Contador, agora: LocalDateTime) {
    val dias = diasAte(c.data, agora.toLocalDate())
    val f = fase(dias)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
    ) {
        Ceu(f, Modifier.matchParentSize())
        // Troca de destaque: o conteúdo antigo sobe e some, o novo entra por baixo.
        AnimatedContent(
            targetState = c.id,
            transitionSpec = {
                (fadeIn(tween(400, 120)) + slideInVertically(tween(400, 120)) { it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it / 8 })
            },
            label = "destaque",
        ) { _ ->
            ConteudoDestaque(c, agora, dias, f)
        }
    }
}

@Composable
private fun ConteudoDestaque(c: Contador, agora: LocalDateTime, dias: Long, f: Fase) {
    val cor = corDa(f)
    val n = kotlin.math.abs(dias)

    // Número sobe de 0 até o valor ao abrir ou trocar de contador.
    val anim = remember(c.id) { Animatable(0f) }
    LaunchedEffect(c.id, n) { anim.animateTo(n.toFloat(), tween(if (anim.value == 0f) 1100 else 400, easing = FastOutSlowInEasing)) }

    Column(Modifier.fillMaxWidth().padding(22.dp)) {
        Text(
            when (f) { Fase.FUTURO -> "FALTAM"; Fase.HOJE -> "É HOJE"; Fase.PASSADO -> "JÁ SE PASSARAM" },
            color = Noite, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp,
            modifier = Modifier.clip(CircleShape).background(cor).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(c.nome.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 30.sp)
        Text(c.data.format(fmtData), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        if (f == Fase.HOJE) {
            val inf = rememberInfiniteTransition(label = "hoje")
            val s by inf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
            Text("HOJE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 104.sp, lineHeight = 104.sp, modifier = Modifier.scale(s))
        } else {
            Text(
                "%,d".format(ptBR, anim.value.toLong()),
                style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, cor)), fontWeight = FontWeight.Black, fontSize = 116.sp, lineHeight = 112.sp),
            )
            Text(if (n == 1L) "DIA" else "DIAS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 3.sp)
        }
        if (f == Fase.FUTURO) {
            // Até 0h do dia do evento (horário exato nem sempre é conhecido).
            val s = ChronoUnit.SECONDS.between(agora, c.data.atStartOfDay()).coerceAtLeast(0)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bloco(s / 86400, "dias")
                Bloco(s % 86400 / 3600, "horas")
                Bloco(s % 3600 / 60, "min")
                Bloco(s % 60, "seg")
            }
        }
        if (f == Fase.PASSADO) {
            Spacer(Modifier.height(10.dp))
            Text(tempoLongo(n), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }
    }
}

/** "12 anos, 3 meses e 5 dias" a partir de dias corridos (aproximado para meses). */
fun tempoLongo(dias: Long): String {
    val anos = dias / 365
    val meses = dias % 365 / 30
    val d = dias % 365 % 30
    val partes = buildList {
        if (anos > 0) add("$anos ${if (anos == 1L) "ano" else "anos"}")
        if (meses > 0) add("$meses ${if (meses == 1L) "mês" else "meses"}")
        if (d > 0 || isEmpty()) add("$d ${if (d == 1L) "dia" else "dias"}")
    }
    return if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " e " + partes.last()
}

@Composable
fun Vazio() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Cartao).padding(28.dp),
    ) {
        Text("Nenhum contador ainda", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Toque em \"Novo contador\" para criar o primeiro.", color = Suave)
    }
}

@Composable
fun Linha(c: Contador, hoje: LocalDate, ativo: Boolean, modifier: Modifier, aoTocar: () -> Unit, aoExcluir: () -> Unit) {
    val dias = diasAte(c.data, hoje)
    val n = kotlin.math.abs(dias)
    val cor = corDa(fase(dias))
    val toque = remember { MutableInteractionSource() }
    val apertado by toque.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apertado) 0.96f else 1f, spring(dampingRatio = 0.5f), label = "escala")
    val borda by animateColorAsState(if (ativo) cor else Color.Transparent, tween(400), label = "borda")

    Row(
        modifier
            .scale(escala)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (ativo) Brush.horizontalGradient(listOf(cor.copy(alpha = 0.18f), Cartao)) else Brush.horizontalGradient(listOf(Cartao, Cartao)))
            .border(1.5.dp, borda, RoundedCornerShape(20.dp))
            .clickable(interactionSource = toque, indication = null, onClick = aoTocar)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(cor.copy(alpha = 0.14f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (n > 99999) "${n / 1000}k" else "%,d".format(ptBR, n), color = cor, fontWeight = FontWeight.Black, fontSize = if (n > 999) 18.sp else 24.sp, maxLines = 1)
            Text(
                when { dias > 1 -> "FALTAM"; dias == 1L -> "FALTA"; dias == 0L -> "HOJE"; else -> "DESDE" },
                color = cor.copy(alpha = 0.8f), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.nome, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(c.data.format(fmtData), color = Suave, fontSize = 13.sp)
        }
        IconButton(onClick = aoExcluir) { Icon(Icons.Rounded.DeleteOutline, "Excluir", tint = Suave) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovoDialogo(aoFechar: () -> Unit, aoSalvar: (String, LocalDate) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<LocalDate?>(null) }
    var escolhendo by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cartao,
        title = { Text("Novo contador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it.take(60) },
                    label = { Text("Nome") }, placeholder = { Text("Ex.: aniversário, viagem") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { escolhendo = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(data?.format(fmtData) ?: "Escolher data", color = Color.White)
                }
                Text("Data no futuro conta quanto falta. Data no passado conta quantos dias já se passaram.", color = Suave, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank() && data != null, onClick = { aoSalvar(nome.trim(), data!!) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar", color = Suave) } },
    )

    if (escolhendo) {
        // O DatePicker trabalha com milissegundos em UTC.
        val inicial = (data ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val st = rememberDatePickerState(initialSelectedDateMillis = inicial, yearRange = 1900..2200)
        DatePickerDialog(
            onDismissRequest = { escolhendo = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { data = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    escolhendo = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { escolhendo = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }
}
