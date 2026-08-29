# The libxposed entry point is loaded by name from META-INF/xposed/java_init.list.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# cppjieba registers these methods by their Java class and method names in JNI_OnLoad.
-keep class com.leaf.hyperdragshare.codex.TextSegmenter {
    native <methods>;
}
