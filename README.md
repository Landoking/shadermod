# Shader Control — Minecraft 26.2 Fabric

Client-side shader editor for specially supported `.fsh` files in the currently loaded resource packs.

## Supported shader controls

The first version automatically exposes top-level GLSL declarations such as:

```glsl
const float BLOOM_STRENGTH = 2.0;
const float BLOOM_SPREAD = 8.0;
```

Optional range metadata can be added immediately above a declaration:

```glsl
// @sc-slider BLOOM_STRENGTH Bloom 0 20
const float BLOOM_STRENGTH = 2.0;
```

## In game

- Default key: **F8**
- The key is rebindable under **Options → Controls → Shader Control**.
- The menu scans active resource packs for `.fsh` files containing editable `const float` controls.
- **Override ON** keeps edited shader text as a client-side override instead of modifying the original resource-pack ZIP.
- Edited values are stored in `.minecraft/config/shader_control.json`.

## Build

Minecraft 26.2 uses official Mojang names with Fabric Loom 1.17 and Java 25. Run `gradle wrapper --gradle-version 9.5.1` once if the wrapper is not present, then `./gradlew build`.

The build environment used here cannot download Gradle/Minecraft artifacts, so this delivery is the complete source project rather than a precompiled JAR.

## Build on GitHub

This project includes a GitHub Actions workflow at `.github/workflows/build.yml`.

1. Upload the contents of this project to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build Shader Control**.
4. Click **Run workflow** for a manual build, or push a commit to build automatically.
5. When the build finishes, open the workflow run and download the **shader-control-26.2** artifact.
6. The artifact contains the built JAR from `build/libs/`.

The workflow uses Java 25 and Gradle 9.5.1 for the Minecraft 26.2 toolchain.
