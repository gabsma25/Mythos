package com.mythos.reader

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Porta de entrada invisível para tudo que chega de outros apps: EPUBs abertos ou
 * compartilhados (WhatsApp, gerenciador de arquivos) e o atalho do modo "Leitura Mythos"
 * em Configurações › Modos.
 *
 * Sem ela, o Android criaria uma biblioteca inteira dentro da tarefa do app de origem,
 * e o Mythos passaria a ter duas cópias abertas (e o dobro de memória). Aqui o pedido é
 * repassado à biblioteca na tarefa do próprio Mythos, e esta tela se fecha na hora.
 */
class ExternalEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forward = Intent(intent)
            .setClass(this, MainActivity::class.java)
            // NEW_TASK leva à tarefa do Mythos; CLEAR_TOP + SINGLE_TOP reaproveitam a biblioteca
            // aberta em vez de empilhar outra. A permissão de leitura do arquivo é repassada.
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        startActivity(forward)
        finish()
    }
}
