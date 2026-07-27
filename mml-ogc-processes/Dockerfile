# Keep the data-update image on the same maintained GDAL release as the shared
# mtk2garmin tooling image.
ARG GDAL_BASE_IMAGE=ghcr.io/osgeo/gdal:ubuntu-full-3.13.1
FROM ${GDAL_BASE_IMAGE}

# The installer requires curl (and certificates) to download the release archive
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
    && rm -rf /var/lib/apt/lists/*

# Download the latest installer
ADD https://astral.sh/uv/install.sh /uv-installer.sh

# Run the installer then remove it
RUN sh /uv-installer.sh && rm /uv-installer.sh

# Ensure the installed binary is on the `PATH`
ENV PATH="/root/.local/bin/:$PATH"

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Install dependencies using uv
RUN uv sync

# Set environment variables (optional)
# ENV PYTHONUNBUFFERED=1

# Default command
CMD ["/bin/bash", "./load_and_convert.sh"]
