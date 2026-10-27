package com.dmwnezes.contador

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Cria (inicial = null) ou edita um contador. Mantém id e data de criação ao editar. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditarDialogo(inicial: Contador?, aoFechar: () -> Unit, aoSalvar: (Contador) -> Unit) {
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var data by remember { mutableStateOf(inicial?.data) }
    var hora by remember { mutableStateOf(inicial?.hora) }
    var corIdx by remember { mutableIntStateOf(inicial?.cor ?: 0) }
    var ic by remember { mutableStateOf(inicial?.icone ?: "evento") }
    var escolhendoData by remember { mutableStateOf(false) }
    var escolhendoHora by remember { mutableStateOf(false) }
    val acc = Color(CORES[corIdx])
    val p = LocalPaleta.current

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = p.cartao,
        title = { Text(if (inicial == null) "Novo contador" else "Editar contador", color = p.texto) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it.take(60) },
                    label = { Text("Nome") }, placeholder = { Text("Ex.: aniversário, viagem") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { escolhendoData = true }, modifier = Modifier.weight(1.4f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(18.dp), tint = acc)
                        Spacer(Modifier.width(6.dp))
                        Text(data?.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Data", color = p.texto, maxLines = 1)
                    }
                    OutlinedButton(onClick = { escolhendoHora = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(18.dp), tint = acc)
                        Spacer(Modifier.width(6.dp))
                        Text(hora?.format(fmtHora) ?: "Hora", color = p.texto, maxLines = 1)
                    }
                }
                if (hora != null) {
                    TextButton(onClick = { hora = null }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp), tint = p.suave)
                        Spacer(Modifier.width(4.dp))
                        Text("Tirar horário", color = p.suave, fontSize = 13.sp)
                    }
                }

                Text("Cor", color = p.suave, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CORES.forEachIndexed { i, argb ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(3.dp, if (i == corIdx) p.texto else Color.Transparent, CircleShape)
                                .clickable { corIdx = i }
                        )
                    }
                }

                Text("Ícone", color = p.suave, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ICONES.forEach { chave ->
                        val sel = chave == ic
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (sel) acc.copy(alpha = 0.25f) else p.texto.copy(alpha = 0.06f))
                                .border(1.5.dp, if (sel) acc else Color.Transparent, CircleShape)
                                .clickable { ic = chave },
                            contentAlignment = Alignment.Center,
                        ) { Icon(icone(chave), chave, tint = if (sel) acc else p.suave, modifier = Modifier.size(20.dp)) }
                    }
                }
                Text("Data no futuro conta quanto falta. Data no passado conta quantos dias já se passaram.", color = p.suave, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank() && data != null, onClick = {
                aoSalvar(
                    Contador(
                        id = inicial?.id ?: System.currentTimeMillis().toString(),
                        nome = nome.trim(), data = data!!, hora = hora,
                        criado = inicial?.criado ?: LocalDate.now(), cor = corIdx, icone = ic,
                    )
                )
            }) { Text("Salvar", color = acc) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar", color = p.suave) } },
    )

    if (escolhendoData) {
        // O DatePicker trabalha com milissegundos em UTC.
        val ini = (data ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val st = rememberDatePickerState(initialSelectedDateMillis = ini, yearRange = 1900..2200)
        DatePickerDialog(
            onDismissRequest = { escolhendoData = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { data = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    escolhendoData = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { escolhendoData = false }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }

    if (escolhendoHora) {
        val st = rememberTimePickerState(hora?.hour ?: 12, hora?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { escolhendoHora = false },
            containerColor = p.cartao,
            title = { Text("Horário", color = p.texto) },
            text = { TimePicker(state = st) },
            confirmButton = { TextButton(onClick = { hora = LocalTime.of(st.hour, st.minute); escolhendoHora = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { escolhendoHora = false }) { Text("Cancelar") } },
        )
    }
}
