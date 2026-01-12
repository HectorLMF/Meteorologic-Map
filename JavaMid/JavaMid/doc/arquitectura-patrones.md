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

- MVP (Model–View–Presenter):
  - `com.javamid.ui.presenter.WeatherMapPresenter` orquesta flujos de datos y UI.
  - `WeatherMapWindow` actúa como View, recibe actualizaciones e invoca al presenter.

- Observer / Event Bus (Observador):
  - `com.javamid.util.EventBus` desacopla emisores y consumidores (p.ej., estaciones cargadas, snapshot actualizado).

## Diagramas por Patrón

Cada patrón cuenta con un sub-diagrama Mermaid que ilustra participantes y relaciones. Puedes abrirlos desde los enlaces para visualizar la estructura.

### Adapter (Adaptador)
- Diagrama: [JavaMid/JavaMid/doc/pattern-adapter.mmd](JavaMid/JavaMid/doc/pattern-adapter.mmd)
- Objetivo: adaptar un cliente concreto al contrato común `WeatherClient` sin cambiar el resto del sistema.
- Participantes: `WeatherClient` (interfaz), `OpenMeteoAdapter` (adaptador), `OpenMeteoClient` (cliente específico).
- Relaciones: `OpenMeteoAdapter` implementa `WeatherClient` y delega en `OpenMeteoClient`.

### Decorator (Decorador)
- Diagrama: [JavaMid/JavaMid/doc/pattern-decorator.mmd](JavaMid/JavaMid/doc/pattern-decorator.mmd)
- Objetivo: añadir responsabilidades (caché) al cliente sin modificar su interfaz ni su uso desde la UI/servicios.
- Participantes: `WeatherClient` (interfaz), `CachingWeatherClient` (decorador).
- Relaciones: `CachingWeatherClient` implementa `WeatherClient` y envuelve otro `WeatherClient` como delegado.

### Factory (Fábrica)
- Diagrama: [JavaMid/JavaMid/doc/pattern-factory.mmd](JavaMid/JavaMid/doc/pattern-factory.mmd)
- Objetivo: centralizar la construcción de objetos complejos, ocultando adaptadores y decoradores.
- Participantes: `WeatherClientFactory` (crea clientes), `WeatherClient`, `OpenMeteoAdapter`; `ProviderFactory`, `WeatherProvider`, `OpenMeteoProvider`.
- Relaciones: las fábricas dependen de las implementaciones concretas y retornan instancias del contrato (`WeatherClient`, `WeatherProvider`).

### Provider (Proveedor)
- Diagrama: [JavaMid/JavaMid/doc/pattern-provider.mmd](JavaMid/JavaMid/doc/pattern-provider.mmd)
- Objetivo: encapsular la fuente de datos meteorológicos detrás de `WeatherProvider`.
- Participantes: `WeatherProvider` (interfaz), `OpenMeteoProvider` (implementación), `WeatherClient` (colaborador interno).
- Relaciones: `OpenMeteoProvider` implementa `WeatherProvider` y utiliza un `WeatherClient` para obtener datos.

### Flyweight (Peso Ligero)
- Diagrama: [JavaMid/JavaMid/doc/pattern-flyweight.mmd](JavaMid/JavaMid/doc/pattern-flyweight.mmd)
- Objetivo: reutilizar estilos (marcadores/partículas) para reducir consumo de memoria y mejorar rendimiento en el mapa.
- Participantes: `MarkerStyle`, `MarkerStyleFactory`; `ParticleStyle`, `WeatherFlyweightFactory`.
- Relaciones: las fábricas mantienen cachés y retornan instancias compartidas por clave.

### Overlay (Capas de Visualización)
- Diagrama: [JavaMid/JavaMid/doc/pattern-overlay.mmd](JavaMid/JavaMid/doc/pattern-overlay.mmd)
- Objetivo: unificar el contrato de capas del mapa para temperatura, humedad y futuras extensiones.
- Participantes: `WeatherOverlay` (interfaz), `TemperatureOverlayPanel`, `HumidityOverlayPanel`.
- Relaciones: ambas implementaciones realizan el render según el contrato de `WeatherOverlay`.

### MVP (Model–View–Presenter)
- Diagrama: [JavaMid/JavaMid/doc/pattern-presenter.mmd](JavaMid/JavaMid/doc/pattern-presenter.mmd)
- Objetivo: desacoplar UI de lógica y datos, facilitando pruebas y extensibilidad.
- Participantes: `WeatherMapWindow` (View), `WeatherMapPresenter` (Presenter), `WeatherService`/`WeatherServiceImpl` (Model).
- Relaciones: el Presenter actualiza la View y consulta el Model; la View delega acciones al Presenter; el Model expone un contrato con una implementación.

## Empleo y finalidad de cada patrón (tono académico)

### Adapter
El patrón Adapter persigue la interoperabilidad entre interfaces incompatibles, ofreciendo un objeto intermediario que traduce operaciones de la interfaz esperada hacia el proveedor concreto. En este sistema, `OpenMeteoAdapter` implementa el contrato `WeatherClient` y reusa `OpenMeteoClient` como dependencia. La finalidad es desacoplar el consumo de datos meteorológicos de la forma específica en que una API externa (Open-Meteo) expone endpoints y modelos. Este desacoplamiento permite:
- sustituir el proveedor sin alterar capas superiores (UI, presenter, servicios),
- aislar cambios de esquema o autenticación del proveedor,
- favorecer pruebas con dobles (mocks/stubs) que implementen `WeatherClient`.
Como compromiso, introduce una capa de indirección que debe mantenerse coherente con el contrato y con el cliente subyacente.

