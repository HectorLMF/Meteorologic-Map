# Arquitectura: Datos meteorológicos con Meteostat

Resumen
-------
Esta aplicación consume datos meteorológicos de Meteostat (vía RapidAPI) y normaliza los datos a un modelo interno estable (DTOs). El objetivo es proporcionar información meteorológica de estaciones reales mediante una interfaz gráfica de mapa.

Componentes principales
-----------------------
- MeteostatApiClient: cliente para conectarse a la API de Meteostat (RapidAPI)
- WeatherStationService: servicio para buscar estaciones meteorológicas cercanas usando Meteostat
- WeatherStation: modelo que representa una estación meteorológica con sus coordenadas y metadatos
- WeatherMapWindow: interfaz gráfica de usuario con mapa interactivo de OpenStreetMap
- DTOs internos: `WeatherDTO`, `LocationDTO`, `ConditionDTO`, `ForecastDTO`, `ForecastDayDTO`. Normalizan campos (temperatura en °C, velocidad de viento en m/s, timestamps en ISO UTC, etc.).

Decisiones de diseño
--------------------
- Proveedor único: Solo se utiliza Meteostat como fuente de datos meteorológicos
- Unidades: las temperaturas se normalizarán a grados Celsius (°C). Las velocidades de viento se normalizan a metros por segundo (m/s) internamente.
- Configuración: la API key de Meteostat se puede configurar mediante:
  - Variable de entorno: METEOSTAT_RAPIDAPI_KEY o RAPIDAPI_KEY
  - Archivo de configuración: meteostat.rapidapi.key en application.properties
  - Interfaz de usuario: campo de API Key en el panel de configuración
- Errores: las implementaciones lanzarán excepciones controladas (`MeteostatApiException`) en caso de fallos HTTP u otros errores de parsing.

Flujo de ejecución para búsqueda de estación meteorológica
---------------------------------------------------------
1. Usuario mueve el mapa a una nueva ubicación
2. `WeatherMapWindow` obtiene las coordenadas del centro del mapa
3. `WeatherStationService.findNearestStation(lat, lon)` busca la estación más cercana
4. `MeteostatApiClient.getNearbyStations()` consulta la API de Meteostat
5. Se parsean los resultados y se selecciona la estación más cercana
6. Se obtienen los metadatos completos de la estación con `getStationMeta()`
7. Se obtienen los datos meteorológicos con `getStationHourly()`
8. La información se muestra en el panel lateral de la interfaz

Variables de entorno relevantes
------------------------------
- METEOSTAT_RAPIDAPI_KEY o RAPIDAPI_KEY: clave de API de RapidAPI para Meteostat
- meteostat.rapidapi.key: clave en archivo de configuración (application.properties)

Notas sobre rendimiento y límites
--------------------------------
- Las llamadas a proveedores externos deben ser limitadas y cacheadas si el uso en producción es intensivo. Implementa caché con TTL en `WeatherServiceImpl` si es necesario.
- Para testing, preferir WireMock o Open-Meteo (sin clave) para evitar límites de API.

Siguientes pasos recomendados
---------------------------
- Implementar los DTOs y la interfaz `WeatherProvider` en el código.
- Implementar `OpenMeteoProvider` primero (no requiere clave) y escribir tests de integración con WireMock para validar el mapeo.
- Opcional: exponer una configuración que permita cambiar proveedor en runtime (Spring `@ConditionalOnProperty` o similar).

