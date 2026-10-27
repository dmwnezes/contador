package com.dmwnezes.contador

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

const val EXTRA_COMPARTILHAR = "compartilhar"

class MainActivity : ComponentActivity() {
    private val pedirAvisos = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val erro = arquivoDeErro(this)
        if (erro.exists()) { mostrarErro(erro); return }
        // Falhas ao agendar não podem impedir o app de abrir.
        runCatching { Avisos.agendar(this) }
        runCatching { Atualizador.agendar(this) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val armazem = Armazem(this)
        val prefs = Preferencias(this)
        setContent { App(armazem, prefs) }
        compartilharDoAviso(intent)
    }

    /** Tela simples (sem Compose) com o erro que fechou o app na última vez. */
    private fun mostrarErro(arquivo: java.io.File) {
        val texto = arquivo.readText()
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val coluna = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad * 2)
            setBackgroundColor(0xFF0B0912.toInt())
        }
        coluna.addView(android.widget.TextView(this).apply {
            text = "O Contador fechou sozinho"
            textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        coluna.addView(android.widget.TextView(this).apply {
            text = "Copie o erro abaixo e mande no chat para corrigir."
            textSize = 14f; setTextColor(0xB3FFFFFF.toInt()); setPadding(0, pad / 2, 0, pad / 2)
        })
        val botoes = android.widget.LinearLayout(this)
        botoes.addView(android.widget.Button(this).apply {
            text = "Copiar erro"
            setOnClickListener {
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("erro", texto))
                android.widget.Toast.makeText(this@MainActivity, "Erro copiado", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        botoes.addView(android.widget.Button(this).apply {
            text = "Abrir o app"
            setOnClickListener { arquivo.delete(); recreate() }
        })
        coluna.addView(botoes)
        coluna.addView(android.widget.ScrollView(this).apply {
            addView(android.widget.TextView(this@MainActivity).apply {
                text = texto; textSize = 11f; setTextColor(0xFFFFB34D.toInt()); setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE; setPadding(0, pad / 2, 0, 0)
            })
        })
        setContentView(coluna)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        compartilharDoAviso(intent)
    }

    // Botão "Compartilhar" da notificação abre o app com o id do contador.
    private fun compartilharDoAviso(i: Intent?) {
        val id = i?.getStringExtra(EXTRA_COMPARTILHAR) ?: return
        i.removeExtra(EXTRA_COMPARTILHAR)
        Armazem(this).ler().contadores.find { it.id == id }?.let { compartilhar(this, it, LocalDateTime.now()) }
    }
}

sealed interface EstadoAtualizacao {
    data object Nada : EstadoAtualizacao
    data class Disponivel(val v: Atualizador.Versao) : EstadoAtualizacao
    data class Baixando(val v: Atualizador.Versao, val prog: Float) : EstadoAtualizacao
    data class Erro(val msg: String) : EstadoAtualizacao
    data object EmDia : EstadoAtualizacao
}

@Composable
fun App(armazem: Armazem, prefs: Preferencias) {
    var estado by remember { mutableStateOf(armazem.ler()) }
    fun mudar(novo: Estado) { estado = novo; armazem.salvar(novo) }
    LaunchedEffect(Unit) { armazem.salvar(estado) }

    var cfg by remember { mutableStateOf(prefs.ler()) }
    fun mudarCfg(c: Config) { cfg = c; prefs.salvar(c) }

    var agora by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { agora = LocalDateTime.now(); delay(1000) } }

    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var atualizacao by remember { mutableStateOf<EstadoAtualizacao>(EstadoAtualizacao.Nada) }

    fun instalar(v: Atualizador.Versao) {
        escopo.launch {
            atualizacao = EstadoAtualizacao.Baixando(v, 0f)
            try {
                withContext(Dispatchers.IO) {
                    Atualizador.baixarEInstalar(ctx, v) { p -> atualizacao = EstadoAtualizacao.Baixando(v, p) }
                }
            } catch (e: Exception) {
                atualizacao = EstadoAtualizacao.Erro("Não deu para baixar a versão ${v.nome}. Tente de novo.")
            }
        }
    }

    fun verificar(manual: Boolean) {
        escopo.launch {
            val v = try { withContext(Dispatchers.IO) { Atualizador.verificar(ctx) } } catch (e: Exception) { null }
            atualizacao = when {
                v == null -> if (manual) EstadoAtualizacao.EmDia else EstadoAtualizacao.Nada
                cfg.autoAtualizar && Atualizador.podeInstalar(ctx) -> { instalar(v); EstadoAtualizacao.Baixando(v, 0f) }
                else -> EstadoAtualizacao.Disponivel(v)
            }
        }
    }
    LaunchedEffect(Unit) { verificar(false) }

    val destaque = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()
    val accGlobal = Color(CORES[cfg.cor.coerceIn(CORES.indices)])
    val acc = if (cfg.porContador && destaque != null) cor(destaque) else accGlobal
    val accAnim by animateColorAsState(acc, tween(700), label = "acc")
    val escuro = when (cfg.tema) { "claro" -> false; "auto" -> isSystemInDarkTheme(); else -> true }
    val p = paleta(escuro, accAnim, cfg.bolhas, if (cfg.horario) periodo(agora.hour) else null)

    var tela by remember { mutableStateOf("inicio") }
    BackHandler(tela != "inicio") { tela = "inicio" }

    CompositionLocalProvider(LocalPaleta provides p) {
        MaterialTheme(
            colorScheme = if (escuro) darkColorScheme(primary = acc, surface = p.cartao, onSurface = p.texto, surfaceContainerHigh = p.cartao)
            else lightColorScheme(primary = acc, surface = p.cartao, onSurface = p.texto, surfaceContainerHigh = p.cartao),
        ) {
            Box(Modifier.fillMaxSize()) {
                FundoBolhas(Modifier.matchParentSize())
                AnimatedContent(
                    targetState = tela,
                    transitionSpec = {
                        if (targetState == "aparencia") (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                        else (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    },
                    label = "tela",
                ) { t ->
                    if (t == "aparencia") Aparencia(
                        cfg, ::mudarCfg, atualizacao,
                        aoVerificar = { verificar(true) },
                        aoInstalar = { v -> instalar(v) },
                        aoVoltar = { tela = "inicio" },
                    ) else Inicio(
                        estado, ::mudar, agora, acc, atualizacao,
                        aoAparencia = { tela = "aparencia" },
                        aoInstalar = { v -> instalar(v) },
                    )
                }
            }
        }
    }
}

@Composable
fun Inicio(
    estado: Estado, mudar: (Estado) -> Unit, agora: LocalDateTime, acc: Color, atualizacao: EstadoAtualizacao,
    aoAparencia: () -> Unit, aoInstalar: (Atualizador.Versao) -> Unit,
) {
    val p = LocalPaleta.current
    val hoje = agora.toLocalDate()
    var editando by remember { mutableStateOf<Contador?>(null) }
    var criando by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf<Contador?>(null) }
    val haptico = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val destaque = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()
    val lista = ordenar(estado.contadores, hoje)
    val margens = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = margens.calculateTopPadding() + 12.dp, bottom = margens.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Contador", color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text(hoje.format(fmtHoje).replaceFirstChar { it.uppercase() }, color = p.suave, fontSize = 13.sp)
                }
                BotaoVidro(Icons.Rounded.Palette, "Aparência", aoAparencia)
                BotaoVidro(Icons.Rounded.Add, "Novo contador") { haptico.performHapticFeedback(HapticFeedbackType.LongPress); criando = true }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (atualizacao is EstadoAtualizacao.Disponivel || atualizacao is EstadoAtualizacao.Baixando) item {
            BannerAtualizacao(atualizacao, acc, aoInstalar)
        }
        item {
            if (destaque != null) Destaque(destaque, agora, acc, aoCompartilhar = { compartilhar(ctx, destaque, agora) }) else Vazio()
        }
        if (lista.isNotEmpty()) item {
            Text("Meus contadores", color = p.suave, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        items(lista, key = { it.id }) { c ->
            Linha(
                c, hoje, ativo = c.id == destaque?.id,
                modifier = Modifier.animateItem(fadeInSpec = tween(350), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(250)),
                aoTocar = { haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove); mudar(estado.copy(destaque = c.id)) },
                aoEditar = { editando = c },
                aoCompartilhar = { compartilhar(ctx, c, agora) },
                aoExcluir = { excluindo = c },
            )
        }
    }

    if (criando || editando != null) EditarDialogo(
        inicial = editando,
        aoFechar = { criando = false; editando = null },
        aoSalvar = { novo ->
            val existe = estado.contadores.any { it.id == novo.id }
            val l = if (existe) estado.contadores.map { if (it.id == novo.id) novo else it } else estado.contadores + novo
            mudar(Estado(l, if (existe) estado.destaque else novo.id))
            criando = false; editando = null
        },
    )

    excluindo?.let { c ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            containerColor = p.cartao,
            title = { Text("Excluir contador?", color = p.texto) },
            text = { Text("\"${c.nome}\" será removido.", color = p.suave) },
            confirmButton = {
                TextButton(onClick = {
                    val resto = estado.contadores.filter { it.id != c.id }
                    mudar(Estado(resto, if (estado.destaque == c.id) resto.firstOrNull()?.id else estado.destaque))
                    excluindo = null
                }) { Text("Excluir", color = Color(0xFFE5484D)) }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("Cancelar", color = p.suave) } },
        )
    }
}

