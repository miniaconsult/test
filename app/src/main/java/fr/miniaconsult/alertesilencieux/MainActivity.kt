package fr.miniaconsult.alertesilencieux

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var testPlayer: AlertPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        testPlayer = AlertPlayer(this)

        // Android 15 affiche l'appli en plein écran : on évite la barre d'état et la barre de navigation.
        findViewById<android.view.View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindSwitch(R.id.switchEnabled) { change { prefs.enabled = it; if (it) prefs.pausedUntil = 0 } }
        bindSwitch(R.id.switchCalls) { change { prefs.callsEnabled = it } }
        bindSwitch(R.id.switchMessages) { change { prefs.messagesEnabled = it } }
        bindSwitch(R.id.switchPrivate) { prefs.alertPrivateNumbers = it }
        bindSwitch(R.id.switchCustomVolume) { change { prefs.customVolume = it } }
        bindSwitch(R.id.switchCallVibrate) { prefs.callVibrate = it }
        bindSwitch(R.id.switchMessageVibrate) { prefs.messageVibrate = it }
        bindVolume(R.id.seekCallVolume, R.id.labelCallVolume, "Volume des appels") { prefs.callVolume = it }
        bindVolume(R.id.seekMessageVolume, R.id.labelMessageVolume, "Volume des messages") { prefs.messageVolume = it }

        // Raccourcis
        button(R.id.btnAllOn) {
            change {
                prefs.enabled = true
                prefs.pausedUntil = 0
                prefs.callsEnabled = true
                prefs.messagesEnabled = true
                prefs.setAllApps(null, true)
            }
        }
        button(R.id.btnAllOff) { change { prefs.enabled = false } }
        button(R.id.btnCallsOnly) { change { preset(calls = true, messages = false) } }
        button(R.id.btnMessagesOnly) { change { preset(calls = false, messages = true) } }
        button(R.id.btnPause1h) { pauseFor(60) }
        button(R.id.btnPause3h) { pauseFor(180) }
        button(R.id.btnPauseTomorrow) {
            val tomorrow = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            pauseUntil(tomorrow.timeInMillis)
        }
        button(R.id.btnResume) { change { prefs.pausedUntil = 0; prefs.enabled = true } }
        button(R.id.btnAppsAllOn) { change { prefs.setAllApps(null, true) } }
        button(R.id.btnAppsAllOff) { change { prefs.setAllApps(null, false) } }
        button(R.id.btnAddApp) { pickApp() }

        findViewById<RadioGroup>(R.id.radioMode).apply {
            check(if (prefs.onlyListed) R.id.radioOnly else R.id.radioExclude)
            setOnCheckedChangeListener { _, id -> prefs.onlyListed = id == R.id.radioOnly }
        }

        button(R.id.btnListener) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button(R.id.btnPhone) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG), REQ_PHONE)
        }
        button(R.id.btnBattery) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
        button(R.id.btnAddContact) {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT
            )
        }
        button(R.id.btnAddName) { askName() }
        button(R.id.btnMessageSound) { pickSound(RingtoneManager.TYPE_NOTIFICATION, prefs.messageSound, REQ_MESSAGE_SOUND) }
        button(R.id.btnCallSound) { pickSound(RingtoneManager.TYPE_RINGTONE, prefs.callSound, REQ_CALL_SOUND) }
        button(R.id.btnTestMessage) { testPlayer.play(AlertPlayer.Kind.MESSAGE, 15_000) }
        button(R.id.btnTestCall) { testPlayer.play(AlertPlayer.Kind.PHONE_CALL, 10_000) }
        button(R.id.btnStop) { testPlayer.stop() }
        button(R.id.btnClearLog) {
            EventLog.clear(this)
            refresh()
        }

        offerTile()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        testPlayer.stop()
    }

    private fun refresh() {
        setChecked(R.id.switchEnabled, prefs.enabled)
        setChecked(R.id.switchCalls, prefs.callsEnabled)
        setChecked(R.id.switchMessages, prefs.messagesEnabled)
        setChecked(R.id.switchPrivate, prefs.alertPrivateNumbers)
        setChecked(R.id.switchCustomVolume, prefs.customVolume)
        setChecked(R.id.switchCallVibrate, prefs.callVibrate)
        setChecked(R.id.switchMessageVibrate, prefs.messageVibrate)
        setVolume(R.id.seekCallVolume, R.id.labelCallVolume, "Volume des appels", prefs.callVolume)
        setVolume(R.id.seekMessageVolume, R.id.labelMessageVolume, "Volume des messages", prefs.messageVolume)
        findViewById<TextView>(R.id.statusSummary).apply {
            text = summary()
            setBackgroundColor(if (prefs.isActive()) 0x3322AA55 else 0x33DD4444)
        }
        findViewById<Button>(R.id.btnResume).visibility = if (prefs.isPaused()) Button.VISIBLE else Button.GONE
        refreshApps()

        val listenerOk = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(this, AlertListenerService::class.java).flattenToString()) == true
        status(R.id.statusListener, listenerOk, "Accès aux notifications accordé",
            "Accès aux notifications manquant (indispensable). S'il est grisé : Infos de l'appli → ⋮ → « Autoriser les paramètres restreints ».")
        findViewById<Button>(R.id.btnListener).visibility = if (listenerOk) Button.GONE else Button.VISIBLE

        val phoneOk = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        status(R.id.statusPhone, phoneOk, "Appels : autorisations accordées",
            "Appels : autorisation manquante (nécessaire pour connaître le numéro qui appelle)")
        findViewById<Button>(R.id.btnPhone).visibility = if (phoneOk) Button.GONE else Button.VISIBLE

        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        status(R.id.statusBattery, batteryOk, "Batterie : non restreinte",
            "Batterie : optimisée (OxygenOS peut arrêter l'appli en arrière-plan)")
        findViewById<Button>(R.id.btnBattery).visibility = if (batteryOk) Button.GONE else Button.VISIBLE

        val alarm = SoundState.alarmVolume(this)
        if (prefs.customVolume) {
            status(R.id.statusAlarm, true, "Volume des alertes : réglé dans « Sons » (le volume Alarme est remis comme avant après chaque alerte)", "")
        } else {
            status(R.id.statusAlarm, alarm > 0, "Volume des alertes = volume « Alarme » actuel ($alarm)",
                "Le volume « Alarme » est à 0 : les alertes seront muettes")
        }

        findViewById<Button>(R.id.btnMessageSound).text = "Son des messages : ${soundName(prefs.messageSound, true)}"
        findViewById<Button>(R.id.btnCallSound).text = "Son des appels : ${soundName(prefs.callSound, false)}"

        refreshPeople()
        val log = EventLog.read(this)
        findViewById<TextView>(R.id.logText).text = if (log.isEmpty()) "(vide)" else log.joinToString("\n")
    }

    /** Résumé lisible de l'état actuel, en haut de l'écran. */
    private fun summary(): String {
        if (!prefs.enabled) return "🔕 Tout est coupé : aucune alerte."
        if (prefs.isPaused()) return "⏸ En pause jusqu'à ${Tiles.time(prefs.pausedUntil)} : aucune alerte."
        val apps = prefs.apps
        fun names(type: AlertType) = apps.filter { prefs.allows(type, it.pkg) }.joinToString(", ") { it.label }
        val calls = names(AlertType.CALL).ifEmpty { "aucun" }
        val messages = names(AlertType.MESSAGE).ifEmpty { "aucun" }
        return "🔔 Alerte si le téléphone est muet.\n📞 Appels : $calls\n💬 Messages : $messages"
    }

    private fun preset(calls: Boolean, messages: Boolean) {
        prefs.enabled = true
        prefs.pausedUntil = 0
        prefs.callsEnabled = calls
        prefs.messagesEnabled = messages
    }

    private fun pauseFor(minutes: Int) = pauseUntil(System.currentTimeMillis() + minutes * 60_000L)

    private fun pauseUntil(time: Long) = change {
        prefs.enabled = true
        prefs.pausedUntil = time
        EventLog.add(this, "Pause jusqu'à ${Tiles.time(time)}")
    }

    /** Applique un changement de réglage puis met à jour l'écran et les tuiles. */
    private fun change(block: () -> Unit) {
        block()
        Tiles.refreshAll(this)
        refresh()
    }

    private fun refreshApps() {
        val container = findViewById<LinearLayout>(R.id.appsList)
        container.removeAllViews()
        prefs.apps.forEach { app -> container.addView(appCard(app)) }
    }

    private fun appCard(app: WatchedApp): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = app.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            compoundDrawablePadding = dp(12)
            appIcon(app.pkg)?.let { setCompoundDrawablesRelative(it, null, null, null) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (!app.builtIn) {
            header.addView(Button(this).apply {
                text = "Retirer"
                setOnClickListener { change { prefs.removeApp(app.pkg) } }
            })
        }
        card.addView(header)
        app.calls?.let { checked ->
            card.addView(appSwitch("Appels", checked, prefs.callsEnabled) { v -> prefs.updateApp(app.pkg) { it.copy(calls = v) } })
        }
        app.messages?.let { checked ->
            card.addView(appSwitch("Messages", checked, prefs.messagesEnabled) { v -> prefs.updateApp(app.pkg) { it.copy(messages = v) } })
        }
        return card
    }

    /** Interrupteur d'une application ; grisé (mais modifiable) si le type est coupé globalement. */
    private fun appSwitch(label: String, checked: Boolean, typeOn: Boolean, onChange: (Boolean) -> Unit) =
        Switch(this).apply {
            text = if (typeOn) label else "$label (type coupé)"
            isChecked = checked
            alpha = if (typeOn) 1f else 0.5f
            minHeight = dp(48)
            setPaddingRelative(dp(44), 0, 0, 0)
            setOnCheckedChangeListener { _, v -> change { onChange(v) } }
        }

    private fun appIcon(pkg: String) = try {
        packageManager.getApplicationIcon(pkg).apply { setBounds(0, 0, dp(32), dp(32)) }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun pickApp() {
        val watched = prefs.apps.map { it.pkg }.toSet()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .distinctBy { it.first }
            .filter { it.first !in watched && it.first != packageName }
            .sortedBy { it.second.lowercase() }
        AlertDialog.Builder(this)
            .setTitle("Ajouter une application")
            .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                val (pkg, label) = apps[which]
                change { prefs.addApp(pkg, label) }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun refreshPeople() {
        val container = findViewById<LinearLayout>(R.id.peopleList)
        container.removeAllViews()
        val people = prefs.people
        if (people.isEmpty()) {
            container.addView(TextView(this).apply { text = "(liste vide)"; alpha = 0.7f })
        }
        people.forEach { person ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = if (person.numbers.isEmpty()) person.name else "${person.name}\n${person.numbers.joinToString(", ")}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Retirer"
                setOnClickListener {
                    prefs.removePerson(person)
                    refreshPeople()
                }
            })
            container.addView(row)
        }
    }

    private fun askName() {
        val input = EditText(this).apply { hint = "Nom tel qu'il apparaît dans Teams" }
        AlertDialog.Builder(this)
            .setTitle("Ajouter un nom")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    prefs.addPerson(name, null)
                    refreshPeople()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun pickSound(type: Int, current: Uri?, requestCode: Int) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_CONTACT -> data.data?.let { addContact(it) }
            REQ_MESSAGE_SOUND -> prefs.messageSound = pickedSound(data)
            REQ_CALL_SOUND -> prefs.callSound = pickedSound(data)
        }
        refresh()
    }

    private fun pickedSound(data: Intent): Uri? {
        @Suppress("DEPRECATION")
        val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // "Par défaut" = suivre le son choisi dans les réglages du téléphone.
        return if (uri == null || RingtoneManager.isDefault(uri)) null else uri
    }

    private fun addContact(uri: Uri) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0) ?: return
                prefs.addPerson(name, cursor.getString(1))
            }
        }
    }

    private fun soundName(uri: Uri?, message: Boolean): String {
        if (uri == null) return if (message) "notification du téléphone" else "sonnerie du téléphone"
        return RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "personnalisé"
    }

    /** Propose (une seule fois) d'ajouter la tuile dans les réglages rapides. */
    private fun offerTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val sp = getSharedPreferences("ui", MODE_PRIVATE)
        if (sp.getBoolean("tile_offered", false)) return
        sp.edit().putBoolean("tile_offered", true).apply()
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, ToggleTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { }
    }

    private fun status(id: Int, ok: Boolean, okText: String, koText: String) {
        findViewById<TextView>(id).text = if (ok) "✅ $okText" else "⚠️ $koText"
    }

    private fun bindSwitch(id: Int, onChange: (Boolean) -> Unit) {
        findViewById<CompoundButton>(id).apply {
            setOnCheckedChangeListener { view, checked -> if (view.tag == null) onChange(checked) }
        }
    }

    private fun bindVolume(seekId: Int, labelId: Int, label: String, onChange: (Int) -> Unit) {
        findViewById<SeekBar>(seekId).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                findViewById<TextView>(labelId).text = "$label : $value %"
                if (fromUser) onChange(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }

    private fun setVolume(seekId: Int, labelId: Int, label: String, value: Int) {
        val enabled = prefs.customVolume
        findViewById<SeekBar>(seekId).apply {
            progress = value
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
        }
        findViewById<TextView>(labelId).apply {
            text = "$label : $value %"
            alpha = if (enabled) 1f else 0.4f
        }
    }

    /** Met à jour un interrupteur sans déclencher son action. */
    private fun setChecked(id: Int, value: Boolean) {
        findViewById<CompoundButton>(id).apply {
            tag = "refresh"
            isChecked = value
            tag = null
        }
    }

    private fun button(id: Int, action: () -> Unit) {
        findViewById<Button>(id).setOnClickListener { action() }
    }

    companion object {
        private const val REQ_PHONE = 1
        private const val REQ_CONTACT = 2
        private const val REQ_MESSAGE_SOUND = 3
        private const val REQ_CALL_SOUND = 4
    }
}
