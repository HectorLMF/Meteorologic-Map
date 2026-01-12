# Manual de Usuario: Meteorologic-Map

Este manual explica cómo instalar, configurar y usar la aplicación de forma completa.

## 1. Requisitos
- Windows, macOS o Linux.
- JDK 11 instalado.
- Conexión a Internet para mapas y datos meteorológicos.
- Opcional: Maven 3.8+ si compilas desde código.

## 2. Instalación
### Opción A: Usar el JAR empaquetado
1. Compila o descarga la release (archivo `.jar`).
2. Coloca el `.jar` en una carpeta conveniente.

### Opción B: Compilar desde código
1. Abre una terminal.
2. Ejecuta:
```bash
cd JavaMid/JavaMid
mvn clean package
```
3. El artefacto quedará en `JavaMid/JavaMid/target/JavaMid-<version>.jar`.

## 3. Configuración
- Archivo `JavaMid/JavaMid/src/main/resources/application.properties`: parámetros generales.
- Archivo `Apikey.conf`: para proveedores que requieren clave (no necesaria para Open-Meteo). Si habilitas otro proveedor, coloca aquí tu API key.
- Red/Proxy: si necesitas proxy, ajusta variables del sistema o configuración de red.

## 4. Ejecución
### Ejecutar el JAR
```bash
cd JavaMid/JavaMid/target
java -jar JavaMid-<version>.jar
```
Esto inicia la aplicación con la GUI.

### Ejecutar con Maven (desarrollo)
```bash
cd JavaMid/JavaMid
mvn exec:java -Dexec.mainClass=com.javamid.JavaMidApplication
```

## 5. Uso de la aplicación
### Pantalla principal
- Mapa principal (OpenStreetMap) con estaciones y marcadores.
- Barra de tiempo global: controla el instante meteorológico mostrado; no se reinicia al cambiar de estación.
- Panel de overlays: selecciona la capa de visualización activa.

### Estaciones
- Selección: haz clic en una estación o usa el buscador (si está habilitado) para centrar el mapa.
- Datos: el sistema consulta y cachea lecturas de temperatura/humedad; la caché acelera consultas repetidas.

### Overlays
- Sin overlay por defecto: al iniciar, ninguna capa está activa.
- Activar capa: elige "Temperatura" o "Humedad"; el gestor asegura exclusividad (solo una capa activa).
- Desactivar: selecciona "Ninguna" o desmarca la opción correspondiente.

### Barra de tiempo
- Arrastra el control para cambiar el instante; el presenter sincroniza los datos y el render de la capa seleccionada.
- Persistencia: el valor se mantiene al cambiar de estación.

### Viento y estilos (Flyweight)
- Las partículas de viento reciclan estilos (color/grosor) según velocidad y dirección, optimizando rendimiento.

## 6. Buenas prácticas y límites
- Rate limiting: Open-Meteo puede responder 429; evita mover la barra de tiempo demasiado rápido y confía en la caché.
- Actualizaciones: si cambias de proveedor o clave, reinicia la app.

## 7. Solución de problemas
- La app no inicia:
  - Verifica JDK 11 (`java -version`).
  - Recompila con `mvn clean package`.
- El mapa no carga:
  - Comprueba tu conexión a Internet y firewall.
- Datos vacíos o lentitud:
  - Limita el número de estaciones visibles; la caché acelera lecturas repetidas.
- Error 429 (rate limit):
  - Espera unos segundos o reduce la frecuencia de cambios; la caché ayuda.

## 8. Atajos y controles
- Zoom del mapa: rueda del ratón o controles en pantalla.
- Arrastrar mapa: clic sostenido y mover.
- Cambiar overlay: panel de capas.
- Cambiar tiempo: barra temporal.

## 9. FAQ
- ¿Necesito API key?
  - Para Open-Meteo, no. Para otros proveedores, usarás `Apikey.conf`.
- ¿Puedo añadir una nueva capa?
  - Sí, implementa `WeatherOverlay` y registra el panel.
- ¿Puedo cambiar el proveedor?
  - Sí, implementa `WeatherProvider` y ajusta la fábrica/configuración.

## 10. Recursos
- Arquitectura y patrones: [JavaMid/JavaMid/doc/arquitectura-patrones.md](JavaMid/JavaMid/doc/arquitectura-patrones.md)
- Documento explicativo: [JavaMid/JavaMid/doc/documento-explicativo.md](JavaMid/JavaMid/doc/documento-explicativo.md)
