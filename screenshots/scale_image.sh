#!/bin/bash

INPUT="$1"

if [ -z "$INPUT" ]; then
  echo "Usage: Datei auf dieses Script ziehen"
  exit 1
fi

# Dateiname und Extension trennen
BASENAME=$(basename "$INPUT")
NAME="${BASENAME%.*}"
EXT="${BASENAME##*.}"
DIR=$(dirname "$INPUT")

OUTPUT="${DIR}/${NAME}_1200x760.${EXT}"

sips -z 760 1200 "$INPUT" --out "$OUTPUT"

echo "Gespeichert als: $OUTPUT"
