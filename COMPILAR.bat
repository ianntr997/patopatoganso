@echo off
chcp 65001 >nul
title Compilar mod Pato Pato Ganso
echo ================================================
echo   Compilando el mod Pato, Pato, Ganso
echo ================================================
echo.
where java >nul 2>nul
if errorlevel 1 (
  echo No encuentro Java. Instalando Java 21 con winget...
  winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-package-agreements --accept-source-agreements
  echo.
  echo Java instalado. CIERRA esta ventana y vuelve a abrir COMPILAR.bat.
  pause
  exit /b
)
echo Java encontrado. Esto puede tardar varios minutos la primera vez...
echo.
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo *** ERROR al compilar. Copia el texto de arriba y pasaselo a Claude. ***
  pause
  exit /b 1
)
echo.
echo ================================================
echo   LISTO. Tu mod esta en la carpeta build\libs
echo   Usa el archivo patopatoganso-1.0.0.jar
echo   (NO el que termina en -sources)
echo ================================================
start "" "%~dp0build\libs"
pause
