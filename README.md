# BoursoSignal

Application mobile Android **personnelle** d'aide à la décision boursière : elle
surveille une liste de valeurs, détecte des signaux d'achat/vente à partir
d'indicateurs techniques et prévient par notification. Les ordres restent passés
**manuellement** dans Boursorama (phase 1), conformément au cahier des charges.

> ⚠️ Outil personnel. Les signaux reposent sur des indicateurs techniques et ne
> constituent pas un conseil en investissement. Investir comporte un risque de
> perte en capital. Boursorama ne fournissant pas d'API publique de passage
> d'ordres, l'exécution reste manuelle et le portefeuille est saisi à la main.

## État du projet (phase 1 — MVP)

Ce dépôt est l'échafaudage de la phase 1 : architecture complète, moteur de
signaux implémenté et **testé**, couche données, UI Compose, tâche de fond et
notifications.

### Ce qui est vérifié

Le **moteur de signaux** (le cœur de l'app) est en Kotlin pur et a été **compilé
et exécuté** : 25/25 tests passent (indicateurs SMA, EMA, RSI, MACD, Bollinger ;
règles de croisement ; scoring ; stop-loss / take-profit).

Deux façons de lancer les tests :

- **Dans Android Studio** : les tests JUnit sont dans
  `app/src/test/java/bzh/dargio/boursosignal/domain/signals/`
  (`IndicatorsTest`, `SignalEngineTest`). Lancez-les via la gouttière ou
  `./gradlew :app:testDebugUnitTest`.
- **En ligne de commande, sans SDK Android** : un petit projet autonome
  `engine-verify/` compile les mêmes sources et exécute un harnais équivalent.

> Note : l'application Android (UI, Room, WorkManager) n'a pas pu être compilée
> dans l'environnement de génération (pas de SDK Android ni d'accès Maven). Le
> code suit les conventions AGP/Compose courantes ; prévoyez d'éventuels ajustements
> mineurs au premier build dans Android Studio. Le moteur, lui, est compilé et testé.

## Ouvrir et lancer

1. Ouvrir le dossier `boursosignal/` dans **Android Studio** (Ladybug ou plus récent).
2. Laisser Gradle synchroniser (télécharge AGP 8.7, Kotlin 2.0, Compose, Room…).
3. Lancer sur un appareil/émulateur **Android 10+ (API 29+)**.

Au premier lancement, l'app démarre en **mode démo** (cours synthétiques, sans
clé ni réseau) pour être utilisable immédiatement. Ajoutez des valeurs dans
l'onglet *Valeurs* (ex. `AIR.PA`, `BNP.PA`).

### Passer en données réelles

1. Créer une clé API gratuite chez un fournisseur de cours (par défaut
   [Twelve Data](https://twelvedata.com/)).
2. Dans *Réglages* : désactiver le mode démo, coller la clé, enregistrer.

La clé est stockée chiffrée (EncryptedSharedPreferences + Android Keystore) et ne
quitte jamais l'appareil.

## Architecture

Architecture en couches (MVVM / clean), source de cours abstraite derrière une
interface pour pouvoir changer de fournisseur sans toucher au reste.

```
app/src/main/java/bzh/dargio/boursosignal/
├── domain/
│   ├── signals/            Moteur pur et testable
│   │   ├── Candle.kt           Bougie OHLC + extensions
│   │   ├── Indicators.kt       SMA, EMA, RSI, MACD, Bollinger
│   │   ├── Strategy.kt         StrategyConfig, SignalResult, SignalType
│   │   └── SignalEngine.kt     Règles + scoring + gestion du risque
│   └── SignalRefresher.kt  Orchestration : cours → moteur → journal
├── data/
│   ├── local/              Room : entités, DAO, base, mappers
│   ├── remote/             MarketDataProvider + TwelveData + démo
│   ├── prefs/              SettingsStore (clé API chiffrée, réglages)
│   └── repo/               Repositories (watchlist, marché, portefeuille, signaux, stratégie)
├── work/                   WorkManager (rafraîchissement) + notifications
├── di/                     AppContainer (injection manuelle)
├── ui/                     Compose : thème, navigation, écrans, graphique
└── BoursoSignalApp / MainActivity
```

### Correspondance avec le cahier des charges

| Cahier | Implémentation |
| --- | --- |
| §5 Moteur de signaux | `domain/signals/` (testé) |
| §6 Sources de données | `data/remote/` (interface + Twelve Data + démo) |
| §7 Architecture | couches domain / data / ui, WorkManager, Room, Keystore |
| §8 Modèle de données | `data/local/Entities.kt` |
| §9 Écrans | `ui/screens/` (dashboard, watchlist, détail, portefeuille, alertes, réglages) |
| §10 Notifications | `work/Notifications.kt` + `RefreshWorker` |
| §11 Sécurité | `SettingsStore` (EncryptedSharedPreferences), `data_extraction_rules` |
| §12 Avertissements | écran Réglages |

## Reste à faire (prochaines itérations)

- Chiffrement de la base Room (SQLCipher) avec clé du Keystore.
- Configurateur de stratégie complet (écran d'édition des paramètres §F8).
- Backtesting (§F10) et tableau de bord enrichi.
- Verrouillage biométrique à l'ouverture.
- Icône d'app définitive et finitions UI.

## Phasage

- **Phase 1** (ce dépôt) : alertes + suivi manuel.
- **Phase 2** : stratégies avancées + backtesting.
- **Phase 3** : étude de l'exécution automatique (faisabilité + risques, cf. §2/§14 du cahier des charges).

## Build & déploiement (GitHub Actions)

Le workflow `.github/workflows/android.yml` compile automatiquement :

- l'**APK debug** (installable directement) ;
- l'**APK release** et l'**AAB release** (l'AAB est le format attendu par le Play Store).

Déclenchement :

- à chaque `push` sur `main` ou `pull_request` → build + tests, artefacts téléchargeables depuis l'onglet **Actions** (section *Artifacts* du run) ;
- sur un **tag** `vX.Y.Z` → une **Release** GitHub est créée avec l'APK et l'AAB attachés.

### Produire une Release avec les binaires

```bash
git tag v0.1.0
git push origin v0.1.0
```

### Signer la release (pour le Play Store)

Par défaut l'APK/AAB release est **non signé**. Pour le signer, ajoutez dans
*Settings → Secrets and variables → Actions* du dépôt :

- `KEYSTORE_BASE64` : le keystore `.jks` encodé en base64 ;
- `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

En local, copiez `keystore.properties.example` en `keystore.properties` (ignoré par git).