### Decorator
El patrón Decorator añade responsabilidades transversales a un objeto sin modificar su interfaz ni su clase, mediante la composición de un delegado del mismo tipo. `CachingWeatherClient` implementa `WeatherClient` y envuelve a otro `WeatherClient` para introducir caché sobre respuestas frecuentes (p. ej., lecturas por estación/instante). Su finalidad es mejorar rendimiento y reducir latencia y consumo de cuota (rate limit) al amortizar llamadas repetitivas. Consecuencias esperables incluyen:
- mejora en tiempo de respuesta percibido en UI,
- menor presión sobre el proveedor externo,
- necesidad de políticas de expiración/invalidez para preservar la frescura de los datos.

### Factory
El patrón Factory centraliza la creación de objetos complejos, encapsulando decisiones de composición (qué adaptador/decorador aplicar) y devolviendo instancias listas para uso. `WeatherClientFactory` y `ProviderFactory` abstraen la lógica de construcción y emiten objetos que cumplen los contratos `WeatherClient` y `WeatherProvider`. La finalidad es:
- mantener un único punto de ensamblaje que facilite cambios de configuración,
- promover coherencia y evitar duplicación de “plomería” al instanciar componentes,
- mejorar testabilidad al poder sustituir la fábrica o su configuración.
Su coste es una capa adicional de abstracción que debe documentarse para que el equipo conozca el ensamblaje efectivo.

### Provider
El patrón Provider encapsula el acceso a una fuente de datos bajo un contrato estable, separando el “qué” (información requerida) del “cómo” (método de obtención). `OpenMeteoProvider` implementa `WeatherProvider` y delega en `WeatherClient` la adquisición efectiva. La finalidad es permitir que servicios y presenters operen contra un proveedor lógico, manteniendo independencia del cliente HTTP y del formato concreto de la API. El beneficio principal es el reemplazo transparente del origen de datos (p. ej., otro servicio meteorológico) y la posibilidad de orquestar política de agregación o fusión de fuentes.

### Flyweight
El patrón Flyweight optimiza el uso de memoria compartiendo objetos intrínsecos inmutables entre múltiples contextos, diferenciando estado intrínseco (compartido) del extrínseco (contextual). `MarkerStyleFactory` y `WeatherFlyweightFactory` gestionan cachés de estilos de marcadores y partículas reutilizados en el mapa. Su finalidad es:
- reducir la cantidad de objetos de estilo idénticos,
- mejorar rendimiento de renderizado al minimizar asignaciones,
- favorecer consistencia visual al reutilizar definiciones.
Como trade-off, requiere una asignación cuidadosa del estado extrínseco para evitar fugas de información entre contextos.

### Overlay (Strategy)
La interfaz `WeatherOverlay` representa una familia de algoritmos de renderizado intercambiables; las implementaciones `TemperatureOverlayPanel` y `HumidityOverlayPanel` constituyen estrategias concretas. El empleo del patrón Strategy (encapsulado aquí como “overlay”) persigue:
- cumplir el principio de abierto/cerrado al añadir nuevas capas sin modificar clientes,
- separar el “qué” de la visualización del “cómo” del cálculo y pintado,
- posibilitar pruebas unitarias por implementación.
Su finalidad práctica es extender el sistema con nuevas visualizaciones (p. ej., viento, presión) manteniendo estable el contrato común.

### MVP (Model–View–Presenter)
MVP organiza la UI separando la lógica de presentación (Presenter), la vista (View) y el modelo (Model). `WeatherMapPresenter` actúa como mediador: recibe eventos de la vista (`WeatherMapWindow`), consulta servicios/modelos (`WeatherService`) y actualiza la vista con estados derivados. La finalidad es:
- reducir acoplamiento entre UI y datos,
- mejorar testabilidad (el Presenter se prueba sin UI real),
- facilitar evolución de la vista (cambios de toolkit) sin afectar flujos de negocio.
El principal compromiso es la disciplina de mantener la lógica de presentación fuera de la vista y definir contratos claros entre roles.

### Observer / Event Bus
El patrón Observer implementado mediante `EventBus` habilita publicación–suscripción para eventos de dominio (p. ej., estaciones cargadas, snapshot actualizado). Su finalidad es desacoplar emisores de consumidores, permitiendo que múltiples componentes reaccionen a cambios sin dependencias directas. Beneficios incluyen extensibilidad (nuevos suscriptores) y reducción de acoplamiento; como coste, puede dificultar el rastreo del flujo si no se instrumenta y documenta adecuadamente.

### State + Manager
El empleo de un gestor de modo (`OverlayManager`) junto con una enumeración de estado (`OverlayMode`) controla la exclusividad de la capa activa, evitando combinaciones inconsistentes en la UI. La finalidad es mantener invariantes de la interfaz (una sola capa visible) y simplificar cambios de modo. El compromiso radica en centralizar decisiones de estado para evitar duplicación y garantizar coherencia.


## Flujos Clave

- Carga en lote de estaciones y datos:
  - Al recibir el evento de “StationsLoaded”, el presenter dispara cargas concurrentes (temperatura/humedad) para todas las estaciones visibles y actualiza overlays una vez completadas.
  - Beneficio: los círculos de temperatura/humedad aparecen simultáneamente y se reduce el “click-to-load”.

- Actualización de snapshot meteorológico:
  - Al publicar “WeatherSnapshotUpdated”, el presenter actualiza paneles (labels, brújula) y sincroniza overlays.

Consulta las interacciones en [JavaMid/JavaMid/doc/interaction.mmd](JavaMid/JavaMid/doc/interaction.mmd) y el detalle de servicios en [JavaMid/JavaMid/doc/service.mmd](JavaMid/JavaMid/doc/service.mmd).

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
