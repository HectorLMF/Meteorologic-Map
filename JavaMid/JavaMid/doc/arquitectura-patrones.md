# Arquitectura y Patrones Implementados

Este documento resume los cambios de arquitectura realizados y los patrones de diseño aplicados para mejorar mantenibilidad, rendimiento y extensibilidad del proyecto.

## Resumen de la Nueva Arquitectura

- UI desacoplada mediante un `Presenter` que orquesta datos y visualización.
- Overlays (temperatura, humedad) con una interfaz común y un gestor que garantiza exclusividad.
- Cliente meteorológico unificado con adaptadores y caché para rendimiento.
- Ejecución asíncrona centralizada y bus de eventos ligero para desacoplar componentes.

Consulta el esquema general en [JavaMid/JavaMid/doc/architecture.md](JavaMid/JavaMid/doc/architecture.md) y las secuencias en [JavaMid/JavaMid/doc/sequence.mmd](JavaMid/JavaMid/doc/sequence.mmd).

## Componentes Clave

- Ventana principal: `WeatherMapWindow` — gestiona la UI y delega lógica al presenter.
- Presenter: `WeatherMapPresenter` — coordina overlays, datos y actualizaciones de UI.
- Overlays: `TemperatureOverlayPanel`, `HumidityOverlayPanel` — renderizan capas sobre el mapa.
- Gestor de overlays: `OverlayManager` — aplica el modo y activa/desactiva overlays.
- Cliente de datos: `WeatherClient` (+ `OpenMeteoAdapter`, `CachingWeatherClient`) — acceso unificado y con caché.
- Ejecución asíncrona: `AsyncExecutor` — executor compartido para tareas concurrentes.
- Bus de eventos: `EventBus` — publicación/suscripción para cambios de estado.

## Patrones de Diseño Aplicados

- Strategy (Estrategia):
  - `com.javamid.ui.overlay.WeatherOverlay` define el contrato común de las capas visuales.
  - Permite intercambiar fácilmente la implementación (temperatura, humedad, etc.).

- State + Manager (Estado con Gestor):
  - `com.javamid.ui.overlay.OverlayMode` y `com.javamid.ui.overlay.OverlayManager` garantizan exclusividad de una sola capa activa.
  - Simplifica el cambio de modo y evita estados inconsistentes en la UI.

- Factory (Fábrica):
  - `com.javamid.client.WeatherClientFactory` entrega un cliente listo (con adaptador y caché) sin exponer detalles de construcción.

- Adapter (Adaptador):
  - `com.javamid.client.OpenMeteoAdapter` adapta el cliente existente al contrato `WeatherClient`.
  - Facilita incorporar nuevas fuentes de datos sin cambiar el resto del sistema.

- Decorator (Decorador / Caché TTL):
  - `com.javamid.client.CachingWeatherClient` añade caché con tiempo de vida para reducir llamadas y mejorar latencia.

- MVP (Model–View–Presenter):
  - `com.javamid.ui.presenter.WeatherMapPresenter` orquesta flujos de datos y UI.
  - `WeatherMapWindow` actúa como View, recibe actualizaciones e invoca al presenter.

- Observer / Event Bus (Observador):
  - `com.javamid.util.EventBus` desacopla emisores y consumidores (p.ej., estaciones cargadas, snapshot actualizado).

- Concurrencia con Futures:
  - `com.javamid.service.AsyncExecutor` y `CompletableFuture` para cargas en lote y actualizaciones en EDT seguras.

## Flujos Clave

- Carga en lote de estaciones y datos:
  - Al recibir el evento de “StationsLoaded”, el presenter dispara cargas concurrentes (temperatura/humedad) para todas las estaciones visibles y actualiza overlays una vez completadas.
  - Beneficio: los círculos de temperatura/humedad aparecen simultáneamente y se reduce el “click-to-load”.

- Actualización de snapshot meteorológico:
  - Al publicar “WeatherSnapshotUpdated”, el presenter actualiza paneles (labels, brújula) y sincroniza overlays.

