#!/bin/bash
set -euxo pipefail

uv run main.py maastotietokanta_korkeussuhteet_koko_suomi /mtkdata/maastotietokanta_korkeussuhteet_koko_suomi.gpkg.zip
sozip --overwrite -j /mtkdata/mtkkorkeus.zip /vsizip//mtkdata/maastotietokanta_korkeussuhteet_koko_suomi.gpkg.zip/mtkkorkeus.gpkg
rm -f /mtkdata/maastotietokanta_korkeussuhteet_koko_suomi.gpkg.zip

uv run main.py maastotietokanta_koko_suomi /mtkdata/maastotietokanta_koko_suomi.gpkg.zip
sozip --overwrite -j /mtkdata/mktmaasto.zip /vsizip//mtkdata/maastotietokanta_koko_suomi.gpkg.zip/mtkmaasto.gpkg
rm -f /mtkdata/maastotietokanta_koko_suomi.gpkg.zip

uv run main.py kiinteistorekisterikartta_vektori_koko_suomi /krkdata/kiinteistorekisterikartta.gpkg
