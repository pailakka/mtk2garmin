# Use official Python image as base
FROM ghcr.io/osgeo/gdal:ubuntu-full-latest-amd64

# The installer requires curl (and certificates) to download the release archive
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates

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