@Composable
fun BotaoVidro(icone: androidx.compose.ui.graphics.vector.ImageVector, descricao: String, aoTocar: () -> Unit) {
    val p = LocalPaleta.current
    Box(
        Modifier.size(48.dp).vidro(p, RoundedCornerShape(16.dp), forte = true).clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) { Icon(icone, descricao, tint = p.texto, modifier = Modifier.size(22.dp)) }
}

@Composable
fun BannerAtualizacao(a: EstadoAtualizacao, acc: Color, aoInstalar: (Atualizador.Versao) -> Unit) {
    val p = LocalPaleta.current
    Column(Modifier.fillMaxWidth().vidro(p, RoundedCornerShape(22.dp), forte = true).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = acc, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                when (a) {
                    is EstadoAtualizacao.Baixando -> {
                        Text("Atualizando para ${a.v.nome}", color = p.texto, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${(a.prog * 100).toInt()}% baixado", color = p.suave, fontSize = 12.sp)
                    }
                    is EstadoAtualizacao.Disponivel -> {
                        Text("Nova versão ${a.v.nome}", color = p.texto, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Toque em Atualizar para instalar", color = p.suave, fontSize = 12.sp)
                    }
                    else -> {}
                }
            }
            if (a is EstadoAtualizacao.Disponivel) {
                val ctx = LocalContext.current
                Button(
                    onClick = {
                        if (Atualizador.podeInstalar(ctx)) aoInstalar(a.v)
                        else ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color(0xFF160C26)),
                ) { Text("Atualizar", fontWeight = FontWeight.Bold) }
            }
        }
        if (a is EstadoAtualizacao.Baixando) Barra(a.prog, acc, 4.dp)
    }
}