Consulta las interacciones en [JavaMid/JavaMid/doc/interaction.mmd](JavaMid/JavaMid/doc/interaction.mmd) y el detalle de servicios en [JavaMid/JavaMid/doc/service.mmd](JavaMid/JavaMid/doc/service.mmd).

## Cambios Principales Realizados

- Desacoplamiento de lógica de UI hacia `WeatherMapPresenter` para evitar NPEs y ordenar el flujo.
- Introducción de `WeatherOverlay` + `OverlayManager` para controlar la visibilidad y exclusividad de capas.
- Unificación del acceso a datos con `WeatherClient` + `OpenMeteoAdapter` + `CachingWeatherClient`.
- Centralización de tareas asíncronas con `AsyncExecutor` y uso de `CompletableFuture`.
- Event-driven: `EventBus` para comunicar cambios (estaciones, snapshot) sin dependencias directas.

## Impacto y Beneficios

- Mantenibilidad: responsabilidades claras (UI vs. lógica) y APIs coherentes.
- Rendimiento: cargas en lote concurrentes con caché TTL.
- Escalabilidad: agregar nuevas capas o proveedores de datos sin tocar la UI.
- Robustez: menor acoplamiento y menos errores por orden de inicialización.

## Referencias de Código

- Presenter: [JavaMid/JavaMid/src/main/java/com/javamid/ui/presenter/WeatherMapPresenter.java](JavaMid/JavaMid/src/main/java/com/javamid/ui/presenter/WeatherMapPresenter.java)
- Ventana: [JavaMid/JavaMid/src/main/java/com/javamid/ui/WeatherMapWindow.java](JavaMid/JavaMid/src/main/java/com/javamid/ui/WeatherMapWindow.java)
- Overlays: [JavaMid/JavaMid/src/main/java/com/javamid/ui/TemperatureOverlayPanel.java](JavaMid/JavaMid/src/main/java/com/javamid/ui/TemperatureOverlayPanel.java), [JavaMid/JavaMid/src/main/java/com/javamid/ui/HumidityOverlayPanel.java](JavaMid/JavaMid/src/main/java/com/javamid/ui/HumidityOverlayPanel.java)
- Contrato + Gestor: [JavaMid/JavaMid/src/main/java/com/javamid/ui/overlay/WeatherOverlay.java](JavaMid/JavaMid/src/main/java/com/javamid/ui/overlay/WeatherOverlay.java), [JavaMid/JavaMid/src/main/java/com/javamid/ui/overlay/OverlayManager.java](JavaMid/JavaMid/src/main/java/com/javamid/ui/overlay/OverlayManager.java)
- Cliente unificado: [JavaMid/JavaMid/src/main/java/com/javamid/client/WeatherClient.java](JavaMid/JavaMid/src/main/java/com/javamid/client/WeatherClient.java)
- Adaptador y caché: [JavaMid/JavaMid/src/main/java/com/javamid/client/OpenMeteoAdapter.java](JavaMid/JavaMid/src/main/java/com/javamid/client/OpenMeteoAdapter.java), [JavaMid/JavaMid/src/main/java/com/javamid/client/CachingWeatherClient.java](JavaMid/JavaMid/src/main/java/com/javamid/client/CachingWeatherClient.java)
- Fábrica: [JavaMid/JavaMid/src/main/java/com/javamid/client/WeatherClientFactory.java](JavaMid/JavaMid/src/main/java/com/javamid/client/WeatherClientFactory.java)
- Asíncrono: [JavaMid/JavaMid/src/main/java/com/javamid/service/AsyncExecutor.java](JavaMid/JavaMid/src/main/java/com/javamid/service/AsyncExecutor.java)
- Eventos: [JavaMid/JavaMid/src/main/java/com/javamid/util/EventBus.java](JavaMid/JavaMid/src/main/java/com/javamid/util/EventBus.java)

## Próximos Pasos (Opcional)

- Limitar cargas en lote a estaciones visibles para optimizar aún más.
- Configuración para seleccionar proveedor de datos desde properties.
- Estandarizar logging (niveles y formato) desde el presenter.
