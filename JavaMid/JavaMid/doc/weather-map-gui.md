# Weather Map Viewer

## Interfaz Gráfica de Mapa Meteorológico

Esta aplicación proporciona una interfaz gráfica en Swing que muestra un mapa interactivo de OpenStreetMaps. Al navegar por el mapa, automáticamente busca la estación meteorológica más cercana al centro y muestra sus datos meteorológicos en tiempo real utilizando la API de Meteostat.

## Características

- **Mapa Interactivo**: Visualización de OpenStreetMaps con navegación completa (pan, zoom)
- **Búsqueda Automática**: Al mover el mapa, busca automáticamente la estación meteorológica más cercana al centro
- **Datos en Tiempo Real**: Muestra datos meteorológicos actualizados de la estación seleccionada desde Meteostat
- **Interfaz Intuitiva**: Panel lateral con información detallada de temperatura, humedad, viento, etc.

## Componentes Principales

### 1. `WeatherStation` (Model)
Representa una estación meteorológica con:
- ID, nombre, coordenadas (latitud/longitud)
- País y estado
- Método para calcular distancia usando la fórmula de Haversine

### 2. `WeatherStationService` (Service)
Servicio para buscar estaciones meteorológicas:
- `findNearestStation(lat, lon)`: Encuentra la estación más cercana a unas coordenadas
- `findNearbyStations(lat, lon, count)`: Encuentra múltiples estaciones cercanas
- Utiliza la API de Meteostat (RapidAPI)

### 3. `WeatherMapWindow` (UI)
Ventana principal de la aplicación:
- Mapa interactivo usando JXMapViewer2
- Panel de información con datos de la estación y clima
- Listeners para detectar movimiento del mapa
- Actualización automática de datos al navegar
- Campo para ingresar API Key de Meteostat

## Uso

### Requisitos Previos
1. Obtener una API Key de RapidAPI para Meteostat: https://rapidapi.com/meteostat/api/meteostat
2. Java 11 o superior
3. Maven para gestión de dependencias

### Ejecución

```bash
# Compilar el proyecto
mvn clean compile

# Ejecutar la aplicación
mvn exec:java -Dexec.mainClass="com.javamid.ui.WeatherMapWindow"
```

O ejecutar directamente desde tu IDE la clase `WeatherMapWindow.java`.

### Funcionalidades Interactivas

1. **Navegación del Mapa**:
   - Click y arrastrar para mover el mapa
   - Scroll del ratón para hacer zoom
   - Al soltar, automáticamente busca la estación más cercana al centro

2. **Visualización de Datos**:
   - Panel derecho muestra el nombre de la estación encontrada
   - Coordenadas del centro del mapa
   - Datos meteorológicos completos (temperatura, humedad, viento, presión, etc.)

3. **Actualización Manual**:
   - Botón "Actualizar Datos" para refrescar la información meteorológica

## Arquitectura

```
UI Layer (Swing)
    ↓
Service Layer (WeatherStationService)
    ↓
Client Layer (MeteostatApiClient)
    ↓
External API (Meteostat via RapidAPI)
```

## Dependencias Agregadas

- **JXMapViewer2 (2.6)**: Librería para integración de mapas OpenStreetMaps en Swing
- **Jackson**: Para procesamiento JSON (ya existente)
- **Spring Boot**: Framework base (ya existente)

## API de OpenWeatherMap Utilizada

1. **Find API** (`/data/2.5/find`): Para buscar estaciones cercanas a coordenadas
2. **Weather API** (`/data/2.5/weather`): Para obtener datos meteorológicos de una ubicación

## Notas Técnicas

- La búsqueda de estaciones se realiza en background usando `SwingWorker` para no bloquear la UI
- La actualización de datos meteorológicos también es asíncrona
- El mapa utiliza tiles de OpenStreetMap de forma gratuita
- Se incluye un flag `isUpdating` para evitar múltiples solicitudes simultáneas
