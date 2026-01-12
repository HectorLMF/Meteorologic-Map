# Meteorologic-Map: Documento Explicativo

Este documento describe qué es el proyecto, qué tecnologías emplea, cómo está organizado y qué objetivos persigue.

## Qué es
Meteorologic-Map es una aplicación Java que visualiza datos meteorológicos sobre mapas (OpenStreetMap), permitiendo al usuario explorar estaciones, ajustar una barra temporal y activar distintas capas de visualización (overlays) como temperatura y humedad. El sistema integra clientes de datos externos (p. ej., Open-Meteo) a través de un contrato común y aprovecha patrones de diseño para lograr un código mantenible, extensible y eficiente.

## Tecnologías empleadas
- Java 11 (JDK): lenguaje y runtime principal.
- Maven: construcción y gestión de dependencias.
- Spring Boot 2.7.x: empaquetado del ejecutable (fat JAR) y soporte opcional para endpoints REST.
- JXMapViewer2: integración con mapas de OpenStreetMap en la GUI Swing.
- Jackson (2.15.x): serialización/parseo JSON para respuestas de APIs.

## Cómo funciona (visión general)
- Capa de datos: `WeatherClient` define el contrato de acceso a datos; `OpenMeteoAdapter` adapta la API externa a ese contrato; `CachingWeatherClient` añade caché para mejorar rendimiento y reducir llamadas repetidas.
- Proveedor lógico: `WeatherProvider` expone operaciones de mayor nivel (p. ej., obtener lecturas por estación). `OpenMeteoProvider` es una implementación que usa un `WeatherClient`.
- Presentación (MVP): `WeatherMapPresenter` media entre la vista (`WeatherMapWindow`) y los servicios/modelo (`WeatherService`). Procesa eventos, coordina cargas y actualiza la UI.
- Overlays (Strategy): `WeatherOverlay` define el contrato de visualización. `TemperatureOverlayPanel` y `HumidityOverlayPanel` implementan la representación de datos sobre el mapa.
- Flyweight: `MarkerStyleFactory` y `WeatherFlyweightFactory` comparten estilos de marcadores/partículas (cuerpo, borde, grosor) para optimizar memoria y rendimiento.
- Event Bus: `EventBus` permite comunicación desacoplada por eventos (estaciones cargadas, snapshot actualizado).

## Qué consigue
- Mantenibilidad: contratos claros (`WeatherClient`, `WeatherProvider`, `WeatherOverlay`) y separación de responsabilidades (MVP).
- Rendimiento: caché en el cliente de datos y reutilización de estilos via Flyweight.
- Extensibilidad: añadir nuevos proveedores u overlays sin tocar el resto del sistema.
- UX coherente: un gestor de overlays garantiza exclusividad de la capa activa y una barra temporal global no se reinicia al cambiar de estación.

## Nota sobre `WeatherFlyweightFactory`
`WeatherFlyweightFactory` agrupa estilos de partículas por velocidad y dirección del viento.
- Velocidad: se clasifica en "buckets" (rangos) para mapear el grosor del borde.
- Dirección: se normaliza a 0–360° y se agrupa en 8 sectores (N, NE, E, SE, S, SW, W, NW). Esta discretización reduce el número de estilos distintos.
- Beneficio: menos objetos en memoria y render más eficiente; estilos consistentes según condiciones de viento. Ver [JavaMid/JavaMid/src/main/java/com/javamid/flyweight/WeatherFlyweightFactory.java](JavaMid/JavaMid/src/main/java/com/javamid/flyweight/WeatherFlyweightFactory.java).

## Cálculo de RAM usada y métricas
La aplicación recopila métricas de rendimiento, incluyendo uso de memoria de la JVM (heap y non-heap). Se emplea `MemoryMXBean` y se actualiza por frame desde `PerformanceMetrics`.

### Fórmulas básicas
- Memoria usada (heap): `used = heap.getUsed()`.
- Memoria comprometida (heap): `committed = heap.getCommitted()`.
- Memoria máxima (heap): `max = heap.getMax()` (puede ser `-1` si no aplica).
- Non-heap usada: `usedNonHeap = nonHeap.getUsed()`.
- Conversión a MB: `MB = bytes / (1024 * 1024)`.

### Ejemplo de código (extracto)
```java
MemoryMXBean mxBean = ManagementFactory.getMemoryMXBean();
MemoryUsage heap = mxBean.getHeapMemoryUsage();
MemoryUsage nonHeap = mxBean.getNonHeapMemoryUsage();

long usedHeapBytes = heap.getUsed();
long committedHeapBytes = heap.getCommitted();
long maxHeapBytes = heap.getMax();
long usedNonHeapBytes = nonHeap.getUsed();

double usedHeapMb = usedHeapBytes / (1024.0 * 1024.0);
double committedHeapMb = committedHeapBytes / (1024.0 * 1024.0);
double maxHeapMb = maxHeapBytes > 0 ? maxHeapBytes / (1024.0 * 1024.0) : 0;
double usedNonHeapMb = usedNonHeapBytes / (1024.0 * 1024.0);
```

### Reporte integrado
`PerformanceMetrics` emite un reporte con memoria en MB:
- Heap: `used`, `committed`, `max`
- Non-heap: `used`
Consulta el reporte desde el presenter o panel correspondiente para inspección en tiempo de ejecución.

## Enlaces útiles
- Arquitectura y patrones: [JavaMid/JavaMid/doc/arquitectura-patrones.md](JavaMid/JavaMid/doc/arquitectura-patrones.md)
- Diagramas por patrón: [JavaMid/JavaMid/doc/pattern-adapter.mmd](JavaMid/JavaMid/doc/pattern-adapter.mmd), [JavaMid/JavaMid/doc/pattern-decorator.mmd](JavaMid/JavaMid/doc/pattern-decorator.mmd), [JavaMid/JavaMid/doc/pattern-factory.mmd](JavaMid/JavaMid/doc/pattern-factory.mmd), [JavaMid/JavaMid/doc/pattern-provider.mmd](JavaMid/JavaMid/doc/pattern-provider.mmd), [JavaMid/JavaMid/doc/pattern-flyweight.mmd](JavaMid/JavaMid/doc/pattern-flyweight.mmd), [JavaMid/JavaMid/doc/pattern-overlay.mmd](JavaMid/JavaMid/doc/pattern-overlay.mmd), [JavaMid/JavaMid/doc/pattern-presenter.mmd](JavaMid/JavaMid/doc/pattern-presenter.mmd)
