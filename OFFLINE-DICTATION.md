# HeliBoard · Dictée locale — prise en main

Fork direct de **HeliBoard v3.7**. Clavier complet + microphone intégré, modèle **Parakeet TDT v3 multilingue INT8** exécuté localement via **sherpa-onnx**. Pas de clé API ni de compte.

**Statut : prototype.** Le décodage intervient **après l’arrêt du micro**. Pas de vrai streaming, pas de garantie de qualité identique à ChatGPT/Claude, pas de latence garantie sur téléphone. Voir [l’audit et ses sources](docs/voice-audit.md).

## Installer

1. Télécharger l’APK de ce fork (pas celui d’HeliBoard original). Android **8 ou plus**, appareil **ARM64 uniquement**. L’APK de développement est signé avec une clé de debug : ne pas le considérer comme une version de production.
2. Ouvrir **HeliBoard · Dictée locale**. Dans Android → Système → Clavier → Clavier à l’écran, activer ce clavier et le choisir. Le nom du menu varie selon le fabricant. L’avertissement Android sur la possibilité pour un clavier de lire la saisie est normal et important : n’installer qu’un APK de confiance.
3. Choisir **Français / AZERTY** dans les réglages de langues et dispositions d’HeliBoard.
4. Ouvrir **Dictée hors ligne**, autoriser le microphone et toucher **Télécharger / réparer le modèle**. Le premier téléchargement fait **670 Mo** ; prévoir au moins **800 Mo libres**, garder l’écran ouvert. Les fichiers sont privés à l’application et vérifiés par SHA-256.
5. Dans une app de notes, toucher le **micro de la barre d’outils** du clavier. S’il est caché, développer la barre ou l’ajouter via Réglages → Barre d’outils. Attendre **Parlez maintenant**, dicter, puis toucher **Terminer**. Le résultat ponctué est inséré au curseur. **Annuler** abandonne la dictée.
6. Activer le mode avion et recommencer : la dictée doit fonctionner sans réseau une fois le modèle installé.

Gboard reste installé. Il suffit de le sélectionner de nouveau dans le sélecteur de clavier Android pour revenir à la situation initiale. Aucun remplacement des fichiers ni des données Gboard/HeliBoard original : identifiant d’application distinct, `fr.alexalmny.offlinedictation.debug` pour cet APK.

## Confidentialité et protections

- Audio capturé uniquement après activation explicite et affichage de l’état d’enregistrement. Aucun enregistrement en arrière-plan prévu.
- Audio en RAM uniquement, maximum **60 secondes par tour** ; micro libéré avant le décodage. Le français est détecté automatiquement, sans requête réseau.
- Aucun envoi d’audio/texte ni journal de transcription dans l’intégration ajoutée. Pas de Gemini, de Deepgram, de télémétrie ou de compte utilisateur ajoutés.
- Permission Internet présente uniquement pour le téléchargement explicite du modèle. HTTPS, redirections HTTPS et fichiers vérifiés. Hugging Face et son infrastructure voient les requêtes et l’adresse IP lors du téléchargement.
- Fermer le clavier, changer de champ/app ou annuler empêche l’insertion d’un résultat tardif. Le décodeur natif déjà lancé peut terminer son calcul, mais le résultat annulé est ignoré.
- Dictée bloquée dans les champs de mot de passe. Pas d’envoi automatique du message : le texte reste à relire.
- Modèle dans `noBackupFilesDir`, sauvegarde Android désactivée pour ce fork. Désinstaller l’app supprime ses modèles ; pas de bouton de suppression à chaud pendant un décodage.

## Performances et limites

- Modèle de 0,6 milliard de paramètres ; **téléphone ARM64 récent, 6 Go de RAM ou plus conseillés**, sans certification de compatibilité. L’APK exclut les appareils 32 bits et les émulateurs x86.
- Chargement anticipé asynchrone à l’ouverture du clavier après installation, puis réutilisation du moteur en mémoire. Premier chargement ou retour après destruction du service plus lent.
- Transcription automatique avec ponctuation/majuscules, mais aucun moteur ne garantit les noms propres, les nombres, le silence, le bruit ou toutes les nuances d’intonation. Toujours relire un message important.
- Pas de VAD/segmentation ni de texte partiel : une dictée longue prend plus de temps à traiter. Limite explicite à 60 s pour borner la RAM ; tester d’abord sur 5–15 s.
- Pas de réécriture LLM ni correction conversationnelle ajoutée, pas de traduction, pas de synchronisation cloud. Les fonctions HeliBoard existantes restent celles de la version stable retenue, pas de la branche beta la plus récente.
- Balayage gestuel : même fonctionnement et mêmes restrictions qu’HeliBoard original, sa bibliothèque optionnelle n’est pas fournie.

## Compiler

JDK 17, Android SDK 35, NDK `28.0.13004108`, Linux x86_64 ou environnement Android Studio compatible. L’APK ARM64 contient les bibliothèques natives ; le modèle est téléchargé séparément dans l’app.

```sh
bash tools/prepare-local-voice.sh  # AAR officielle, version et SHA-256 épinglés
./gradlew --max-workers=2 :app:assembleDebugNoMinify \
  :app:testDebugNoMinifyUnitTest --tests 'helium314.keyboard.latin.voice.*'
```

APK : `app/build/outputs/apk/debugNoMinify/HeliBoard-Offline_3.7-local.1-debugNoMinify.apk`.

