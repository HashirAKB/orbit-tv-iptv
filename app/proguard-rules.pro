# Orbit TV keeps nothing by hand except what reflection needs. Compose, Media3,
# OkHttp, Coil and DataStore all ship their own consumer rules.

# ---- kotlinx.serialization -------------------------------------------------
# The generated $$serializer classes are reached reflectively via Companion, so
# R8 (full mode by default in AGP 8) cannot see the link. These are the rules
# recommended by the kotlinx.serialization project.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.hashir.orbittv.data.**$$serializer { *; }
