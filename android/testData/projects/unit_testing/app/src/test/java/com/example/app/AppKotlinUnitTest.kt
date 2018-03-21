package com.example.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.SyncResult
import android.content.SyncStats
import android.os.AsyncTask
import android.os.Debug
import android.os.PowerManager
import android.util.ArrayMap
import com.example.javalib.JavaLibJavaClass
import com.example.javalib.JavaLibKotlinClass
import com.example.util_lib.UtilLibJavaClass
import com.example.util_lib.UtilLibKotlinClass
import org.apache.commons.logging.LogFactory
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.io.File
import java.nio.charset.StandardCharsets

class AppKotlinUnitTest {
    @Test
    fun referenceProductionCode() {
        assertEquals("AppJavaClass", AppJavaClass().name)
    }

    @Test
    fun referenceProductionKotlinCode() {
        assertEquals("AppKotlinClass", AppKotlinClass().name)
    }

    @Test
    fun referenceLibraryCode() {
        assertEquals("UtilLibJavaClass", UtilLibJavaClass().name)
    }

    @Test
    fun referenceLibraryKotlinCode() {
        assertEquals("UtilLibKotlinClass", UtilLibKotlinClass().name)
    }

    @Test
    fun referenceJavaLibJavaClass() {
        assertEquals("JavaLibJavaClass", JavaLibJavaClass().name)
    }

    @Test
    fun referenceJavaLibKotlinClass() {
        assertEquals("JavaLibKotlinClass", JavaLibKotlinClass().name)
    }

    @Test
    fun mockFinalMethod() {
        val activity = mock(Activity::class.java)
        val app = mock(Application::class.java)
        `when`(activity.application).thenReturn(app)

        assertSame(app, activity.application)

        verify(activity).application
        verifyNoMoreInteractions(activity)
    }

    @Test
    @SuppressLint("MissingPermission")
    fun mockFinalClass() {
        val adapter = mock(BluetoothAdapter::class.java)
        `when`(adapter.isEnabled).thenReturn(true)

        assertTrue(adapter.isEnabled)

        verify(adapter).isEnabled
        verifyNoMoreInteractions(adapter)
    }

    @Test
    @Throws(Exception::class)
    fun mockInnerClass() {
        val wakeLock = mock(PowerManager.WakeLock::class.java)
        `when`(wakeLock.isHeld).thenReturn(true)
        assertTrue(wakeLock.isHeld)
    }

    @Test
    @Throws(Exception::class)
    fun aarDependencies() {
        val deferred = org.jdeferred.impl.DeferredObject<Int, Int, Int>()
        val promise = deferred.promise()
        deferred.resolve(42)
        assertTrue(promise.isResolved)
    }

    @Test
    fun exceptions() {
        try {
            val map = ArrayMap<String, String>()
            map.isEmpty()
            fail()
        } catch (e: RuntimeException) {
            assertEquals(RuntimeException::class.java, e.javaClass)
            assertTrue(e.message!!.contains("isEmpty"))
            assertTrue(e.message!!.contains("not mocked"))
            assertTrue(e.message!!.contains("androidstudio/not-mocked"))
        }

        try {
            @Suppress("DEPRECATION")
            Debug.getThreadAllocCount()
            fail()
        } catch (e: RuntimeException) {
            assertEquals(RuntimeException::class.java, e.javaClass)
            assertTrue(e.message!!.contains("getThreadAllocCount"))
            assertTrue(e.message!!.contains("not mocked"))
            assertTrue(e.message!!.contains("androidstudio/not-mocked"))
        }

    }

    @Test
    fun enums() {
        assertNotNull(AsyncTask.Status.RUNNING)
        assertNotEquals(AsyncTask.Status.RUNNING, AsyncTask.Status.FINISHED)

        assertEquals(AsyncTask.Status.FINISHED, AsyncTask.Status.valueOf("FINISHED"))
        assertEquals(1, AsyncTask.Status.PENDING.ordinal.toLong())
        assertEquals("RUNNING", AsyncTask.Status.RUNNING.name)

        assertEquals(AsyncTask.Status.RUNNING, AsyncTask.Status.valueOf("RUNNING"))

        val values = AsyncTask.Status.values()
        assertEquals(3, values.size.toLong())
        assertEquals(AsyncTask.Status.FINISHED, values[0])
        assertEquals(AsyncTask.Status.PENDING, values[1])
        assertEquals(AsyncTask.Status.RUNNING, values[2])
    }

    @Test
    fun instanceFields() {
        val result = mock(SyncResult::class.java)
        val statsField = result.javaClass.getField("stats")
        val syncStats = mock(SyncStats::class.java)
        statsField.set(result, syncStats)

        syncStats.numDeletes = 42
        assertEquals(42, result.stats.numDeletes)
    }

    @Test
    fun javaResourcesOnClasspath() {
        val url = javaClass.classLoader.getResource("app_test_resource_file.txt")
        assertNotNull(url)

        val stream = javaClass.classLoader.getResourceAsStream("app_test_resource_file.txt")
        assertNotNull(stream)
        val s = String(stream.readBytes(), StandardCharsets.UTF_8).trim()
        assertEquals("app test", s)
    }

    @Test
    fun prodJavaResourcesOnClasspath() {
        val url = javaClass.classLoader.getResource("app_resource_file.txt")
        assertNotNull(url)

        val stream = javaClass.classLoader.getResourceAsStream("app_resource_file.txt")
        assertNotNull(stream)
        val s = String(stream.readBytes(), StandardCharsets.UTF_8).trim()
        assertEquals("app", s)
    }

    @Test
    fun libJavaResourcesOnClasspath() {
        val url = javaClass.classLoader.getResource("util_resource_file.txt")
        assertNotNull(url)

        val stream = javaClass.classLoader.getResourceAsStream("util_resource_file.txt")
        assertNotNull(stream)
        val s = String(stream.readBytes(), StandardCharsets.UTF_8).trim()
        assertEquals("util", s)
    }

    @Test
    fun javaLibJavaResourcesOnClasspath() {
        val url = javaClass.classLoader.getResource("javalib_resource_file.txt")
        assertNotNull(url)

        val stream = javaClass.classLoader.getResourceAsStream("javalib_resource_file.txt")
        assertNotNull(stream)
        val s = String(stream.readBytes(), StandardCharsets.UTF_8).trim()
        assertEquals("javalib", s)
    }

    @Test
    fun prodRClass() {
        val id = R.string.app_name

        assertTrue(id > 0)
    }

    @Test
    fun commonsLogging() {
        val log = LogFactory.getLog(javaClass)
        log.info("I can use commons-logging!")
    }

    @Test
    fun workingDir() {
      assertTrue(File("").absolutePath.endsWith("app"))
    }

    @Test
    fun assertions() {
        try {
            assert(false)
            fail("assertions disabled");
        } catch (e: AssertionError) {
            // expected
        }
    }
}
