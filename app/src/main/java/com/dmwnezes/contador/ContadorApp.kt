package com.dmwnezes.contador

import android.app.Application
import android.content.Context
import java.io.File

/** Guarda o último erro que fechou o app, para mostrar na próxima abertura. */
class ContadorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { arquivoDeErro(this).writeText("Versão ${Atualizador.nomeAtual(this)}\n\n" + e.stackTraceToString()) }
            anterior?.uncaughtException(t, e)
        }
    }
}

fun arquivoDeErro(ctx: Context) = File(ctx.filesDir, "ultimo_erro.txt")
