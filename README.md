# Alerte Silencieux

Application Android (testée à la compilation pour Android 10 à 16, pensée pour un OnePlus 15)
qui **émet un son d'alerte quand le téléphone est en silencieux / vibreur / ne pas déranger**
et que vous recevez :

- un appel téléphonique (Téléphone Google) ;
- un message dans Google Messages (SMS / RCS) ;
- un message Teams ;
- un appel Teams.

Si le son du téléphone est déjà actif, l'application ne fait rien. Elle ne modifie
**jamais** vos réglages de son : l'alerte est jouée sur le canal « Alarme ».

## Fonctions

- **Activer / désactiver en un geste** : tuile « Alerte son » dans les réglages rapides
  (volet du haut). Également un interrupteur en haut de l'application.
- **Filtre des personnes** :
  - « Tout le monde, sauf la liste » (ex. : pas d'alerte pour X et Y) ou
    « Uniquement la liste » ;
  - numéros masqués / privés : pas d'alerte par défaut (réglable) ;
  - ajout depuis les contacts du téléphone (numéro) ou par nom (pour Teams).
- **Sons au choix** pour les messages et les appels, boutons de test.
- **Journal** des derniers événements (alerte jouée, ignorée car son actif, filtrée...).

Pour un appel, l'alerte sonne en boucle jusqu'à ce que vous décrochiez, refusiez ou que
l'appel s'arrête (60 s maximum).

## Installation sur le téléphone

1. Sur le téléphone, ouvrez la page **Releases** du dépôt GitHub et téléchargez
   `AlerteSilencieux.apk` (release « apk-latest », reconstruite à chaque modification).
2. Ouvrez le fichier ; autorisez l'installation depuis le navigateur / gestionnaire de
   fichiers si Android le demande.
3. Ouvrez **Alerte Silencieux** et suivez les lignes ⚠️ :
   - **Accès aux notifications** → activer *Alerte Silencieux*.
     S'il est grisé (« paramètre restreint ») : *Paramètres → Applications →
     Alerte Silencieux → ⋮ (en haut à droite) → Autoriser les paramètres restreints*,
     puis recommencez.
   - **Téléphone et journal d'appels** → autoriser (sert à connaître le numéro qui appelle).
   - **Batterie** → ne pas restreindre. Sur OnePlus, vérifiez aussi
     *Paramètres → Batterie → Optimisation de la batterie* et, si disponible,
     verrouillez l'appli dans les applis récentes.
4. Acceptez l'ajout de la tuile « Alerte son » (ou ajoutez-la via le crayon du volet
   des réglages rapides).
5. Vérifiez que le **volume « Alarme »** n'est pas à 0 : c'est lui qui règle le volume
   des alertes.

## Limites connues

- En mode **Ne pas déranger**, si un contact est autorisé à passer (favoris...), le
  téléphone peut sonner *et* l'application alerter : ajoutez ce contact à la liste
  d'exclusion si besoin.
- Si « Ne pas déranger » bloque aussi les alarmes, les alertes seront muettes.
- La détection des appels Teams repose sur la notification d'appel entrant de Teams ;
  si elle ne fonctionne pas, le journal de l'application aide à comprendre pourquoi.
- Les noms (Teams, Google Messages) doivent correspondre à ceux affichés dans les
  notifications (majuscules et accents ignorés).

## Développement

```
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

L'APK est signé avec `app/signing.keystore` (clé fixe, mot de passe dans
`app/build.gradle.kts`) pour que les mises à jour s'installent par-dessus la version
précédente.
