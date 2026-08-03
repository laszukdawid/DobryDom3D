# Sweet Home 3D

This repository is an unofficial GitHub fork of the Sweet Home 3D 7.5 source
distribution.

The original project is hosted on [SourceForge](https://sourceforge.net/projects/sweethome3d/).
This repository does not claim original ownership or official project status.
It preserves the upstream source snapshot as a starting point for incremental
modernization.

## Upstream Snapshot

- Version: 7.5
- Upstream source reference: `https://svn.code.sf.net/p/sweethome3d/code/tags/V_7_5/SweetHome3D`
- Original copyright notices: Space Mushrooms, 2024
- License: GNU General Public License version 2 or later
- Third-party components: see the `THIRDPARTY-LICENSE-*.TXT` files

The original source documentation remains available in [`README.TXT`](README.TXT).

## Repository Contents

- `src/` - Sweet Home 3D application source code and resources
- `test/` - JUnit and GUI test sources and test resources
- `lib/` and `libtest/` - runtime and test dependencies
- `deploy/` - legacy deployment descriptors and optional server scripts
- `install/` - platform launcher and installer templates
- `include/` - native YafaRay integration headers

## Building

The upstream build uses Apache Ant. See [`README.TXT`](README.TXT) and
[`build.xml`](build.xml) for the documented Eclipse and Ant build procedures.

The default Ant target builds an executable JAR under `install/`.

## Fork Status

This is the initial repository staging pass. No application code or behavior was
changed here; future changes will be developed separately from the preserved
upstream snapshot.
