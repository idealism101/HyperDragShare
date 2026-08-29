package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PortalReflectTest {
    @Test
    fun missingClassesAreReportedInsteadOfThrown() {
        assertNull(PortalReflect.findClassOrNull("com.miui.contentextension.Absent", loader()))
        assertNotNull(PortalReflect.findClassOrNull(Child::class.java.name, loader()))
    }

    @Test
    fun privateFieldsAreFoundAlongTheSuperclassChain() {
        val child = Child()

        assertEquals("child", PortalReflect.getObjectField(child, "childSecret"))
        assertEquals("base", PortalReflect.getObjectField(child, "baseSecret"))
        assertEquals("static", PortalReflect.getStaticObjectField(Child::class.java, "STATIC_SECRET"))
    }

    @Test
    fun overloadsAreResolvedByArgumentTypeIncludingBoxedPrimitives() {
        val child = Child()

        assertEquals("int:7", PortalReflect.callMethod(child, "overloaded", 7))
        assertEquals("text:hi", PortalReflect.callMethod(child, "overloaded", "hi"))
        assertEquals("text:null", PortalReflect.callMethod(child, "overloaded", null))
        assertEquals("static:3", PortalReflect.callStaticMethod(Child::class.java, "shared", 3))
    }

    @Test
    fun theCacheIsKeyedByArgumentShapeSoTheSecondOverloadStillResolves() {
        val child = Child()

        val forInt = PortalReflect.findMethod(child.javaClass, "overloaded", arrayOf<Any?>(7))
        val forText = PortalReflect.findMethod(child.javaClass, "overloaded", arrayOf<Any?>("hi"))
        assertEquals(Integer.TYPE, forInt.parameterTypes[0])
        assertEquals(String::class.java, forText.parameterTypes[0])
        assertSame(
            forInt,
            PortalReflect.findMethod(child.javaClass, "overloaded", arrayOf<Any?>(9)),
        )
    }

    @Test
    fun exactLookupsRefuseInheritedFrameworkMethods() {
        assertEquals("base", PortalReflect.callMethod(Child(), "onlyOnBase"))
        try {
            PortalReflect.requireMethod(Child::class.java, "onlyOnBase")
            throw AssertionError("an inherited method must not resolve as a hook target")
        } catch (_: NoSuchMethodException) {
            // Hooking Base.onlyOnBase would fire for every subclass in the process.
        }
    }

    private companion object {
        private fun loader(): ClassLoader = PortalReflectTest::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()
    }

    private open class Base {
        private val baseSecret = "base"

        private fun onlyOnBase(): String = "base"
    }

    private class Child : Base() {
        private val childSecret = "child"

        private fun overloaded(value: Int): String = "int:$value"

        private fun overloaded(value: String?): String = "text:$value"

        companion object {
            @JvmStatic
            private val STATIC_SECRET = "static"

            @JvmStatic
            private fun shared(value: Int): String = "static:$value"
        }
    }
}
