# Estrategia de testing para proveedores meteorológicos

Objetivo
--------
Proveer una estrategia reproducible que permita validar la integración con proveedores meteorológicos sin depender de límites de cuota ni flakiness de redes.

Tipos de tests y cuándo correrlos
--------------------------------
- Unit tests (rápidos, en cada PR): mockean `WeatherProvider` o la capa HTTP y validan la lógica de negocio y mapeo.
- Integration tests (en CI nightly o etapa separada): utilizan WireMock para simular respuestas concretas de cada proveedor. Se ejecutan en CI y son deterministas.
- Live tests (manuales/periodicos): hacen peticiones reales a proveedores (Open-Meteo por defecto, o WeatherAPI si está configurado). No correr en every-PR.

Infraestructura y dependencias
-----------------------------
- WireMock (ya añadido en `pom.xml`) para tests de integración locales y CI.
- JUnit 5 para pruebas.

Fixtures
--------
Guardar respuestas reales (JSON) en `src/test/resources/fixtures/{provider}/...` para reutilizar en tests.

Ejemplos de comandos
--------------------
- Ejecutar tests unitarios y de integración locales:

```powershell
mvn -DskipITs=false test
```

- Ejecutar solo tests de integración con live provider (usa variables de entorno para claves):

```powershell
$env:WEATHERAPI_KEY = 'tu_key'
mvn -Dtest=com.javamid.client.LiveWeatherIntegrationTest test
```

WireMock y grabar respuestas (record/playback)
---------------------------------------------
WireMock permite grabar peticiones reales y luego reproducirlas en CI. Flujo recomendado:
1. Ejecuta WireMock en modo proxy que apunte al proveedor real y guarda los mappings (record). 
2. Añade los mappings JSON a `src/test/resources/wiremock/{provider}`.
3. En CI, arranca WireMock con esos mappings y ejecuta tests contra el servidor local.

Guía rápida para un test de integración con WireMock (resumen):
1. Añade `wiremock-jre8` en `pom.xml` (ya hecho).
2. En el test, arranca `WireMockServer` en puerto dinámico.
3. Stubea la ruta esperada (`/v1/current.json`, `/data/2.5/weather`, etc.) con la respuesta de prueba.
4. Construye `WeatherProvider` pasando `baseUrl` apuntando al WireMock local.
5. Ejecuta el test y valida el `WeatherDTO`.

Comprobaciones que realizan los tests de mapeo
---------------------------------------------
- Los campos básicos existen: `location`, `temperature`, `wind`, `humidity`.
- Las unidades son normalizadas a lo esperado (temp en °C, wind en m/s).
- Campos opcionales manejados (por ejemplo, `windDirection` puede ser null).

Responsabilidad del repositorio
-------------------------------
- Mantener fixtures actualizados en `src/test/resources/fixtures/`.
- Mantener un README corto para cómo ejecutar tests live y cómo grabar mappings de WireMock.

Si quieres, implemento ahora los DTOs y el provider `OpenMeteoProvider` para que el proyecto esté alineado con los diagramas y la documentación; además actualizaré `WeatherServiceImpl` y `WeatherController` para que devuelvan `WeatherDTO` y escribiré tests de integración con WireMock. ¿Lo implemento ahora? 

