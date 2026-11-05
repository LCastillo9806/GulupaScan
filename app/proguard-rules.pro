# Reglas de ProGuard/R8 (minify desactivado por ahora).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class co.edu.unal.gulupascan.**$$serializer { *; }
-keepclassmembers class co.edu.unal.gulupascan.** { *** Companion; }
