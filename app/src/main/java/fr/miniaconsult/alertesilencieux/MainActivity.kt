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
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView

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

        bindSwitch(R.id.switchEnabled, prefs.enabled) { prefs.enabled = it }
        bindSwitch(R.id.switchPhoneCalls, prefs.phoneCalls) { prefs.phoneCalls = it }
        bindSwitch(R.id.switchSms, prefs.smsMessages) { prefs.smsMessages = it }
        bindSwitch(R.id.switchTeamsMessages, prefs.teamsMessages) { prefs.teamsMessages = it }
        bindSwitch(R.id.switchTeamsCalls, prefs.teamsCalls) { prefs.teamsCalls = it }
        bindSwitch(R.id.switchPrivate, prefs.alertPrivateNumbers) { prefs.alertPrivateNumbers = it }

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
        button(R.id.btnTestMessage) { testPlayer.play(AlertPlayer.Kind.MESSAGE, prefs.messageSound, 15_000) }
        button(R.id.btnTestCall) { testPlayer.play(AlertPlayer.Kind.PHONE_CALL, prefs.callSound, 10_000) }
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
        findViewById<CompoundButton>(R.id.switchEnabled).isChecked = prefs.enabled

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
        status(R.id.statusAlarm, alarm > 0, "Volume des alertes = volume « Alarme » ($alarm)",
            "Le volume « Alarme » est à 0 : les alertes seront muettes")

        findViewById<Button>(R.id.btnMessageSound).text = "Son des messages : ${soundName(prefs.messageSound, true)}"
        findViewById<Button>(R.id.btnCallSound).text = "Son des appels : ${soundName(prefs.callSound, false)}"

        refreshPeople()
        val log = EventLog.read(this)
        findViewById<TextView>(R.id.logText).text = if (log.isEmpty()) "(vide)" else log.joinToString("\n")
    }

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

    private fun bindSwitch(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<CompoundButton>(id).apply {
            isChecked = value
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
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
