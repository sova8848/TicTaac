#!/bin/sh

TT_VERSION="latest"
THREAT_MODEL_DIR=$HOME/threat-model

# Make sure we are using the latest version
docker pull rusakovichma/tic-taac:$TT_VERSION

docker run --rm \
    -e user=$USER \
    -u $(id -u ${USER}):$(id -g ${USER}) \
    --volume $THREAT_MODEL_DIR:/threat-model:z \
    --volume $(pwd)/report:/report:z \
    rusakovichma/tic-taac:$TT_VERSION \
    --threatModel /threat-model \
    --outFormat html \
    --out /report
    # Set mitigation strategy for the corresponding threats
    # see https://github.com/rusakovichma/TicTaaC/blob/master/expl/mitigations.yml 
    # --mitigations /threat-model/mitigations.yml 
    # or set the folder where scan the mitigations files: --mitigations /mitigations