@Composable
fun Destaque(c: Contador, agora: LocalDateTime, acc: Color, aoCompartilhar: () -> Unit) {
    val p = LocalPaleta.current
    val hoje = agora.toLocalDate()
    val dias = diasAte(c.data, hoje)
    val f = fase(dias)
    val seg = segundosAte(c, agora)

    Column(
        Modifier.fillMaxWidth().vidro(p, RoundedCornerShape(30.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AnimatedContent(
            targetState = c.id,
            transitionSpec = { (fadeIn(tween(400, 100)) + slideInVertically(tween(400, 100)) { it / 10 }) togetherWith fadeOut(tween(180)) },
            label = "destaque",
        ) { _ -> TopoDestaque(c, agora, dias, f, acc, aoCompartilhar) }

        if (f == Fase.FUTURO || (f == Fase.HOJE && seg > 0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bloco(seg / 86400, "dias", Modifier.weight(1f))
                Bloco(seg % 86400 / 3600, "horas", Modifier.weight(1f))
                Bloco(seg % 3600 / 60, "min", Modifier.weight(1f))
                Bloco(seg % 60, "seg", Modifier.weight(1f))
            }
        }
        progresso(c, hoje)?.takeIf { f == Fase.FUTURO }?.let { pr ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("Caminho desde ${c.criado.format(DateTimeFormatter.ofPattern("dd/MM", ptBR))}", color = p.suave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${(pr * 100).toInt()}%", color = p.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Barra(pr, acc)
            }
        }
        if (f == Fase.PASSADO) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = acc, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Há ${tempoLongo(abs(dias))}", color = p.texto, fontSize = 14.sp)
            }
        }
        if (f == Fase.HOJE && seg <= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Celebration, null, tint = acc, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Chegou o dia!", color = p.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TopoDestaque(c: Contador, agora: LocalDateTime, dias: Long, f: Fase, acc: Color, aoCompartilhar: () -> Unit) {
    val p = LocalPaleta.current
    var unidade by remember(c.id) { mutableStateOf(Unidade.DIAS) }
    val n = valorEm(unidade, c, agora)
    val anim = remember(c.id) { Animatable(0f) }
    LaunchedEffect(c.id, abs(dias)) { anim.animateTo(abs(dias).toFloat(), tween(if (anim.value == 0f) 1100 else 400, easing = FastOutSlowInEasing)) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Selo(c, 36.dp, acc)
            Spacer(Modifier.width(10.dp))
            Text(
                when (f) { Fase.FUTURO -> "FALTAM"; Fase.HOJE -> "É HOJE"; Fase.PASSADO -> "JÁ SE PASSARAM" },
                color = p.suave, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = aoCompartilhar, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Share, "Compartilhar", tint = p.suave) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.nome, color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dataTexto(c), color = p.suave, fontSize = 14.sp)
        }
        Column {
            if (f == Fase.HOJE && unidade == Unidade.DIAS) {
                val inf = rememberInfiniteTransition(label = "hoje")
                val s by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
                Text(
                    "Hoje", color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = 88.sp, lineHeight = 88.sp,
                    modifier = Modifier.scale(s).clickable(remember { MutableInteractionSource() }, null) { unidade = Unidade.HORAS },
                )
            } else {
                val mostrado = if (unidade == Unidade.DIAS) anim.value.toLong() else n
                val len = numero(n).length
                val tam = when { len <= 3 -> 104.sp; len <= 5 -> 80.sp; len <= 7 -> 60.sp; else -> 46.sp }
                AnimatedContent(
                    targetState = unidade,
                    transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), 0.92f)) togetherWith fadeOut(tween(150)) },
                    label = "unidade",
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                        unidade = Unidade.entries[(unidade.ordinal + 1) % Unidade.entries.size]
                    },
                ) { u ->
                    Row {
                        Text(
                            if (u == unidade) numero(mostrado) else numero(valorEm(u, c, agora)),
                            color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = tam, lineHeight = tam, letterSpacing = (-2).sp,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(u.rotulo(n), color = p.suave, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.alignByBaseline())
                    }
                }
            }
            Text(
                if (unidade == Unidade.SEMANAS && abs(dias) % 7 != 0L) "e ${abs(dias) % 7} ${if (abs(dias) % 7 == 1L) "dia" else "dias"} · toque para trocar"
                else "Toque no número para trocar a unidade",
                color = p.suave.copy(alpha = 0.8f), fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun Vazio() {
    val p = LocalPaleta.current
    Column(Modifier.fillMaxWidth().vidro(p, RoundedCornerShape(30.dp)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Nenhum contador ainda", color = p.texto, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Toque no + para criar o primeiro.", color = p.suave)
    }
}

@Composable
fun Linha(
    c: Contador, hoje: LocalDate, ativo: Boolean, modifier: Modifier,
    aoTocar: () -> Unit, aoEditar: () -> Unit, aoCompartilhar: () -> Unit, aoExcluir: () -> Unit,
) {
    val p = LocalPaleta.current
    val dias = diasAte(c.data, hoje)
    val n = abs(dias)
    val acc = cor(c)
    val toque = remember { MutableInteractionSource() }
    val apertado by toque.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apertado) 0.97f else 1f, spring(dampingRatio = 0.5f), label = "escala")
    val borda by animateColorAsState(if (ativo) acc.copy(alpha = 0.8f) else Color.Transparent, tween(400), label = "borda")
    var menu by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(22.dp)

    Row(
        modifier
            .scale(escala)
            .fillMaxWidth()
            .vidro(p, forma, forte = ativo)
            .border(1.5.dp, borda, forma)
            .clickable(interactionSource = toque, indication = null, onClick = aoTocar)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Selo(c, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.nome, color = p.texto, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dataTexto(c), color = p.suave, fontSize = 12.sp, maxLines = 1)
            progresso(c, hoje)?.takeIf { dias > 0 }?.let {
                Spacer(Modifier.height(4.dp))
                Barra(it, acc, 3.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 48.dp)) {
            Text(if (dias == 0L) "Hoje" else numero(n), color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1)
            Text(
                when { dias > 1 -> "faltam"; dias == 1L -> "falta"; dias == 0L -> ""; n == 1L -> "dia atrás"; else -> "dias atrás" },
                color = p.suave, fontSize = 11.sp, maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opções", tint = p.suave) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.cartao) {
                DropdownMenuItem(text = { Text("Editar", color = p.texto) }, leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = p.texto) }, onClick = { menu = false; aoEditar() })
                DropdownMenuItem(text = { Text("Compartilhar", color = p.texto) }, leadingIcon = { Icon(Icons.Rounded.Share, null, tint = p.texto) }, onClick = { menu = false; aoCompartilhar() })
                DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE5484D)) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFE5484D)) }, onClick = { menu = false; aoExcluir() })
            }
        }
    }
}

