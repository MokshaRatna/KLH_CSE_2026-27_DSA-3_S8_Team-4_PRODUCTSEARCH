#!/bin/sh
# Mac / Linux launcher (Windows: use run.bat)
mkdir -p out && javac -d out src/*.java && java -cp out Main
