package com.dmwnezes.contador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val Noite = Color(0xFF160C26)
val Crepusculo = Color(0xFF2A1440)
val Cartao = Color(0xFF22123A)
val Borda = Color(0xFF3A2559)
val Suave = Color(0xFFB9A6CF)
val Rosa = Color(0xFFFF5D8F)
val Ambar = Color(0xFFFFB34D)
val Turquesa = Color(0xFF3EE0CF)

private val fmtData = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

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

    val destaque = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()
    val lista = ordenar(estado.contadores, hoje)

    Scaffold(
        containerColor = Noite,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { criando = true },
                containerColor = Rosa, contentColor = Noite,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Novo contador", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("CONTADOR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
            }
            item {
                if (destaque != null) Destaque(destaque, agora) else Vazio()
                Spacer(Modifier.height(14.dp))
            }
            if (lista.isNotEmpty()) item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("MEUS CONTADORES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text("Toque para destacar", color = Suave, fontSize = 12.sp)
                }
            }
            items(lista, key = { it.id }) { c ->
                Linha(
                    c, hoje, ativo = c.id == destaque?.id,
                    aoTocar = { mudar(estado.copy(destaque = c.id)) },
                    aoExcluir = { excluindo = c },
                )
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
    val passado = dias < 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Crepusculo, Color(0xFF3B1550), Color(0xFF6A2058))))
            .padding(22.dp)
    ) {
        Text(
            when { dias > 0 -> "FALTAM"; dias == 0L -> "É HOJE"; else -> "JÁ SE PASSARAM" },
            color = if (passado) Turquesa else Ambar, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 2.sp,
        )
        Text(c.nome.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 32.sp)
        Text(c.data.format(fmtData), color = Suave, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        if (dias == 0L) {
            Text("HOJE", color = Rosa, fontWeight = FontWeight.Black, fontSize = 96.sp, lineHeight = 96.sp)
        } else {
            val n = kotlin.math.abs(dias)
            Text("%,d".format(Locale("pt", "BR"), n), color = if (passado) Turquesa else Ambar, fontWeight = FontWeight.Black, fontSize = 112.sp, lineHeight = 112.sp)
            Text(if (n == 1L) "DIA" else "DIAS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 2.sp)
        }
        if (dias > 0) {
            // Até 0h do dia do evento (horário exato nem sempre é conhecido).
            val s = ChronoUnit.SECONDS.between(agora, c.data.atStartOfDay()).coerceAtLeast(0)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bloco(s / 86400, "dias")
                Bloco(s % 86400 / 3600, "horas")
                Bloco(s % 3600 / 60, "min")
                Bloco(s % 60, "seg")
            }
        }
    }
}

@Composable
fun Bloco(v: Long, rotulo: String) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Noite.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("%02d".format(v), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(rotulo.uppercase(), color = Suave, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun Vazio() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Cartao).padding(28.dp),
    ) {
        Text("Nenhum contador ainda", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Toque em \"Novo contador\" para criar o primeiro.", color = Suave)
    }
}

@Composable
fun Linha(c: Contador, hoje: LocalDate, ativo: Boolean, aoTocar: () -> Unit, aoExcluir: () -> Unit) {
    val dias = diasAte(c.data, hoje)
    val n = kotlin.math.abs(dias)
    val cor = when { dias > 0 -> Ambar; dias == 0L -> Rosa; else -> Turquesa }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Cartao)
            .border(1.dp, if (ativo) Rosa else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = aoTocar)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(Locale("pt", "BR"), n), color = cor, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text(
                when { dias > 1 -> "FALTAM"; dias == 1L -> "FALTA"; dias == 0L -> "HOJE"; n == 1L -> "DIA DESDE"; else -> "DIAS DESDE" },
                color = Suave, fontSize = 10.sp, letterSpacing = 1.sp,
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
