# syntax=docker/dockerfile:1
# PrintBridge slicer: OrcaSlicer's command line behind a small HTTP API (slicer_service.py).
# This wrapper is PrintBridge (AGPL-3.0-or-later). OrcaSlicer is AGPL-3.0: its licence and a
# notice of what this image changes end up in /opt/orca (LICENSE.txt, NOTICE); see the README.

ARG ORCA_VERSION=2.5.0

# OrcaSlicer's system profiles as JSON, from the same release. The AppImage only ships them
# packed (.opc), which the command line can't read when we pass profiles by file.
FROM debian:trixie-slim AS profiles
ARG ORCA_VERSION
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --quiet --depth 1 --branch "v${ORCA_VERSION}" --filter=blob:none --no-checkout \
        https://github.com/OrcaSlicer/OrcaSlicer.git /src \
    && cd /src && git sparse-checkout set --no-cone 'resources/profiles/**/*.json' LICENSE.txt \
    && git checkout --quiet

# The AppImage, verified and unpacked (no FUSE inside containers).
FROM debian:trixie-slim AS orca
ARG ORCA_VERSION
ARG ORCA_SHA256=d13eb0a9cd618ed080ab6b82c64c0a60c4501aac91e88d5ac2c3b526fb141a7c
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL -o /tmp/orca.AppImage \
        "https://github.com/OrcaSlicer/OrcaSlicer/releases/download/v${ORCA_VERSION}/OrcaSlicer_Linux_AppImage_Ubuntu2404_V${ORCA_VERSION}.AppImage" \
    && echo "${ORCA_SHA256}  /tmp/orca.AppImage" | sha256sum -c - \
    && cd /tmp && chmod +x orca.AppImage && ./orca.AppImage --appimage-extract >/dev/null \
    && mv squashfs-root /opt/orca \
    && chmod -R a+rX /opt/orca \
    # Only used by the GUI: printer error docs, translations, icons, web pages, fonts, sample
    # models, calibration files and a Python package manager (about 260 MB).
    && cd /opt/orca/resources \
    && rm -rf hms i18n images web textures handy_models calib tooltip fonts tools Icon.icns

FROM debian:trixie-slim
ARG ORCA_VERSION
LABEL org.opencontainers.image.title="PrintBridge slicer" \
      org.opencontainers.image.description="OrcaSlicer ${ORCA_VERSION} behind a small HTTP API for PrintBridge" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later AND AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/Beauver/PrintBridge"
# OrcaSlicer links against GTK, WebKit and FFmpeg even when only the command line is used.
RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 libgl1 libegl1 libglu1-mesa libgtk-3-0t64 libwebkit2gtk-4.1-0 \
        libgstreamer1.0-0 libgstreamer-plugins-base1.0-0 libsecret-1-0 libsm6 libice6 libmspack0t64 \
        libavformat61 libavcodec61 libswscale8 libavutil59 \
    && rm -rf /var/lib/apt/lists/* \
    # Mesa's GPU drivers are only loaded when something renders; the command line doesn't (~165 MB).
    && rm -f /usr/lib/x86_64-linux-gnu/libgallium-*.so /usr/lib/x86_64-linux-gnu/libLLVM.so* \
    && rm -rf /usr/lib/x86_64-linux-gnu/dri
COPY --from=orca /opt/orca /opt/orca
COPY --from=profiles /src/resources/profiles /opt/profiles
COPY --from=profiles /src/LICENSE.txt /opt/orca/LICENSE.txt
COPY NOTICE /opt/orca/NOTICE
COPY slicer_service.py /app/slicer_service.py
# Merged ("full") profiles where OrcaSlicer looks for a project's presets when the printer changes.
RUN PROFILES_DIR=/opt/profiles python3 /app/slicer_service.py --write-full-profiles /opt/orca/resources/profiles \
    && sed -i "s/@ORCA_VERSION@/${ORCA_VERSION}/g" /opt/orca/NOTICE
RUN useradd --system --create-home slicer
USER slicer
WORKDIR /home/slicer
ENV ORCA_VERSION=${ORCA_VERSION} LANG=C.UTF-8 HOME=/home/slicer
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s CMD python3 -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"
CMD ["python3", "/app/slicer_service.py"]
