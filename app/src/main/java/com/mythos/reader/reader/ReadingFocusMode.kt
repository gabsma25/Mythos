package com.mythos.reader.reader

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import com.mythos.reader.MainActivity
import com.mythos.reader.R

/**
 * Modo "Leitura Mythos" do sistema (Configurações › Modos), ligado enquanto um livro está
 * aberto. O app só controla o próprio modo; o Não Perturbe do usuário nunca é alterado,
 * e as exceções podem ser ajustadas nas configurações do Android.
 */
class ReadingFocusMode(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("reading_focus_mode", Context.MODE_PRIVATE)
    private val conditionId = Uri.parse("condition://com.mythos.reader/reading")

    val hasPermission: Boolean
        get() = notificationManager.isNotificationPolicyAccessGranted

    /** Tela do sistema onde o usuário concede o acesso ao Não Perturbe. */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun activate() = setActive(true)

    fun deactivate() = setActive(false)

    private fun setActive(active: Boolean) {
        if (!hasPermission) return
        // Nunca deixa uma falha do sistema derrubar o leitor.
        runCatching {
            val id = if (active) ensureRule() else existingRuleId()
            id?.let { notificationManager.setAutomaticZenRuleState(it, condition(active)) }
        }
    }

    private fun existingRuleId(): String? =
        prefs.getString(KEY_RULE_ID, null)?.takeIf { it in notificationManager.automaticZenRules }

    /** Cria o modo na primeira vez, ou de novo se o usuário o apagou nas configurações. */
    private fun ensureRule(): String? {
        existingRuleId()?.let { id ->
            // Só reaplica a política quando o app muda os padrões; fora isso, as exceções
            // ajustadas pelo usuário em Configurações › Modos são preservadas.
            if (prefs.getInt(KEY_POLICY_VERSION, 0) < POLICY_VERSION) {
                notificationManager.updateAutomaticZenRule(id, buildRule())
                prefs.edit().putInt(KEY_POLICY_VERSION, POLICY_VERSION).apply()
            }
            return id
        }
        val id = notificationManager.addAutomaticZenRule(buildRule()) ?: return null
        prefs.edit().putString(KEY_RULE_ID, id).putInt(KEY_POLICY_VERSION, POLICY_VERSION).apply()
        return id
    }

    private fun buildRule(): AutomaticZenRule {
        // Silencia sons e alertas flutuantes; alarmes e chamadas repetidas continuam passando.
        // Mídia continua liberada: música de fundo e a futura leitura em voz alta (TTS).
        val policy = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowMedia(true)
            .allowAlarms(true)
            .allowRepeatCallers(true)
            .hideAllVisualEffects()
            .build()

        return AutomaticZenRule.Builder(RULE_NAME, conditionId)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(policy)
            .setManualInvocationAllowed(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    setType(AutomaticZenRule.TYPE_IMMERSIVE)
                    setIconResId(R.drawable.ic_reading_mode)
                    setTriggerDescription("Enquanto um livro estiver aberto no Mythos")
                }
            }
            .build()
    }

    private fun condition(active: Boolean): Condition {
        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        val summary = if (active) "Lendo" else "Fora da leitura"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // SOURCE_CONTEXT: o modo segue o que o usuário está fazendo no app.
            Condition(conditionId, summary, state, Condition.SOURCE_CONTEXT)
        } else {
            Condition(conditionId, summary, state)
        }
    }

    private companion object {
        const val KEY_RULE_ID = "ruleId"
        const val KEY_POLICY_VERSION = "policyVersion"
        /** Incrementar sempre que [buildRule] mudar. 2: mídia liberada. */
        const val POLICY_VERSION = 2
        const val RULE_NAME = "Leitura Mythos"
    }
}
