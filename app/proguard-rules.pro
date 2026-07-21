# ============================================================================
# Guardian — R8 keep rules (full mode: android.enableR8.fullMode=true)
#
# Full mode is more aggressive than the AGP default: it does not assume default
# constructors survive, strips more attributes, and treats reflection-accessed
# members as unused unless explicitly kept. The :app module is the only minified
# module, so every library's bytecode is shrunk here — all keep rules live here.
#
# Libraries that ship their own consumer rules (Room, OkHttp, Okio, Firebase,
# kotlinx-coroutines, kotlinx-serialization, WorkManager, AndroidX) are
# intentionally NOT repeated below. kotlinx-serialization's bundled rules are
# full-mode-complete; duplicating them here only widened the keep radius.
#
# Verified with the R8 configuration analyzer
# (-Dcom.android.tools.r8.dumpkeepradiustodirectory). Impact figures in the
# comments are kept/live items attributable to that rule.
# ============================================================================

# ---- Crash-deobfuscation attributes ----------------------------------------
# Kotzilla uploads the mapping file; keep line numbers, hide original source name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Reflection / generics attributes --------------------------------------
# kotlinx.serialization reads generic signatures and annotations at runtime;
# full mode strips these unless kept.
-keepattributes Signature,InnerClasses,EnumConstantName
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ============================================================================
# kotlinx.serialization
#
# Only ONE rule is kept here. Everything else this file used to declare was
# measurably redundant against kotlinx-serialization's own consumer rules:
#
#   * @Serializable Companion field keep    -> duplicate of the bundled
#     "-keepclassmembers @kotlinx.serialization.Serializable class **
#      { static ** Companion; }" (analyzer reported mutual subsumption)
#   * @Serializable object INSTANCE+serializer -> duplicate of the bundled
#     INSTANCE rule (mutual subsumption)
#   * -keepclassmembers class com.shdev.guardian.** { ...serializer(...); }
#     -> subsumed by the -if rule below
#   * -keepclassmembers enum com.shdev.guardian.** { values/valueOf }
#     -> subsumed by "-keepclassmembers enum *" from proguard-android-optimize.txt
#   * -keepclassmembers class **$$serializer { descriptor, childSerializers,
#     typeParametersSerializers, serialize, deserialize }  (467 items)
#     -> the bundled rule keeps `descriptor`; the rest are GeneratedSerializer
#        overrides reached through normal interface dispatch
#   * -keep,includedescriptorclasses class com.shdev.guardian.**$$serializer { *; }
#     (356 items) -> generated serializers are reachable from the companion's
#        serializer(); the bundled "-if @Serializable class ** -keep,allowshrinking"
#        rule covers the annotated classes themselves
# ============================================================================

# Keep the synthetic serializer() on companions of @Serializable classes. Retained
# because full mode strips it across module boundaries in some configurations, and
# it is the classic "Serializer for class X not found" failure. 100 items.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# Ktor client (OkHttp engine, content-negotiation, auth, logging)
# ============================================================================
# Ktor uses kotlinx.atomicfu volatile fields via atomic field updaters. Ktor's own
# consumer rule is -keepclassmembernames, which prevents RENAMING but still allows
# the field to be shrunk away; a field updater needs it to exist as well. 18 items.
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
# SLF4J is an optional Ktor logging dependency; silence missing-class warnings.
-dontwarn org.slf4j.**

# ============================================================================
# Navigation3 — route keys are @Serializable NavKey objects (covered by the
# serialization rules above). No extra rules needed for Koin (constructor DI,
# no name-based reflection), Compose, or the manifest-declared receivers/service
# (AGP keeps manifest-referenced classes automatically).
# ============================================================================
