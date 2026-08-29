package com.leaf.hyperdragshare.codex

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection for the portal adapters. libxposed API 102 dropped XposedHelpers, so the few lookups
 * Taplus needs live here: fields and dynamic calls resolved along the superclass chain, matched by
 * name plus argument count plus assignability, and cached because these run once per gesture.
 */
internal object PortalReflect {
    private val FIELDS = ConcurrentHashMap<String, Field>()
    private val METHODS = ConcurrentHashMap<String, Method>()

    private val PRIMITIVES: Map<Class<*>, Class<*>> = mapOf(
        java.lang.Boolean::class.java to java.lang.Boolean.TYPE,
        java.lang.Byte::class.java to java.lang.Byte.TYPE,
        java.lang.Character::class.java to Character.TYPE,
        java.lang.Short::class.java to java.lang.Short.TYPE,
        java.lang.Integer::class.java to Integer.TYPE,
        java.lang.Long::class.java to java.lang.Long.TYPE,
        java.lang.Float::class.java to java.lang.Float.TYPE,
        java.lang.Double::class.java to java.lang.Double.TYPE,
    )

    fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? = try {
        Class.forName(name, false, classLoader)
    } catch (_: Throwable) {
        null
    }

    fun requireClass(name: String, classLoader: ClassLoader): Class<*> =
        Class.forName(name, false, classLoader)

    /**
     * Exact signature lookup on the declaring class itself, used to obtain the [Method] a hook is
     * installed on. Inherited framework methods are deliberately not resolved: hooking
     * `Service.onCreate` instead of the portal's own override would fire for every service in the
     * process, which is what `XposedHelpers.findMethodExact` also refused to do.
     */
    fun requireMethod(owner: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        val key = owner.name + "#" + name + "(" + parameterTypes.joinToString(",") { it.name } + ")"
        METHODS[key]?.let { return it }
        val method = owner.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        METHODS[key] = method
        return method
    }

    /** Finds the single declared method matching the name and the given arguments. */
    fun findMethod(owner: Class<*>, name: String, args: Array<out Any?>): Method {
        // The argument shape is part of the key: two overloads of the same arity resolve to
        // different methods, and a cache keyed by arity alone would hand the second call the first
        // one's method.
        val key = owner.name + "#" + name + "/" + args.joinToString(",") {
            it?.javaClass?.name ?: "null"
        }
        METHODS[key]?.let { return it }
        var current: Class<*>? = owner
        while (current != null) {
            for (method in current.declaredMethods) {
                if (method.name != name || method.parameterCount != args.size) {
                    continue
                }
                if (!matches(method.parameterTypes, args)) {
                    continue
                }
                method.isAccessible = true
                METHODS[key] = method
                return method
            }
            current = current.superclass
        }
        throw NoSuchMethodException(owner.name + "." + name + "/" + args.size)
    }

    fun getObjectField(target: Any, name: String): Any? =
        requireField(target.javaClass, name).get(target)

    fun getStaticObjectField(owner: Class<*>, name: String): Any? =
        requireField(owner, name).get(null)

    fun callMethod(target: Any, name: String, vararg args: Any?): Any? =
        findMethod(target.javaClass, name, args).invoke(target, *args)

    fun callStaticMethod(owner: Class<*>, name: String, vararg args: Any?): Any? =
        findMethod(owner, name, args).invoke(null, *args)

    private fun requireField(owner: Class<*>, name: String): Field {
        val key = owner.name + "#" + name
        FIELDS[key]?.let { return it }
        var current: Class<*>? = owner
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                FIELDS[key] = field
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException(owner.name + "." + name)
    }

    private fun matches(parameterTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        for (index in parameterTypes.indices) {
            val expected = parameterTypes[index]
            val argument = args[index]
            if (argument == null) {
                if (expected.isPrimitive) {
                    return false
                }
                continue
            }
            val actual = argument.javaClass
            if (expected.isAssignableFrom(actual)) {
                continue
            }
            // A boxed argument still matches the primitive parameter it is unboxed into.
            if (expected.isPrimitive && PRIMITIVES[actual] == expected) {
                continue
            }
            return false
        }
        return true
    }
}
