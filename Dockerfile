FROM eclipse-temurin:25-jre

ARG MINECRAFT_VERSION
ARG LOADER_VERSION
ARG FABRIC_API_VERSION

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      python3 \
      curl \
      xvfb \
      libgl1 libglx-mesa0 libgl1-mesa-dri \
      libx11-6 libxext6 libxrandr2 libxinerama1 libxcursor1 libxi6 libxxf86vm1 libxtst6 \
      libopenal1 \
      ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home --uid 10001 bot \
 && mkdir -p /mc /data /tmp/.X11-unix \
 && chmod 1777 /tmp/.X11-unix \
 && chown bot:bot /mc /data

ENV LIBGL_ALWAYS_SOFTWARE=1 \
    MESA_LOADER_DRIVER_OVERRIDE=llvmpipe \
    GALLIUM_DRIVER=llvmpipe \
    MC_ASSETS_DIR=/mc \
    BOT_WORK_DIR=/data \
    BOT_FABRIC_MINECRAFT=${MINECRAFT_VERSION} \
    BOT_FABRIC_LOADER=${LOADER_VERSION}

COPY docker/fetch-minecraft.py docker/entrypoint.sh /opt/bot-fabric/
COPY dist/ /opt/bot-fabric/mods/

# Loom puts Fabric API on the classpath in a dev run; a real launch wants it as a mod. It is
# Apache-2.0, so unlike the client jar it can live in the image.
RUN test -n "${FABRIC_API_VERSION}" || { echo "FABRIC_API_VERSION build arg is required" >&2; exit 1; } \
 && curl -fsSL --retry 3 --connect-timeout 20 --max-time 300 \
      -o "/opt/bot-fabric/mods/fabric-api-${FABRIC_API_VERSION}.jar" \
      "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar"

# Numeric, not a name. Kubernetes refuses to start a container under runAsNonRoot when the
# image's user is a name it cannot resolve, and the pod sits in CreateContainerConfigError
# saying so -- which is where an operator-built bot pod ended up the first time one ran.
USER 10001
WORKDIR /data
ENTRYPOINT ["/opt/bot-fabric/entrypoint.sh"]
