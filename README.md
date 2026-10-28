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

- **Résumé en haut de l'écran** : ce qui déclenchera une alerte, en clair.
- **Raccourcis d'un geste** : « Tout activer », « Tout couper », « Appels seulement »,
  « Messages seulement », et **pause** 1 h / 3 h / jusqu'à demain 8 h (reprise automatique).
- **Par type** : « Tous les appels » et « Tous les messages » se coupent d'un coup, sans
  perdre les choix faits application par application.
- **Par application** : Téléphone (appels), Google Messages (messages), Teams (appels et
  messages), plus toute application ajoutée (WhatsApp, Outlook, Signal...), chacune avec
  ses interrupteurs « Appels » / « Messages ». Boutons « Tout cocher » / « Tout décocher ».
- **3 tuiles dans les réglages rapides** (volet du haut) :
  - « Alerte son » : tout activer / tout couper (et lever une pause) ;
  - « Alerte appels » : activer / couper les appels ;
  - « Alerte messages » : activer / couper les messages.
  Un appui long sur une tuile ouvre l'application.
- **Filtre des personnes** :
  - « Tout le monde, sauf la liste » (ex. : pas d'alerte pour X et Y) ou
    « Uniquement la liste » ;
  - numéros masqués / privés : pas d'alerte par défaut (réglable) ;
  - ajout depuis les contacts du téléphone (numéro) ou par nom (pour Teams).
- **Sons au choix** pour les messages et les appels, boutons de test.
- **Journal** des derniers événements (alerte jouée, ignorée car son actif, filtrée...).

Une alerte est jouée seulement si **tout** est d'accord : application activée et pas en
pause, type activé, application cochée pour ce type, personne autorisée, et téléphone muet.

Pour un appel, l'alerte sonne en boucle jusqu'à ce que vous décrochiez, refusiez ou que
l'appel s'arrête (60 s maximum). Couper l'application ou le type pendant une alerte
l'arrête immédiatement.

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
4. Acceptez l'ajout de la tuile « Alerte son », et ajoutez si vous le souhaitez
   « Alerte appels » et « Alerte messages » via le crayon du volet des réglages rapides.
5. Vérifiez que le **volume « Alarme »** n'est pas à 0 : c'est lui qui règle le volume
   des alertes.

## Limites connues

- En mode **Ne pas déranger**, si un contact est autorisé à passer (favoris...), le
  téléphone peut sonner *et* l'application alerter : ajoutez ce contact à la liste
  d'exclusion si besoin.
- Si « Ne pas déranger » bloque aussi les alarmes, les alertes seront muettes.
- La détection des appels Teams / WhatsApp / autres repose sur la notification d'appel
  entrant de l'application ; si elle ne fonctionne pas, le journal aide à comprendre pourquoi.
- Pour une application ajoutée, toute notification « normale » compte comme un message
  (décochez « Messages » pour cette application si cela fait trop d'alertes).
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
