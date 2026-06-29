BirdNET model assets
=====================

The on-device bird-sound feature (the "Detect / Bird" button on each voice
recording in the hike-detail screen) loads a BirdNET TensorFlow Lite model from
THIS folder. The model files are NOT committed to the repo — you must add them:

    app/src/main/assets/birdnet/model.tflite      <-- BirdNET TFLite model (FP16)
    app/src/main/assets/birdnet/labels.txt         <-- one label per line

Where to get them
-----------------
From the official BirdNET-Analyzer project (Cornell Lab / Chemnitz):
    https://github.com/kahst/BirdNET-Analyzer
Use the GLOBAL 6K V2.4 model. Rename the files to exactly `model.tflite` and
`labels.txt` (or change the constants in util/BirdNetClassifier.kt).

Label format
------------
One class per line, matching the model's output order, in the form:
    Scientific name_Common Name
e.g.
    Cyanocitta cristata_Blue Jay
BirdNetClassifier splits on the first '_' to show the common name.

Contract the code assumes (verify against your model)
-----------------------------------------------------
- Input  : mono 48 kHz float PCM, one 3-second window (~144,000 samples).
           Window length is read from the model's input tensor automatically.
- Output : one logit per class; the code applies a sigmoid (toggle
           APPLY_SIGMOID in BirdNetClassifier if your export already does).
- Built-in TFLite ops only (no Select-TF-ops/Flex). If the interpreter reports
  a missing op, add `org.tensorflow:tensorflow-lite-select-tf-ops` in
  app/build.gradle.kts.

LICENSE — IMPORTANT
-------------------
BirdNET's model + labels are licensed CC BY-NC-SA 4.0 (NON-COMMERCIAL). Fine for
a free/student/research app with attribution. If WildTrail ever becomes
commercial you need a commercial license from the BirdNET team, or switch to a
permissively-licensed model (e.g. Google's "Bird Vocalization Classifier" /
Perch) — the same classifier plumbing works.
