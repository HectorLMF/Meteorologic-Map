# Meteorologic-Map / JavaMid

Proyecto Java para visualizar mapas meteorológicos y datos de clima (OpenStreetMap + APIs de clima). Incluye una GUI (`WeatherMapWindow`) y controladores REST para integrar servicios.

## Requisitos
- JDK 11
- Maven 3.8+
- Conexión a Internet (para APIs de tiles y clima)

## Estructura
- Código principal: `JavaMid/JavaMid`
- `pom.xml`: configuración de Maven y plugins
- Configuración: `JavaMid/JavaMid/src/main/resources/application.properties` y `Apikey.conf`

## Configuración
- Revisa/edita claves y endpoints en `Apikey.conf` y `application.properties`.
- Si usas proxies o limitaciones de red, ajusta la configuración según tus necesidades.

## Compilar
```bash
# Linux/macOS
cd JavaMid/JavaMid
mvn clean package

# Windows (PowerShell)
cd "JavaMid/JavaMid"
mvn clean package
```
Artefacto generado: `JavaMid/JavaMid/target/JavaMid-<version>.jar`

## Ejecutar (opciones)
- GUI (plugin exec):
```bash
cd JavaMid/JavaMid
mvn exec:java -Dexec.mainClass=com.javamid.ui.WeatherMapWindow
```
- Spring Boot (si procede):
```bash
cd JavaMid/JavaMid
mvn spring-boot:run
```

## Tests
```bash
cd JavaMid/JavaMid
mvn test
```

## Release automática (GitHub Actions)
Este repositorio incluye un workflow para publicar una release manualmente.

Pasos:
1. Ve a la pestaña "Actions" en GitHub.
2. Elige el workflow "Release".
3. Pulsa "Run workflow".
   - Opcional: define `tag` (por ejemplo `v1.0.0`). Si lo dejas vacío, se usará la versión del `pom.xml`.
   - `prerelease`: marca la release como prerelease si quieres.
   - `notes`: texto libre para las notas de la release.
4. El workflow compila el proyecto y publica el `.jar` en la release.

## Contribuir
- Abre issues o PRs con mejoras.
- Mantén el estilo de código y añade tests cuando sea posible.

## Licencia
- Este proyecto es de uso académico/demostrativo. Ajusta la licencia según tus necesidades.