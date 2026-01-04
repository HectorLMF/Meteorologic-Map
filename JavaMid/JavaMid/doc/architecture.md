# Arquitectura: Multi-proveedor de datos meteorológicos

Resumen
-------
Esta arquitectura adapta el proyecto para consumir datos meteorológicos de múltiples proveedores (por ejemplo, WeatherAPI, Open-Meteo, OpenWeather) y normalizarlos a un modelo interno estable (DTOs). El objetivo es desacoplar la lógica de negocio del formato concreto de cada proveedor y facilitar testing (mocks/WireMock) y la incorporación de nuevos proveedores.

Componentes principales
-----------------------
- WeatherProvider (interfaz): abstracción que expone métodos para obtener el tiempo actual y pronósticos.
- ProviderFactory: fábrica/selector que devuelve una implementación concreta de `WeatherProvider` según la configuración (variable de entorno `WEATHER_PROVIDER`).
- Implementaciones de proveedor (adapters): `OpenMeteoProvider`, `WeatherApiProvider`, `OpenWeatherProvider`. Cada uno consulta el proveedor y mapea la respuesta al `WeatherDTO`/`ForecastDTO`.
- DTOs internos: `WeatherDTO`, `LocationDTO`, `ConditionDTO`, `ForecastDTO`, `ForecastDayDTO`. Normalizan campos (temperatura en °C, velocidad de viento en m/s, timestamps en ISO UTC, etc.).
- WeatherService / WeatherServiceImpl: servicio que actúa como fachada para la lógica del dominio, inyectado con un `WeatherProvider`.
- WeatherController: controlador HTTP que expone endpoints REST (por ejemplo, `/weather` y `/forecast`) y devuelve DTOs.

Decisiones de diseño
--------------------
- Unidades: las temperaturas se normalizarán a grados Celsius (°C). Las velocidades de viento se normalizan a metros por segundo (m/s) internamente.
- Configuración: la selección de proveedor se controla con la variable de entorno `WEATHER_PROVIDER` (valores esperados: `weatherapi`, `open-meteo`, `openweather`).
- Claves y base URLs: cada proveedor tiene su propia variable de entorno para la API key y, opcionalmente, para `BASE_URL`. Por ejemplo:
  - WEATHERAPI_KEY, WEATHERAPI_BASE_URL
  - OPENWEATHER_API_KEY, OPENWEATHER_BASE_URL
  - (Open-Meteo no requiere clave, pero soportamos OPENMETEO_BASE_URL si se desea)
- Errores: las implementaciones lanzarán excepciones controladas (`WeatherClientException`) en caso de fallos HTTP u otros errores de parsing.

Flujo de ejecución para una petición `/weather?city=Madrid`
---------------------------------------------------------
1. `WeatherController` recibe la petición y llama a `WeatherService.getCurrentWeather("Madrid")`.
2. `WeatherServiceImpl` obtiene (por inyección o fábrica) la implementación de `WeatherProvider` configurada.
3. `WeatherProvider.getCurrent("Madrid")` consulta el proveedor (HTTP), parsea la respuesta y devuelve un `WeatherDTO` normalizado.
4. `WeatherServiceImpl` aplica lógica adicional si es necesario (caching, agregaciones) y devuelve el DTO al controlador.
5. `WeatherController` devuelve JSON serializado del `WeatherDTO`.

Extender con un nuevo proveedor (pasos)
---------------------------------------
1. Crear una clase que implemente `WeatherProvider` y añadirla al paquete `com.javamid.provider.impl`.
2. Implementar `getCurrent`/`getForecast` para consultar la API del proveedor, parsear la respuesta y mapear a los DTOs.
3. Añadir la opción al `ProviderFactory` (por ejemplo, `case "myprovider": return new MyProvider(...)`).
4. Añadir variables de entorno necesarias para la clave y base URL.
5. Añadir tests: unitarios (mocks) y de integración con WireMock si el proveedor tiene una API HTTP compatible.

Variables de entorno relevantes
------------------------------
- WEATHER_PROVIDER: (optional) `weatherapi` | `open-meteo` | `openweather` — default: `open-meteo`.
- WEATHERAPI_KEY, WEATHERAPI_BASE_URL
- OPENWEATHER_API_KEY, OPENWEATHER_BASE_URL
- OPENMETEO_BASE_URL
- TEST_CITY, TEST_LAT, TEST_LON — usados por tests de integración en vivo.

Notas sobre rendimiento y límites
--------------------------------
- Las llamadas a proveedores externos deben ser limitadas y cacheadas si el uso en producción es intensivo. Implementa caché con TTL en `WeatherServiceImpl` si es necesario.
- Para testing, preferir WireMock o Open-Meteo (sin clave) para evitar límites de API.

Siguientes pasos recomendados
---------------------------
- Implementar los DTOs y la interfaz `WeatherProvider` en el código.
- Implementar `OpenMeteoProvider` primero (no requiere clave) y escribir tests de integración con WireMock para validar el mapeo.
- Opcional: exponer una configuración que permita cambiar proveedor en runtime (Spring `@ConditionalOnProperty` o similar).

