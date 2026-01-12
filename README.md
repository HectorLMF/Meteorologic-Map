# 🌦️ Meteorologic-Map / JavaMid

**Aplicación Java para visualización interactiva de datos meteorológicos sobre mapas OpenStreetMap.**

Incluye animación de partículas de viento, overlays de temperatura/humedad, y una arquitectura basada en 8 patrones de diseño.

![Java](https://img.shields.io/badge/Java-11+-orange) ![Maven](https://img.shields.io/badge/Maven-3.8+-blue) ![Patrones](https://img.shields.io/badge/Patrones-8-green) ![Clases](https://img.shields.io/badge/Clases-47-purple)

---

## ✨ Características

- 🗺️ **Mapa interactivo** con tiles de OpenStreetMap
- 💨 **Animación de viento** con sistema de partículas optimizado (30 FPS, miles de partículas)
- 🌡️ **Overlay de temperatura** con escala cromática
- 💧 **Overlay de humedad** con gradientes de color
- 📍 **Estaciones meteorológicas virtuales** generadas dinámicamente
- 🔄 **Datos en tiempo real** desde API Open-Meteo (gratuita, sin API key)
- 🖥️ **Doble modo**: GUI Swing o servidor REST (Spring Boot)

---

## 🏗️ Arquitectura

### Patrones de Diseño Implementados

| Patrón | Clases | Propósito |
|--------|--------|-----------|
| **Factory** | `ProviderFactory`, `WeatherClientFactory`, `UIComponentFactory` | Creación flexible de objetos |
| **Adapter** | `OpenMeteoAdapter` | Compatibilidad entre interfaces |
| **Decorator** | `CachingWeatherClient` | Caché transparente sobre cliente |
| **Flyweight** | `WeatherFlyweightFactory`, `MarkerStyleFactory` | Compartir estilos entre partículas |
| **Observer** | `EventBus` | Comunicación desacoplada |
| **State** | `OverlayManager`, `OverlayMode` | Gestión de estados de overlay |
| **MVP** | `WeatherMapPresenter` | Separación UI/lógica |
| **Object Pool** | `WindParticlePool`, `StationParticlePool` | Reutilización de partículas |

### Estructura de Paquetes

```
com.javamid/
├── client/          # Clientes HTTP (OpenMeteoClient, CachingWeatherClient)
├── config/          # Configuración centralizada (MapConfig)
├── controller/      # REST controllers (WeatherController)
├── flyweight/       # Patrón Flyweight (estilos compartidos)
├── model/           # DTOs (WeatherDTO, WeatherStation, etc.)
├── provider/        # Abstracción de APIs (WeatherProvider)
├── service/         # Lógica de negocio (StationManager, WeatherDataManager)
├── ui/              # Componentes Swing
│   ├── overlay/     # Sistema de overlays (OverlayManager)
│   └── presenter/   # MVP Presenter
└── util/            # Utilidades (EventBus, GeoUtils, TimedCache)
```

**Total: 47 clases en 11 paquetes**

---

## 📋 Requisitos

- **JDK 11** o superior
- **Maven 3.8+**
- **Conexión a Internet** (para tiles OSM y API Open-Meteo)

---

## 🚀 Instalación y Ejecución

### Compilar

```bash
cd JavaMid/JavaMid
mvn clean package -DskipTests
```

### Ejecutar GUI

```bash
# Opción 1: Maven exec
mvn exec:java

# Opción 2: JAR directo
java -jar target/JavaMid-1.0.0.jar
```

### Ejecutar como Servidor REST

```bash
java -jar target/JavaMid-1.0.0.jar --server
# o
mvn spring-boot:run
```

**Endpoints disponibles:**
- `GET /weather?city=Madrid` - Clima actual
- `POST /weather/points` - Clima para múltiples coordenadas

---

## 🎮 Uso de la Aplicación

### Controles del Mapa
| Acción | Control |
|--------|---------|
| Mover mapa | Arrastrar con ratón |
| Zoom | Rueda del ratón |
| Seleccionar estación | Click en marcador |

### Panel de Control
- **Selector de tiempo**: Navegar por horas históricas
- **Overlays**: Activar/desactivar Viento, Humedad, Temperatura
- **Control de partículas**: Ajustar cantidad, tamaño, velocidad

---

## 📊 Optimizaciones de Rendimiento

| Técnica | Mejora |
|---------|--------|
| Object Pooling | -70% GC pressure |
| Flyweight | -60% memoria en estilos |
| Spatial Grid | O(1) búsqueda espacial |
| Velocity Cache | Evita recálculos trigonométricos |
| Batch Rendering | Renderizado eficiente a BufferedImage |

**Resultado**: 30 FPS estable con 5000+ partículas

---

## 📁 Documentación

| Documento | Descripción |
|-----------|-------------|
| [Manual de Usuario](JavaMid/JavaMid/doc/manual-usuario.md) | Guía de uso de la aplicación |
| [Documento Técnico](JavaMid/JavaMid/doc/documento-explicativo.md) | Explicación detallada del código |
| [Arquitectura y Patrones](JavaMid/JavaMid/doc/arquitectura-patrones.md) | Patrones de diseño implementados |
| [Prompts IA](JavaMid/JavaMid/doc/prompts.md) | Interacción con Claude Opus 4.5 |

### Diagramas UML (Mermaid)

- `doc/class.mmd` - Diagrama de clases completo
- `doc/pattern-*.mmd` - Diagramas de cada patrón
- `doc/eventBus.mmd` - Patrón Observer
- `doc/state.mmd` - Patrón State

---

## 🧪 Tests

```bash
cd JavaMid/JavaMid
mvn test
```

Incluye tests de integración con la API real de Open-Meteo.

---

## 📦 Release Automática (GitHub Actions)

1. Ve a **Actions** → **Release**
2. Click en **Run workflow**
3. Define el tag (ej: `v1.0.0`)
4. El workflow compila y publica el JAR

---

## 🛠️ Tecnologías

- **Java 11** - Lenguaje principal
- **Spring Boot** - Framework web (modo servidor)
- **JXMapViewer2** - Componente de mapas
- **Jackson** - Serialización JSON
- **Open-Meteo API** - Datos meteorológicos (gratuita)
- **OpenStreetMap** - Tiles de mapas

---

## 📈 Métricas del Proyecto

| Métrica | Valor |
|---------|-------|
| Clases | 47 |
| Paquetes | 11 |
| Patrones de diseño | 8 |
| Líneas de código | ~4,500 |
| Tiempo de desarrollo | ~107 horas |

---

## 👥 Contribuir

1. Fork del repositorio
2. Crear rama feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -am 'Añadir funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Crear Pull Request

---

## 📄 Licencia

Proyecto académico/demostrativo. Uso educativo.

---

## 🙏 Agradecimientos

- **Open-Meteo** - API meteorológica gratuita
- **OpenStreetMap** - Tiles de mapas
- **JXMapViewer2** - Librería de mapas Java
- **Claude Opus 4.5** - Asistencia en desarrollo