Le workflow GitHub Actions **Build debug APK** effectue aussi la préparation, la compilation et les tests ciblés, puis publie un artefact. Pour une diffusion durable, créer et conserver une **clé de signature de production privée** ; aucune clé privée ni mot de passe de signature n’est commis. Une clé de debug ne constitue pas une identité de distribution pérenne.

### Particularité de la VM ARM64

Le SDK Google Linux et le NDK embarquent des outils hôtes x86_64. Dans la VM utilisée ici : binfmt/QEMU x86_64 existant, ajout local de la bibliothèque hôte **zlib amd64** au RUNPATH du clang NDK, et adaptation locale du détecteur hôte `ndk_bin_common.sh` pour exécuter les outils x86_64 via QEMU sur aarch64. Ces adaptations sont **hors du dépôt** ; elles ne modifient pas le code Android ni l’architecture ARM64 de l’APK. Elles ne sont pas nécessaires sur une machine de build Linux x86_64. SDK et NDK installés dans `/home/ubuntu/Android/Sdk`.

## Vérifications

### Test moteur réel sur la VM (pas un téléphone)

Fichiers du modèle épinglés et vérifiés ; extrait français fourni par le dépôt ONNX, `test_wavs/fr.wav`, rééchantillonné de 22 050 Hz à 16 000 Hz avec ffmpeg. sherpa-onnx Python **1.12.40**, CPU, deux threads, même configuration de modèle que l’app :

- Durée audio : **4,97 s**.
- Chargement du moteur : **6,78 s** (hors contrôle des fichiers).
- Décodage : **1,71 s**.
- RSS maximale du processus : **environ 1,0 Go**.
- Résultat : « Ne vous demandez pas ce que votre pays peut faire pour vous. Demandez-vous plutôt ce que vous pouvez faire pour lui. »
- Second passage réussi : même texte et **aucun texte produit sur 1 seconde de silence numérique**. Ce contrôle ne vaut pas détection du silence d’un vrai microphone bruité.

C’est un **smoke test sur un seul extrait**, pas une mesure WER, pas une évaluation générale du français, pas un test de la capture micro Android ou de la latence mobile. La RAM de l’application Android peut être différente. Reproduction optionnelle avec `tools/check-local-model.py` (dépendances de test seulement : `sherpa-onnx==1.12.40`, `numpy`).

### Tests logiciels

`ModelFilesTest` vérifie les SHA-256, tailles exactes, corruptions de même taille, fichiers tronqués/surdimensionnés/absents et le catalogue de quatre fichiers épinglés. `ModelStoreTest` vérifie qu’une installation partielle ou un composant manquant/tronqué ne peut pas apparaître prêt. Les checksums complets sont vérifiés avant le chargement natif ; le contrôle rapide d’état ne remplace pas cette vérification.

`VoiceInputAvailabilityTest` vérifie aussi que le micro local ne dépend plus d’un moteur vocal externe, et que les champs de mot de passe et l’option d’interdiction du micro de l’application restent respectés.

**Livraison vérifiée :**

- Construction finale **sur cette VM ARM64** : `BUILD SUCCESSFUL`, APK produit.
- **9 tests réussis**, zéro échec : 7 nouveaux tests de dictée/installation/disponibilité et 2 tests existants `KeySpecParserTest`. La suite complète d’HeliBoard n’a pas été exécutée.
- Compilation indépendante du cœur de l’intégration et tests ciblés réussis sur [GitHub Actions](https://github.com/alexalmny/HeliBoard-OfflineDictation/actions/runs/41167439110). L’APK distribué est celui de la VM ; le correctif final d’autorité du fournisseur de fichiers est vérifié dans cet APK.
- Signature APK v2 vérifiée par `apksigner` ; contrôle `zipalign -c -P 16 4` réussi ; bibliothèques ARM64 uniquement et notices de licences présentes.
- Identifiant vérifié : `fr.alexalmny.offlinedictation.debug`, SDK minimum 26, cible 35. Autorité du fournisseur distincte d’HeliBoard original.
- APK : `HeliBoard-Offline_3.7-local.1-arm64-debug.apk`.
- SHA-256 : `4d39548b7b633f39f563e0cac9810f2d2b12c2fe79549740a6d7d3e584f189aa`.
- Téléchargement : [préversion v0.1.0-local](https://github.com/alexalmny/HeliBoard-OfflineDictation/releases/tag/v0.1.0-local).

**Aucun téléphone Android n’est connecté à la VM** : installation, capture réelle, mode avion, compatibilité IME entre applications, interface, batterie et performances mobiles restent à valider avec la checklist de l’audit.

## Licences et provenance

- HeliBoard v3.7 / cette intégration : GPL-3.0 (voir `LICENSE` et les mentions des sources d’origine).
- sherpa-onnx : [Apache 2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v1.12.40/LICENSE), [AAR officielle 1.12.40](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.40), ONNX Runtime fourni dans cette AAR : licence MIT. Sources et avis tiers de ces projets à conserver lors d’une redistribution.
- Modèle : **NVIDIA Parakeet TDT 0.6B v3**, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), [model card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3). Export ONNX quantifié INT8 par csukuangfj, hébergé dans [sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8), révision dans `ModelFiles.kt`. Cette conversion/quantification diffère des poids NeMo d’origine ; aucun entraînement supplémentaire dans ce fork.
