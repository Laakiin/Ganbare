# Ganbare


Application Android développée en **Kotlin** pour gérer son emploi du temps et ses tâches au quotidien.

---

## 📱 Aperçu

Ganbare permet de visualiser un agenda au format **iCal** et de créer des **tâches avec rappels**. L'application est pensée pour rester utile même en cas d'indisponibilité du serveur, grâce à une mise en cache locale de l'emploi du temps.

---

## ✨ Fonctionnalités

### 📅 Agenda iCal
- Affichage d'un emploi du temps depuis une URL au format iCal (`.ics`)
- Sauvegarde locale de l'agenda : si le serveur est en maintenance ou inaccessible, l'emploi du temps le plus récent reste consultable hors ligne

### ✅ Tâches & Rappels
- Création de tâches personnalisées avec une date et heure précises
- Notifications de rappel au moment voulu

### 🧩 Widgets
- **Widget "Prochain événement"** — affiche le prochain cours ou événement de l'agenda directement sur l'écran d'accueil
- **Widget "Prochaine tâche"** — affiche la prochaine tâche à compléter sur l'écran d'accueil

---

## 🗺️ Roadmap

- [x] Affichage de l'agenda iCal
- [x] Création de tâches avec rappels
- [x] Widget prochain événement
- [x] Widget prochaine tâche
- [ ] Persistance complète de l'agenda en cache offline

---

## 🛠️ Stack technique

| Technologie | Détail |
|---|---|
| Langage | Kotlin 100% |
| Plateforme | Android |
| Build system | Gradle (Kotlin DSL) |
| Format agenda | iCal (`.ics`) |
| Version actuelle | v1.2 |

---

## 📦 Installation

### Prérequis

- Android Studio (Hedgehog ou plus récent recommandé)
- JDK 17+
- Android SDK

### Cloner le projet

```bash
git clone https://github.com/Laakiin/Ganbare.git
cd Ganbare
```

### Lancer l'application

1. Ouvrir le projet dans **Android Studio**
2. Synchroniser les dépendances Gradle
3. Sélectionner un émulateur ou un appareil physique
4. Cliquer sur **Run ▶️**

### Ou télécharger l'APK

Rendez-vous sur la page [Releases](https://github.com/Laakiin/Ganbare/releases) pour télécharger la dernière version (`v1.2`).

---

## 📁 Structure du projet

```
Ganbare/
├── app/                  # Module principal de l'application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/     # Code source Kotlin
│   │   │   ├── res/      # Ressources (layouts, drawables, strings...)
│   │   │   └── AndroidManifest.xml
│   │   └── test/         # Tests unitaires
├── gradle/               # Wrapper Gradle
├── build.gradle.kts      # Configuration du build (root)
└── settings.gradle.kts   # Paramètres du projet
```
