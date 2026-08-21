# Architecture

## Layering

The code base is a classic layered MVC. The seams below are the most valuable
property of this code base — they are what makes headless testing possible at
all. **Do not erode them** (e.g. by letting controllers call Swing components
directly "just this once").

```
model/            pure domain objects, zero UI imports
  ↑               (observable via PropertyChangeSupport / CollectionChangeSupport)
viewcontroller/   controllers + view INTERFACES (no implementations)
  ↑
swing/, j3d/      view implementations (Swing plan/panels, Java 3D scene)
io/               persistence: legacy binary .sh3d + XML
plugin/           plugin API (small, currently untested)
tools/            misc utilities (URL content, OS detection, class loading)
```

Verified invariants (keep them true):

- `src/com/eteks/sweethome3d/model/` contains **no** `javax.swing` or
  `javax.media.j3d` references.
- No class in `viewcontroller/` references `swing/` or `j3d/` implementation
  packages; views are injected interfaces.

## Key files

| File | LOC | Role |
|---|---|---|
| `viewcontroller/PlanController.java` | ~15,900 | All 2D interaction modes as a state machine |
| `swing/PlanComponent.java` | ~7,700 | Plan rendering + hit testing + paint caches |
| `swing/HomePane.java` | ~6,000 | Main window, menus, actions |
| `swing/HomeComponent3D.java` | ~3,900 | 3D view |
| `model/Home.java` | ~2,300 | Central aggregate; selection, furniture, listeners |
| `io/HomeXMLHandler.java` | ~2,000 | SAX parser for the XML home format |

### PlanController — a god class that is actually a state machine

`PlanController` looks alarming but internally it is coherent: ~20 inner
`ControllerState` subclasses (`SelectionState`, `WallCreationState`,
`PieceOfFurnitureRotationState`, `PanningState`, `DragAndDropState`, ...), one
per interaction mode, plus magnetism helper classes. The size reflects genuine
CAD input-handling complexity, not spaghetti.

Consequences:

- It is the highest-risk file for regressions and the hardest to test in
  isolation.
- If splitting it up ever becomes worthwhile, the natural cut is *one file per
  interaction state*, not by layer.

### PlanComponent

Rendering pipeline: `paintComponent` → `paintContent` → per-item-type painters
(rooms → walls → furniture → polylines → dimension lines → texts → selection
outlines). Recent fork work added clip-based culling, dirty-region repaints and
several paint caches (see [Code Quality](Code-Quality.md)).

## Persistence

Two formats coexist:

1. **Binary `.sh3d`** (`DefaultHomeInputStream` / `HomeFileRecorder`) — the
   legacy Java-serialization format. Frozen; kept for reading old files.
2. **XML** (`HomeXMLHandler` + `HomeXMLExporter`) — the format new development
   should target. Note the handler is a large element-name dispatch; it has no
   round-trip test yet (see [Testing](Testing.md)).
