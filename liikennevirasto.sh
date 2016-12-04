#!/bin/bash
wget -O geodata/syvyyskayrat.zip "https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs?request=GetFeature&typeName=rajoitettu:syvyyskayra_v&outputFormat=shape-zip"
wget -O geodata/syvyyspiste_p.zip "https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs?request=GetFeature&typeName=rajoitettu:syvyyspiste_p&outputFormat=shape-zip"
