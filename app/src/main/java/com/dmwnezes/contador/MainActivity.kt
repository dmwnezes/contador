package com.dmwnezes.contador

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val ptBR: Locale = Locale("pt", "BR")
val fmtData: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ptBR)
val fmtHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", ptBR)
private val fmtHoje = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)

fun dataTexto(c: Contador) = c.data.format(fmtData) + (c.hora?.let { " · " + it.format(fmtHora) } ?: "")
fun numero(n: Long): String = "%,d".format(ptBR, n)

class MainActivity : ComponentActivity() {
    private val pedirAvisos = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Avisos.agendar(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
    // Grava já os campos novos (data de criação etc.) de quem veio da versão anterior.
    LaunchedEffect(Unit) { armazem.salvar(estado) }

    // Relógio que bate a cada segundo; também vira o dia sozinho.
    var agora by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { agora = LocalDateTime.now(); delay(1000) } }
    val hoje = agora.toLocalDate()

    var editando by remember { mutableStateOf<Contador?>(null) }
    var criando by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf<Contador?>(null) }
    val haptico = LocalHapticFeedback.current
    val ctx = LocalContext.current

    val destaque = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()
    val lista = ordenar(estado.contadores, hoje)
    val brilho by animateColorAsState(destaque?.let { cor(it) } ?: Rosa, tween(900), label = "brilho")
    val margens = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        Modifier
            .fillMaxSize()
            .background(Noite)
            .background(Brush.radialGradient(listOf(brilho.copy(alpha = 0.14f), Color.Transparent), radius = 1200f, center = androidx.compose.ui.geometry.Offset(900f, 0f)))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = margens.calculateTopPadding() + 12.dp, bottom = margens.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CONTADOR",
                            style = TextStyle(brush = Brush.horizontalGradient(listOf(Ambar, Rosa)), fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 3.sp),
                        )
                        Text(hoje.format(fmtHoje).replaceFirstChar { it.uppercase() }, color = Suave, fontSize = 13.sp)
                    }
                    FilledIconButton(
                        onClick = { haptico.performHapticFeedback(HapticFeedbackType.LongPress); criando = true },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rosa, contentColor = Noite),
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Rounded.Add, "Novo contador") }
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                if (destaque != null) Destaque(destaque, agora, aoCompartilhar = { compartilhar(ctx, destaque, agora) }) else Vazio()
                Spacer(Modifier.height(14.dp))
            }
            if (lista.isNotEmpty()) item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("MEUS CONTADORES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text(if (lista.size == 1) "1 contador" else "${lista.size} contadores", color = Suave, fontSize = 12.sp)
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
                    aoEditar = { editando = c },
                    aoCompartilhar = { compartilhar(ctx, c, agora) },
                    aoExcluir = { excluindo = c },
                )
            }
        }
    }

    if (criando || editando != null) EditarDialogo(
        inicial = editando,
        aoFechar = { criando = false; editando = null },
        aoSalvar = { novo ->
            val existe = estado.contadores.any { it.id == novo.id }
            val lista2 = if (existe) estado.contadores.map { if (it.id == novo.id) novo else it } else estado.contadores + novo
            mudar(Estado(lista2, if (existe) estado.destaque else novo.id))
            criando = false; editando = null
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
fun Destaque(c: Contador, agora: LocalDateTime, aoCompartilhar: () -> Unit) {
    val hoje = agora.toLocalDate()
    val dias = diasAte(c.data, hoje)
    val f = fase(dias)
    val seg = segundosAte(c, agora)
    val acc = cor(c)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Cartao)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Ceu(f, Modifier.matchParentSize())
            AnimatedContent(
                targetState = c.id,
                transitionSpec = {
                    (fadeIn(tween(400, 120)) + slideInVertically(tween(400, 120)) { it / 8 }) togetherWith fadeOut(tween(200))
                },
                label = "destaque",
            ) { _ -> TopoDestaque(c, agora, dias, f, aoCompartilhar) }
        }

        // Faixa inferior: relógio, progresso ou tempo por extenso.
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (f == Fase.FUTURO || (f == Fase.HOJE && seg > 0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bloco(seg / 86400, "dias", Modifier.weight(1f))
                    Bloco(seg % 86400 / 3600, "horas", Modifier.weight(1f))
                    Bloco(seg % 3600 / 60, "min", Modifier.weight(1f))
                    Bloco(seg % 60, "seg", Modifier.weight(1f))
                }
            }
            progresso(c, hoje)?.takeIf { f == Fase.FUTURO }?.let { p ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text("Caminho desde ${c.criado.format(DateTimeFormatter.ofPattern("dd/MM", ptBR))}", color = Suave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${(p * 100).toInt()}%", color = acc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Barra(p, acc)
                }
            }
            if (f == Fase.PASSADO) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = acc, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Há ${tempoLongo(abs(dias))}", color = Color.White, fontSize = 14.sp)
                }
            }
            if (f == Fase.HOJE && seg <= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Celebration, null, tint = acc, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Chegou o dia!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TopoDestaque(c: Contador, agora: LocalDateTime, dias: Long, f: Fase, aoCompartilhar: () -> Unit) {
    val acc = cor(c)
    var unidade by remember(c.id) { mutableStateOf(Unidade.DIAS) }
    val n = valorEm(unidade, c, agora)

    // Número sobe de 0 até o valor ao abrir ou trocar de contador.
    val anim = remember(c.id) { Animatable(0f) }
    LaunchedEffect(c.id, abs(dias)) { anim.animateTo(abs(dias).toFloat(), tween(if (anim.value == 0f) 1100 else 400, easing = FastOutSlowInEasing)) }

    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Selo(c, 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                when (f) { Fase.FUTURO -> "FALTAM"; Fase.HOJE -> "É HOJE"; Fase.PASSADO -> "JÁ SE PASSARAM" },
                color = Noite, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.clip(CircleShape).background(acc).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = aoCompartilhar) { Icon(Icons.Rounded.Share, "Compartilhar", tint = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Text(c.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 12.dp))
        Spacer(Modifier.height(2.dp))
        Text(dataTexto(c), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        if (f == Fase.HOJE && unidade == Unidade.DIAS) {
            val inf = rememberInfiniteTransition(label = "hoje")
            val s by inf.animateFloat(1f, 1.06f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
            Text(
                "HOJE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 88.sp, lineHeight = 88.sp,
                modifier = Modifier.scale(s).clickable(remember { MutableInteractionSource() }, null) { unidade = Unidade.HORAS },
            )
        } else {
            val mostrado = if (unidade == Unidade.DIAS) anim.value.toLong() else n
            val texto = numero(mostrado)
            val tam = when { numero(n).length <= 3 -> 96.sp; numero(n).length <= 5 -> 76.sp; numero(n).length <= 7 -> 58.sp; else -> 46.sp }
            AnimatedContent(
                targetState = unidade,
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), 0.9f)) togetherWith fadeOut(tween(150)) },
                label = "unidade",
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                    unidade = Unidade.entries[(unidade.ordinal + 1) % Unidade.entries.size]
                },
            ) { u ->
                Row {
                    Text(
                        if (u == unidade) texto else numero(valorEm(u, c, agora)),
                        style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, acc)), fontWeight = FontWeight.Black, fontSize = tam, lineHeight = tam),
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(u.rotulo(n), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.alignByBaseline())
                }
            }
        }
        Text(
            if (unidade == Unidade.SEMANAS && abs(dias) % 7 != 0L) "e ${abs(dias) % 7} ${if (abs(dias) % 7 == 1L) "dia" else "dias"} · toque para trocar"
            else "Toque para trocar a unidade",
            color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
        )
    }
}

