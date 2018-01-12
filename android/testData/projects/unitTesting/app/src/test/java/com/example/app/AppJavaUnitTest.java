package com.example.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.SyncResult;
import android.content.SyncStats;
import android.os.AsyncTask;
import android.os.Debug;
import android.os.PowerManager;
import android.util.ArrayMap;

import com.example.javalib.JavaLibJavaClass;
import com.example.javalib.JavaLibKotlinClass;
import com.example.util_lib.UtilLibJavaClass;
import com.example.util_lib.UtilLibKotlinClass;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdeferred.Deferred;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class AppJavaUnitTest {
    @Test
    public void referenceProductionCode() {
        assertEquals("AppJavaClass", new AppJavaClass().getName());
    }

    @Test
    public void referenceProductionKotlinCode() {
        assertEquals("AppKotlinClass", new AppKotlinClass().getName());
    }

    @Test
    public void referenceLibraryCode() {
        assertEquals("UtilLibJavaClass", new UtilLibJavaClass().getName());
    }

    @Test
    public void referenceLibraryKotlinCode() {
        assertEquals("UtilLibKotlinClass", new UtilLibKotlinClass().getName());
    }

    @Test
    public void referenceJavaLibJavaClass() throws Exception {
        assertEquals("JavaLibJavaClass", new JavaLibJavaClass().getName());
    }

    @Test
    public void referenceJavaLibKotlinClass() throws Exception {
        assertEquals("JavaLibKotlinClass", new JavaLibKotlinClass().getName());
    }

    @Test
    public void mockFinalMethod() {
        Activity activity = mock(Activity.class);
        Application app = mock(Application.class);
        when(activity.getApplication()).thenReturn(app);

        assertSame(app, activity.getApplication());

        verify(activity).getApplication();
        verifyNoMoreInteractions(activity);
    }

    @Test
    @SuppressLint("MissingPermission")
    public void mockFinalClass() {
        BluetoothAdapter adapter = mock(BluetoothAdapter.class);
        when(adapter.isEnabled()).thenReturn(true);

        assertTrue(adapter.isEnabled());

        verify(adapter).isEnabled();
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void mockInnerClass() throws Exception {
        PowerManager.WakeLock wakeLock = mock(PowerManager.WakeLock.class);
        when(wakeLock.isHeld()).thenReturn(true);
        assertTrue(wakeLock.isHeld());
    }

    @Test
    public void aarDependencies() throws Exception {
        Deferred<Integer, Integer, Integer> deferred = new org.jdeferred.impl.DeferredObject<>();
        org.jdeferred.Promise promise = deferred.promise();
        deferred.resolve(42);
        assertTrue(promise.isResolved());
    }

    @Test
    public void exceptions() {
        try {
            ArrayMap<String, String> map = new ArrayMap<String, String>();
            map.isEmpty();
            fail();
        } catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertTrue(e.getMessage().contains("isEmpty"));
            assertTrue(e.getMessage().contains("not mocked"));
            assertTrue(e.getMessage().contains("androidstudio/not-mocked"));
        }

        try {
            Debug.getThreadAllocCount();
            fail();
        } catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertTrue(e.getMessage().contains("getThreadAllocCount"));
            assertTrue(e.getMessage().contains("not mocked"));
            assertTrue(e.getMessage().contains("androidstudio/not-mocked"));
        }

    }

    @Test
    public void enums() throws Exception {
        assertNotNull(AsyncTask.Status.RUNNING);
        assertNotEquals(AsyncTask.Status.RUNNING, AsyncTask.Status.FINISHED);

        assertEquals(AsyncTask.Status.FINISHED, AsyncTask.Status.valueOf("FINISHED"));
        assertEquals(1, AsyncTask.Status.PENDING.ordinal());
        assertEquals("RUNNING", AsyncTask.Status.RUNNING.name());

        assertEquals(AsyncTask.Status.RUNNING, Enum.valueOf(AsyncTask.Status.class, "RUNNING"));

        AsyncTask.Status[] values = AsyncTask.Status.values();
        assertEquals(3, values.length);
        assertEquals(AsyncTask.Status.FINISHED, values[0]);
        assertEquals(AsyncTask.Status.PENDING, values[1]);
        assertEquals(AsyncTask.Status.RUNNING, values[2]);
    }

    @Test
    public void instanceFields() throws Exception {
        SyncResult result = mock(SyncResult.class);
        Field statsField = result.getClass().getField("stats");
        SyncStats syncStats = mock(SyncStats.class);
        statsField.set(result, syncStats);

        syncStats.numDeletes = 42;
        assertEquals(42, result.stats.numDeletes);
    }

    @Test
    public void javaResourcesOnClasspath() throws Exception {
        URL url = getClass().getClassLoader().getResource("app_test_resource_file.txt");
        assertNotNull(url);

        InputStream stream = getClass().getClassLoader().getResourceAsStream("app_test_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("app test", s);
    }

    @Test
    public void prodJavaResourcesOnClasspath() throws Exception {
        URL url = getClass().getClassLoader().getResource("app_resource_file.txt");
        assertNotNull(url);

        InputStream stream = getClass().getClassLoader().getResourceAsStream("app_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("app", s);
    }

    @Test
    public void libJavaResourcesOnClasspath() throws Exception {
        URL url = getClass().getClassLoader().getResource("util_resource_file.txt");
        assertNotNull(url);

        InputStream stream = getClass().getClassLoader().getResourceAsStream("util_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("util", s);
    }

    @Test
    public void javaLibJavaResourcesOnClasspath() throws Exception {
        URL url = getClass().getClassLoader().getResource("javalib_resource_file.txt");
        assertNotNull(url);

        InputStream stream = getClass().getClassLoader().getResourceAsStream("javalib_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("javalib", s);
    }

    @Test
    public void prodRClass() {
        int id = R.string.app_name;
        //noinspection ConstantConditions
        assertTrue(id > 0);
    }

    @Test
    public void commonsLogging() {
        Log log = LogFactory.getLog(getClass());
        log.info("I can use commons-logging!");
    }

    @Test
    public void workingDir() {
        assertTrue(new File("").getAbsolutePath().endsWith("app"));
    }

    @Test
    public void assertions() {
        try {
            assert false;
            fail("assertions disabled");
        } catch (AssertionError e) {
            // expected
        }
    }
}
