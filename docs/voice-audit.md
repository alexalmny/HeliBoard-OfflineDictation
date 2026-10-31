# Audit : dictée universelle Android

## Besoin et décision

Remplacer la dictée traditionnelle du clavier par une transcription neuronale avec ponctuation, utilisable dans les applications Android. Choix explicites de l’utilisateur : **tout hors ligne**, **fork HeliBoard**. Pas de connexion à un compte ChatGPT ou Claude, pas d’abonnement ni clé API.

## Ce qui est réellement public sur ChatGPT et Claude

- [OpenAI, Voice Dictation FAQ](https://help.openai.com/en/articles/12168547-voice-dictation-faq) : le bouton de dictée enregistre un message et envoie l’audio pour transcription ; le texte peut être corrigé avant envoi. Cela n’établit ni que la dictée est un vrai streaming de bout en bout, ni le modèle exact actuellement utilisé dans chaque application. Page identifiée par recherche ; récupération directe bloquée par HTTP 403 dans cette VM.
- [Anthropic, dictée mobile](https://support.anthropic.com/en/articles/10065434-using-dictation-on-claude-mobile) : fonctionnalité de conversion parole/texte distincte du mode conversation vocale. La documentation consultée ne nomme pas le modèle ni le fournisseur. Ne pas affirmer que Claude utilise Whisper.
- [OpenAI, Realtime transcription](https://developers.openai.com/api/docs/guides/realtime-transcription) : API publique de transcription sans réponse vocale d’assistant, audio PCM via WebSocket, événements partiels/finals, commit des tours. La documentation consultée recommande `gpt-live-transcribe` pour le flux continu. Une telle API est une possibilité **cloud**, pas une solution hors ligne et pas la preuve du moteur interne de l’app ChatGPT.
- [NVIDIA, modèle Parakeet TDT v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) : 25 langues dont le français, ponctuation et capitalisation, licence CC BY 4.0. Les benchmarks NVIDIA ne constituent pas une mesure de ce fork ni d’un téléphone Android.

La qualité perçue provient du modèle acoustique/linguistique, du contexte audio et de sa ponctuation apprise ; il n’est pas nécessaire de reformuler chaque phrase avec un agent conversationnel. Aucun post-traitement LLM n’est ajouté, pour ne pas inventer ni réécrire les propos.

## Bases comparées

Étoiles observées au moment de l’audit, chiffres variables :

| Projet | Étoiles environ | Analyse |
|---|---:|---|
| [HeliBoard](https://github.com/Helium314/HeliBoard) | 6 456 | GPL-3.0, clavier complet ; retenu comme base directe. Version stable v3.7 pour une chaîne SDK 35 disponible et reproductible dans cette VM. |
| [FUTO Keyboard](https://github.com/futo-org/android-keyboard) | 3 412 | Dictée locale déjà intégrée ; licence Source First spécifique, pas à présenter comme une licence open source OSI classique. Pas nécessaire de reproduire ce produit. |
| [whisperIME](https://github.com/woheller69/whisperIME) | 654 | IME vocal local ; alternative intéressante si le besoin était uniquement un clavier compagnon. |
| [WhisperBoard](https://github.com/david-digitis/WhisperBoard) | 7 | Fork HeliBoard explorant Whisper puis Parakeet ; son README ne correspond pas totalement au code actuel. Cloud Deepgram, Gemini, journaux de texte, chargement potentiellement bloquant et capture non bornée : pas retenu comme base de confiance. Pas de reprise de ces fonctions. |
| [Dictus](https://github.com/getdictus/dictus-android) | 28 | Autre clavier local, mais pas la base mature demandée. |

## Intégration retenue

- HeliBoard v3.7, commit `267ffbf9523581d94b99ff7901b0dcf92e3ce186`.
- Android IME standard (`InputMethodService`) : texte transmis au champ via l’input connection d’HeliBoard. Pas d’accessibilité détournée, pas de presse-papiers automatique, pas de remplacement interne du micro Gboard.
- sherpa-onnx 1.12.40, AAR officielle épinglée par SHA-256, calcul CPU INT8, deux threads.
- Parakeet TDT 0.6B v3, export [ONNX](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8), révision `2bda32ec70b097a55adaa07d9a7173915b43cc78`. Quatre fichiers, **670 478 772 octets** au total. Tailles et SHA-256 vérifiés avant activation, puis au chargement du moteur.
- Capture PCM mono 16 kHz en mémoire, maximum 60 secondes par tour. Pas de fichier audio. Calcul et chargement hors thread UI ; moteur conservé chaud tant que le service reste vivant.
- À l’arrêt : libération du micro, décodage local, insertion du résultat final. **Ce n’est pas du streaming**. La limite est explicite dans l’interface.
- Fermeture du clavier, changement de champ/application ou annulation : abandon du résultat ; jamais d’insertion tardive dans une autre application. Un calcul JNI déjà démarré n’est pas interrompu brutalement ; son résultat est ignoré.
- Champs de mot de passe bloqués. Aucun transcript/audio dans les logs ajoutés. Sauvegarde système désactivée ; modèle dans l’espace privé non sauvegardé.
- Réseau uniquement à la demande pour le modèle : HTTPS et redirections HTTPS uniquement, fichiers temporaires, borne de taille, contrôle de checksum, marqueur d’installation après succès. Annulation et téléchargement incomplet n’activent pas un modèle incomplet.

## Limites et critères de validation

Le modèle est **offline/batch** dans cette intégration. Obtenir du vrai streaming exige un autre modèle streaming ou une segmentation/VAD avec réconciliation ; ce n’est pas un bouton à activer sur ce décodeur. Une fausse transcription partielle par redécodage permanent consommerait davantage et risquerait de dégrader la ponctuation.

Avant de promettre « même qualité que ChatGPT » ou « instantané », il faut mesurer sur le téléphone cible : temps de chargement à froid, délai après arrêt, RAM maximale, température/batterie, précision et ponctuation sur la voix réelle. Prévoir un corpus français avec noms propres, nombres/dates, questions, négations, bruit, silence et alternance français/anglais. Au moins 20 extraits, références relues humainement ; compter les erreurs de mots et de ponctuation séparément. Le silence et le bruit peuvent produire des erreurs ou du texte parasite avec tout moteur ASR : les résultats doivent rester relisibles par l’utilisateur.

Test matériel requis : autorisation refusée puis accordée, mode avion après installation, dictée dans SMS/notes/navigateur, changement d’app pendant le calcul, Retour/annulation, fermeture du clavier, rotation, micro occupé, deux dictées successives, dictée de 60 s, réseau coupé durant installation, modèle tronqué, stockage insuffisant. Aucun envoi de message automatique.

Cette livraison est un **prototype installable à valider sur appareil**, pas une affirmation de parité avec les applis commerciales. Les tests logiciels et le test moteur sur la VM sont consignés séparément dans `OFFLINE-DICTATION.md`.
