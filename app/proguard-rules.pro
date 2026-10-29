# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# libxposed (modern Xposed API) — the module entry is discovered by NAME from
# META-INF/xposed/java_init.list and instantiated reflectively by the framework,
# so R8 must not delete it or strip its lifecycle callbacks.
# ---------------------------------------------------------------------------

# Keep every XposedModule subclass, its constructor, and all public callbacks
# (onModuleLoaded / onPackageLoaded / onPackageReady / onSystemServerStarting).
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public *;
}

# Keep hook-callback annotations and the methods they mark.
-keepattributes RuntimeVisibleAnnotations
-keep,allowoptimization,allowobfuscation @io.github.libxposed.api.annotations.* class * {
    @io.github.libxposed.api.annotations.BeforeInvocation <methods>;
    @io.github.libxposed.api.annotations.AfterInvocation <methods>;
}

# If R8 obfuscates the entry class name, rewrite the name inside java_init.list
# so the file keeps pointing at the (renamed) class.
-adaptresourcefilecontents META-INF/xposed/java_init.list

# ---------------------------------------------------------------------------
# Gson data models — serialized/deserialized reflectively by field name, so the
# field names must not be renamed (otherwise JSON keys change and parsing breaks).
# ---------------------------------------------------------------------------
-keep class com.noobexon.xposedfakelocation.data.model.** { *; }

# GitHub contributors response, parsed with Gson in the About screen.
-keep class com.noobexon.xposedfakelocation.manager.ui.about.AboutViewModel$GithubContributor { *; }