@Composable
fun Vazio() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Cartao).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Nenhum contador ainda", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Toque no + para criar o primeiro.", color = Suave)
    }
}

@Composable
fun Linha(
    c: Contador, hoje: LocalDate, ativo: Boolean, modifier: Modifier,
    aoTocar: () -> Unit, aoEditar: () -> Unit, aoCompartilhar: () -> Unit, aoExcluir: () -> Unit,
) {
    val dias = diasAte(c.data, hoje)
    val n = abs(dias)
    val acc = cor(c)
    val toque = remember { MutableInteractionSource() }
    val apertado by toque.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apertado) 0.97f else 1f, spring(dampingRatio = 0.5f), label = "escala")
    val borda by animateColorAsState(if (ativo) acc else Color.Transparent, tween(400), label = "borda")
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier
            .scale(escala)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (ativo) CartaoClaro else Cartao)
            .border(1.5.dp, borda, RoundedCornerShape(20.dp))
            .clickable(interactionSource = toque, indication = null, onClick = aoTocar)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Selo(c, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.nome, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dataTexto(c), color = Suave, fontSize = 12.sp, maxLines = 1)
            progresso(c, hoje)?.takeIf { dias > 0 }?.let {
                Spacer(Modifier.height(4.dp))
                Barra(it, acc, 4.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 52.dp)) {
            Text(if (dias == 0L) "Hoje" else numero(n), color = acc, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
            Text(
                when { dias > 1 -> "faltam"; dias == 1L -> "falta"; dias == 0L -> ""; n == 1L -> "dia atrás"; else -> "dias atrás" },
                color = Suave, fontSize = 11.sp, maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opções", tint = Suave) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = CartaoClaro) {
                DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; aoEditar() })
                DropdownMenuItem(text = { Text("Compartilhar") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; aoCompartilhar() })
                DropdownMenuItem(text = { Text("Excluir", color = Rosa) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Rosa) }, onClick = { menu = false; aoExcluir() })
            }
        }
    }
}
