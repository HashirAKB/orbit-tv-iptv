-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class dev.hashir.orbittv.**$$serializer { *; }
-keepclassmembers class dev.hashir.orbittv.** {
    *** Companion;
}
-keepclasseswithmembers class dev.hashir.orbittv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
