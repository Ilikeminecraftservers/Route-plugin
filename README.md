# RoutePlugin

Ein Minecraft Spigot/Paper Plugin zum Erstellen und Verfolgen von Routen mit Partikeln.  
Spieler können Routen ähnlich wie bei Google Maps sehen und diesen folgen.  

## Installation
1. Baue das Projekt mit Maven (`mvn package`).
2. Kopiere die JAR nach `plugins/` deines Servers.
3. Starte den Server.

## Befehle
- `/route create <Name>`
- `/route setparticle <RouteName> <Particle>`
- `/route follow start <RouteName>`
- `/route follow pause`
- `/route follow unpause`
- `/route follow end`
- `/route edit select <RouteName>`
- `/route edit deselect`
- `/route edit point add`
- `/route edit linefollowstart`
- `/route edit linefollowpause`
- `/route edit linefollowunpause`

## Config
- `default-particle`: Standard Partikeltyp.
- `linefollow-point-distance`: Abstand, wann neue Punkte bei LineFollow gesetzt werden.
- `line-particle-spacing`: Abstand für Linien-Rendering (geplant).

## Permissions
- `route.use` (Standard: true)

## Hinweise
- Routen werden in `routes.yml` gespeichert.
- Am Ende einer Route erscheint ein zufälliges Feuerwerk.
