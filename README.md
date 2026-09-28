# MC Helden Forge Mod 1.21.1

Minecraft Forge Mod für das MC Helden Spielsystem.

## Features

- **Hearts System**: Spieler starten mit 2 Herzen
- **PvP Deaths**: Verlust eines Herzens bei Tod durch andere Spieler
- **24-Stunden Ban**: Spieler mit 0 Herzen werden für 24 Stunden gebannt
- **20-Minuten Protection**: Rückkehrende Spieler erhalten 1 Herz und 20 Minuten Unverwundbarkeit
- **20% Loot Return**: 20% des Inventars wird bei Tod gespeichert und bei Rückkehr wiederhergestellt

## Build

```bash
gradlew.bat build
```

Die JAR wird in `build/libs/mchelden-1.0.0.jar` erstellt.

## Installation

1. JAR-Datei in den `mods/` Ordner des Forge-Servers kopieren
2. Server neu starten

## Commands

- `/helden herzen` - Zeigt deine aktuelle Herzanzahl
- `/helden setherzen <value>` - Setzt Herzen (nur für Admins)
