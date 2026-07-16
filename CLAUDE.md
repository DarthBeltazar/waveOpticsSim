# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A JavaFX desktop app that simulates monochromatic light propagation through optical elements
(lenses, mirrors, gratings, slits, free space) using the Angular Spectrum Method (FFT-based).
See README.md for the physics model (transfer function, phase-mask formulas per element).

## Commands

Windows (this repo's normal environment) uses `gradlew.bat`; POSIX shells use `./gradlew`.

- Run the GUI app: `./gradlew run` (entry point `com.optics.simulation.ui.SimulationFX`, JavaFX 22, default heap `-Xmx16g -Xms1g` set in `build.gradle`)
- Build the fat/shaded jar: `./gradlew jar` (output in `build/libs/`, manifest main-class set to `SimulationFX`, duplicate resources excluded)
- Run all tests: `./gradlew test` (JUnit 5 / Jupiter)
- Run a single test class: `./gradlew test --tests "com.optics.simulation.IntegrationTest"`
- Run a single test method: `./gradlew test --tests "com.optics.simulation.ElementTest.testSlitElementAmplitudeMask"`
- Run the headless CLI runner (batch simulation from a JSON config, no GUI): build the jar first, then
  `java -cp build/libs/<jar> com.optics.simulation.headless.HeadlessRunner <config.json> [output_dir]`
- Run the performance benchmark (per-element timing over the ASM propagation pipeline): build classes/jar first, then
  `java -cp build/libs/<jar> com.optics.simulation.benchmark.Benchmark [N] [dx] [lambda] [beamWidth]`
  (writes `benchmarkResults/benchmark_results.csv` relative to the working directory)

`HeadlessRunner` and `Benchmark` are alternate `main()` entry points, not wired into the Gradle `application`
plugin (which only knows about `SimulationFX`) — invoke them via `java -cp`, not `./gradlew run`.

CI (`.github/workflows/build.yml`, `release.yml`) builds the fat jar then packages it per-OS with `jpackage`
into a portable `OpticsSimulation` app image. Commit messages containing `[skip ci]` or `[no ci]` skip these
workflows — the existing history uses `[skip ci]` for docs-only commits.

## Architecture

### Field representation and propagation

- `ComplexField` (`src/main/java/com/optics/simulation/ComplexField.java`) is the core data structure: an
  `n x 2n` `double[][]` in interleaved format (`data[i][2j]=Re`, `data[i][2j+1]=Im`), sized directly for
  JTransforms with no extra copying. `n` must be a power of two. It exposes `applyPhaseMask` (functional,
  per-pixel phase) and `applyPrecomputedMask` (precomputed interleaved mask, parallelized over rows) — the
  two mechanisms every optical element uses to mutate the field in place.
- `FFT2D` wraps JTransforms' `DoubleFFT_2D` for forward/inverse 2D complex FFT on that same interleaved layout.
- `Propagator` is a one-method functional interface (`propagate(field, distance)`); `AngularSpectrumPropagator`
  is the only implementation, computing the ASM transfer function `H(fx,fy)`, caching it in a static
  `ConcurrentHashMap` keyed by `(n, dx, lambda, distance)`, and optionally zero-padding to `2n` before FFT to
  suppress aliasing (`AngularSpectrumPropagator.setAntiAliasingEnabled`, a static/global toggle read by both
  the GUI's checkbox and `HeadlessRunner`'s config).

### Optical elements

`model/OpticalElement` is the interface every element implements: `apply(ComplexField)` mutates the field,
`draw(GraphicsContext, x, y)` renders a schematic symbol for `SchemeRenderer`, `getDescription()` feeds the
UI list and PDF export, and `getLength()` (default 0) is the physical offset used only by `FreeSpaceElement`
for schematic distance annotations. Concrete elements (`LensElement`, `MirrorElement`, `GratingElement`,
`SlitElement`, `FreeSpaceElement`) each cache their precomputed phase/amplitude mask on first `apply()` and
reuse it if the grid size hasn't changed. `ElementFactory` is the only place elements get constructed from
raw parameters — both the GUI and `HeadlessRunner` go through it rather than calling constructors directly.
`ElementManager` is a thin wrapper over a JavaFX `ObservableList<OpticalElement>` giving the GUI element list
its ordering/CRUD operations; `SimulationEngine.run(field, elements)` just applies each element to the field
in sequence (optionally recording a `ComplexField` snapshot after every step when `debugMode` is on).

### Configuration and headless mode

`SimulationConfig` (Jackson-annotated POJO, unknown properties ignored) is the serializable form of a full
simulation setup: physical params (`lambda`, `dx`, `n`, `beamWidth`, `sourceType`) plus an ordered
`List<ElementConfig>`, where `ElementConfig` is a loose union type (`type` string + nullable fields for every
element kind) resolved by a `switch` in both `SimulationFX`'s load/save logic and
`HeadlessRunner.createElement`. `PresetConfigs` builds the five built-in presets (free space, lens focusing,
slit diffraction, grating diffraction, lens+grating) as `SimulationConfig` instances consumed by both the
GUI's "Presets" menu and available for headless use. `HeadlessRunner` is the batch path: load JSON → build
elements via `ElementFactory` → build the initial coherent field → run through `SimulationEngine` → write
intensity/phase PNGs (via `ImageUtils`/`Colormap`), a horizontal-profile CSV, and a beam-analysis text file.

### GUI (`ui/SimulationFX`)

Single ~1500-line `Application` subclass — there is no MVC split; UI construction, event wiring, and
simulation orchestration all live here. Key points if you need to touch it:
- `runSimulation()` runs the actual propagation on a background `Thread`/`Task` (never on the FX thread) and
  marshals all UI updates back via `Platform.runLater`; follow this pattern for any new long-running work.
- Three source-generation modes share the same element pipeline: single coherent field, RGB mode (three
  independent wavelengths run through the same elements, composited into an RGB image — mutually exclusive
  with partial coherence), and partially coherent mode (`PartiallyCoherentSource` generates N field
  realizations in parallel via the Schell model, each independently propagated, then intensity-averaged).
- `SchemeRenderer` draws the optical bench diagram (element symbols + distances) on the schematic `Canvas`;
  `Colormap`/`ImageUtils` handle intensity/phase → colored `BufferedImage`/JavaFX `Image` conversion, log
  scaling, and colorbar rendering; `BeamAnalyzer` computes centroid/sigma/1/e² radius/FWHM/peak/total power
  from a `ComplexField`; `PdfExporter` assembles the on-demand PDF report (params, element list, 2D images,
  profile table, beam analysis) using OpenPDF.
- `Surface3D` is a separate small JavaFX 3D view for the intensity surface, opened from the main window.

### Threading/caching conventions to preserve

- Per-pixel mask computation loops are parallelized with `IntStream.range(...).parallel()` (see
  `applyPrecomputedMask`, `multiplyByTransferFunction`, `PartiallyCoherentSource.generateFields`) — keep new
  hot loops consistent with this rather than introducing a different concurrency primitive.
  - Transfer functions and element masks are cached and keyed by the params that invalidate them (grid size
  for element masks, `(n, dx, lambda, distance)` for the ASM transfer function) — don't recompute these per
  frame/run without checking the existing cache fields first.