@Composable
fun Aparencia(
    cfg: Config, mudar: (Config) -> Unit, atualizacao: EstadoAtualizacao,
    aoVerificar: () -> Unit, aoInstalar: (Atualizador.Versao) -> Unit, aoVoltar: () -> Unit,
) {
    val p = LocalPaleta.current
    val ctx = LocalContext.current
    val acc = Color(CORES[cfg.cor.coerceIn(CORES.indices)])
    val margens = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = margens.calculateTopPadding() + 12.dp, bottom = margens.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotaoVidro(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", aoVoltar)
                Text("Aparência", color = p.texto, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
        }
        item {
            Secao("Tema") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("escuro" to "Escuro", "claro" to "Claro", "auto" to "Automático").forEach { (id, nome) ->
                        val sel = cfg.tema == id
                        Column(
                            Modifier.weight(1f).vidro(p, RoundedCornerShape(20.dp)).border(2.dp, if (sel) acc else Color.Transparent, RoundedCornerShape(20.dp))
                                .clickable { mudar(cfg.copy(tema = id)) }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val mini = when (id) {
                                "escuro" -> Brush.verticalGradient(listOf(Color(0xFF16121F), Color(0xFF16121F)))
                                "claro" -> Brush.verticalGradient(listOf(Color(0xFFEEF0F7), Color(0xFFEEF0F7)))
                                else -> Brush.horizontalGradient(0f to Color(0xFFEEF0F7), 0.5f to Color(0xFFEEF0F7), 0.5f to Color(0xFF16121F), 1f to Color(0xFF16121F))
                            }
                            Column(
                                Modifier.size(56.dp, 80.dp).clip(RoundedCornerShape(12.dp)).background(mini).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55888899)))
                                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55888899)))
                                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55888899)))
                            }
                            Text(nome, color = p.texto, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item {
            Secao("Cor de destaque") {
                Row(Modifier.fillMaxWidth().vidro(p, RoundedCornerShape(22.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    CORES.forEachIndexed { i, argb ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(argb))
                                .border(3.dp, if (i == cfg.cor) p.texto else Color.Transparent, CircleShape)
                                .clickable { mudar(cfg.copy(cor = i)) }
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().vidro(p, RoundedCornerShape(22.dp))) {
                Chave("Bolhas animadas", "Manchas de cor se movendo no fundo", cfg.bolhas, acc) { mudar(cfg.copy(bolhas = it)) }
                Chave("Fundo por horário", "Manhã, tarde e noite mudam as cores", cfg.horario, acc) { mudar(cfg.copy(horario = it)) }
                Chave("Cor de cada contador", "O destaque usa a cor escolhida no contador", cfg.porContador, acc, ultima = true) { mudar(cfg.copy(porContador = it)) }
            }
        }
        item {
            Secao("Atualizações") {
                Column(Modifier.fillMaxWidth().vidro(p, RoundedCornerShape(22.dp))) {
                    Chave("Atualizar automaticamente", "Baixa e instala versões novas do GitHub", cfg.autoAtualizar, acc) { mudar(cfg.copy(autoAtualizar = it)) }
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Versão instalada: ${Atualizador.nomeAtual(ctx)}", color = p.suave, fontSize = 13.sp)
                        when (atualizacao) {
                            is EstadoAtualizacao.EmDia -> Text("Você está na versão mais nova.", color = p.texto, fontSize = 13.sp)
                            is EstadoAtualizacao.Erro -> Text(atualizacao.msg, color = Color(0xFFE5484D), fontSize = 13.sp)
                            else -> {}
                        }
                        if (atualizacao is EstadoAtualizacao.Disponivel || atualizacao is EstadoAtualizacao.Baixando) BannerAtualizacao(atualizacao, acc, aoInstalar)
                        if (!Atualizador.podeInstalar(ctx)) {
                            Text("Para instalar sozinho, permita que o Contador instale apps.", color = p.suave, fontSize = 13.sp)
                            OutlinedButton(onClick = {
                                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                            }) { Text("Permitir instalação", color = p.texto) }
                        }
                        Button(onClick = aoVerificar, colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color(0xFF160C26))) {
                            Text("Verificar agora", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    val p = LocalPaleta.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(titulo, color = p.suave, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        conteudo()
    }
}

@Composable
private fun Chave(titulo: String, desc: String, ligado: Boolean, acc: Color, ultima: Boolean = false, aoMudar: (Boolean) -> Unit) {
    val p = LocalPaleta.current
    Row(
        Modifier.fillMaxWidth().clickable { aoMudar(!ligado) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, color = p.texto, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = p.suave, fontSize = 12.sp)
        }
        Switch(
            checked = ligado, onCheckedChange = aoMudar,
            colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color.White, uncheckedTrackColor = p.trilho, uncheckedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White),
        )
    }
    if (!ultima) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(p.trilho))
}
