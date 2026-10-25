# Pato, Pato, Ganso - Squid Craft Games 4 (mod Fabric 1.21.1)

Mod **solo de servidor**: los jugadores entran con Minecraft Java normal, sin instalar nada
(los sombreros son entidades vanilla, no hace falta resource pack).

## Como se juega (segun el cuarto dia de los SCG4)
1. Los jugadores (por defecto **6**) se sientan en circulo alrededor de un **arbol**. Todos son **patos**: llevan un
   sombrero de pato amarillo.
2. Una **botella** gira y elige a quien queda fuera del circulo (el caminante).
3. El caminante camina alrededor y hace clic en jugadores sentados:
   - **Tinte amarillo = "Pato"** (puede decirlo **todas las veces que quiera**).
   - **Tinte blanco = "GANSO"**: el elegido recibe el **sombrero de ganso blanco** y empieza la carrera.
4. El caminante y el ganso corren al **arbol, lo tocan y vuelven al asiento del ganso**. Hay un tiempo limite
   (configurable, barra de jefe con cuenta atras).
5. Quien se queda de pie queda **eliminado** (si se acaba el tiempo y nadie llego, caen los dos).
6. Rondas hasta que quede **un solo superviviente** (o hasta las rondas que configures).

## Instalar
Necesitas Java 21, un servidor Fabric 1.21.1 y Fabric API 0.102.0+1.21.1.

```
./gradlew build            # (Windows: gradlew.bat build)
```
El mod queda en `build/libs/patopatoganso-1.0.0.jar`. Copialo a la carpeta `mods/` del servidor junto con Fabric API.
Tambien funciona en un mundo de un jugador / LAN si lo pones en tu carpeta `mods` (y tienes Fabric API).

## Comandos
| Comando | Quien | Que hace |
|---|---|---|
| `/pg build` | op | Construye la arena donde estas: suelo de arena, arbol, asientos, borde arcoiris y muro invisible |
| `/pg setcenter` | op | Usa tu posicion como centro (arbol) si ya tienes tu propia arena |
| `/pg join` / `/pg leave` | todos | Entrar o salir de la sala (se llena a 6 y empieza sola) |
| `/pg start` / `/pg stop` | op | Empezar (minimo `minPlayers`) o parar y limpiar |
| `/pg status` | todos | Estado de la sala o la partida |
| `/pg config` | op | Ver la configuracion |
| `/pg config <clave> <valor>` | op | Cambiar un ajuste |

## Configuracion (`/pg config` o `config/patopatoganso.json`)
| Clave | Por defecto | Significado |
|---|---|---|
| `players` | 6 | Jugadores por circulo (plazas de la sala) |
| `minPlayers` | 2 | Minimo para `/pg start` |
| `raceSeconds` | 20 | **Tiempo de la carrera** (el que quieras) |
| `walkSeconds` | 0 | Tiempo maximo del caminante para decir ganso; 0 = sin limite |
| `maxRounds` | 0 | Rondas maximas; 0 = hasta que quede uno |
| `countdownSeconds` | 5 | Cuenta atras inicial |
| `seatRadius` | 8.0 | Radio del circulo |
| `touchRadius` | 2.0 | Distancia al arbol para contar como tocado |
| `seatTolerance` | 1.8 | Distancia al asiento para contar como sentado |
| `seatOffsetY` | -0.9 | Altura del asiento (ajustalo si ves a los jugadores flotando o hundidos) |
| `elimination` | spectator | `spectator` (pasan a espectador) o `kill` (mueren de verdad) |
| `bothEliminatedOnTimeout` | true | Si se acaba el tiempo, caen los dos |
| `autoStart` | true | Empieza sola al llenarse la sala |

Ejemplo: `/pg config raceSeconds 30`, `/pg config elimination kill`.

## Notas
- Durante la partida los jugadores estan en modo aventura y no reciben dano.
- Si alguien se desconecta o usa `/pg leave` en plena partida, abandona y se sigue sin el.
- Autor: ianntr997 - https://kick.com/ianntr997
