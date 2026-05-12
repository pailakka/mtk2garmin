Downloads current full-country MML GeoPackage deliveries through the MML OGC
Processes API and stores them in the paths used by `mapcreator/docker-compose.yml`.

Create a local `.env` from `.env.example` and set `MML_API_KEY`. The real `.env`
is intentionally ignored and is passed into the Docker container with Compose.
