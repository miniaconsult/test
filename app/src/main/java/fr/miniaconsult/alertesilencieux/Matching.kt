package fr.miniaconsult.alertesilencieux

import java.text.Normalizer

object Matching {
    fun normalizeName(name: CharSequence): String =
        Normalizer.normalize(name.toString(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Clé de comparaison d'un numéro : les 9 derniers chiffres,
     * pour que "+33 6 12 34 56 78" et "06 12 34 56 78" correspondent.
     */
    fun numberKey(raw: CharSequence?): String? {
        val digits = raw?.filter(Char::isDigit)?.toString() ?: return null
        return if (digits.length < 6) null else digits.takeLast(9)
    }

    fun sameNumber(a: CharSequence?, b: CharSequence?): Boolean {
        val ka = numberKey(a) ?: return false
        return ka == numberKey(b)
    }

    fun matches(person: Person, names: Collection<CharSequence>, numbers: Collection<CharSequence>): Boolean {
        val personName = normalizeName(person.name)
        if (personName.isNotEmpty() && names.any { normalizeName(it) == personName }) return true
        return person.numbers.any { n -> numbers.any { sameNumber(it, n) } }
    }

    /** Décide si l'expéditeur / l'appelant mérite une alerte selon la liste de personnes. */
    fun allows(prefs: Prefs, names: Collection<CharSequence>, numbers: Collection<CharSequence>): Boolean {
        val listed = prefs.people.any { matches(it, names, numbers) }
        return if (prefs.onlyListed) listed else !listed
    }
}
