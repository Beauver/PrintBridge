# Licence

Issue: #16.

## PrintBridge

PrintBridge is licensed under the **GNU Affero General Public License, version 3 or any later version** (`AGPL-3.0-or-later`). Copyright (C) 2026 Beauver.

- `LICENSE` in the repository root is the unchanged AGPL-3.0 text, so GitHub recognises it. The "or any later version" and the copyright line are in the README's licence section.
- **What it allows:** use, change, host and sell services with PrintBridge, also commercially.
- **What it asks:** whoever hands out a changed version, or runs one for others over a network, must make its source available under the same licence.
- **Source offer in the app (AGPL §13):** every page has a footer "PrintBridge · AGPL-3.0 · Source · Third-party licences".
  - *AGPL-3.0* links to the licence on gnu.org.
  - *Source* links to `SOURCE_URL` (default `https://github.com/Beauver/PrintBridge`), served by the public `GET /api/license`. Someone hosting a changed version points it at their code.
  - *Third-party licences* opens `/3rdpartylicenses.txt`, which the Angular production build writes (Angular, RxJS, Bootstrap, Three.js, …); the frontend image serves it. The development server (`npm start`) doesn't have the file.
- The backend and frontend images carry `org.opencontainers.image.licenses="AGPL-3.0-or-later"` and `org.opencontainers.image.source`.

## Contributions

[CONTRIBUTING.md](../CONTRIBUTING.md) has the contributor terms, a light contributor licence agreement:
- contributions come in under `AGPL-3.0-or-later`;
- contributors also grant the project owner the right to relicense their contribution, under other licences too, so the project can change its licence later without finding every contributor;
- commits are signed off (`Signed-off-by`, DCO).

The pull request template has a checkbox for these terms. There is no CLA bot (yet); it can be added later without changing anything for earlier contributions. The owner's own commits don't need the sign-off.

## OrcaSlicer (slicer image)

The optional slicer image contains OrcaSlicer, licensed under the GNU AGPL-3.0 (version 3, as its README states; its sources carry no "or later" headers). PrintBridge talks to it over HTTP and runs it as a separate program, so OrcaSlicer's licence doesn't decide PrintBridge's.

The image passes OrcaSlicer's licence on (see [PrintBridge-Slicer/README.md](../PrintBridge-Slicer/README.md#licence)):
- `/opt/orca/LICENSE.txt`: OrcaSlicer's licence from the pinned release tag.
- `/opt/orca/NOTICE`: version, source links and what the image changes (GUI-only files removed, merged `*_full` profiles generated).
- Labels: `org.opencontainers.image.licenses="AGPL-3.0-or-later AND AGPL-3.0-only"` and `org.opencontainers.image.source`.
- **Releases:** the release workflow attaches `OrcaSlicer-<version>-source.tar.gz` (the tag's source archive, version taken from the Dockerfile) to every GitHub release.
- **Settings page:** the Server slicing card says "Slicing by OrcaSlicer <version> (AGPL-3.0) · source", linking to the tag the slicer reports.

The Debian packages in the image keep their own copyright files under `/usr/share/doc`.

This is how the project applies the licences, not legal advice.
