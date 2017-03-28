/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tests;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.SyncResult;
import android.content.SyncStats;
import android.os.AsyncTask;
import android.os.Debug;
import android.os.PowerManager;
import android.util.ArrayMap;

import com.android.tests.lib.LibFoo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class UnitTest {
    @Test
    public void passingTest() {
        assertEquals(4, 2+2);
    }

    @Test
    public void failingTest() {
        assertEquals(7, 3+3);
    }

    @Test
    public void referenceProductionCode() {
        // Reference production code:
        Foo foo = new Foo();
        assertEquals("production code", foo.foo());
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
        org.jdeferred.Deferred<Integer, Integer, Integer> deferred =
                new org.jdeferred.impl.DeferredObject<Integer, Integer, Integer>();
        org.jdeferred.Promise promise = deferred.promise();
        deferred.resolve(42);
        assertTrue(promise.isResolved());
    }

    @Test
    public void exceptions() {
        try {
            ArrayMap map = new ArrayMap();
            map.isEmpty();
            fail();
        } catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertTrue(e.getMessage().contains("isEmpty"));
            assertTrue(e.getMessage().contains("not mocked"));
        }

        try {
            Debug.getThreadAllocCount();
            fail();
        } catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertTrue(e.getMessage().contains("getThreadAllocCount"));
            assertTrue(e.getMessage().contains("not mocked"));
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
        URL url = UnitTest.class.getClassLoader().getResource("resource_file.txt");
        assertNotNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        // Make sure test resources shadow prod resources with the same name.
        assertEquals("from test", s);
    }

    @Test
    public void prodJavaResourcesOnClasspath() throws Exception {
        URL url = UnitTest.class.getClassLoader().getResource("prod_resource_file.txt");
        assertNotNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("prod_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("prod", s);
    }

    @Test
    public void libJavaResourcesOnClasspath() throws Exception {
        URL url = UnitTest.class.getClassLoader().getResource("lib_prod_resource_file.txt");
        assertNotNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("lib_prod_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("lib prod", s);
    }

    @Test
    public void libTestJavaResourcesNotOnClasspath() throws Exception {
        URL url = UnitTest.class.getClassLoader().getResource("lib_test_resource_file.txt");
        assertNull(url);

        InputStream stream = UnitTest.class.getClassLoader().getResourceAsStream("lib_test_resource_file.txt");
        assertNull(stream);
    }

    @Test
    public void prodRClass() {
        int id = R.string.app_name;
        assertTrue(id > 0);
    }

    @Test
    public void commonsLogging() {
        Log log = LogFactory.getLog(getClass());
        log.info("I can use commons-logging!");
    }

    @Test
    public void libraryCode() {
        LibFoo libFoo = new LibFoo();
        assertEquals("library code", libFoo.foo());

        Foo foo = new Foo();
        assertEquals("library code", foo.callLibFoo());
    }

    @Test
    public void onlyOneMockableJar() throws Exception {
        URL[] urls = ((URLClassLoader) getClass().getClassLoader()).getURLs();
        int count = 0;
        URL mockableJar = null;
        for(URL u : urls){
            if(u.toString().contains("mockable-")){
                count++;
                mockableJar = u;
            }
        }

        assertEquals(1, count);
        assertNotNull(mockableJar);
        assertTrue(mockableJar.toString().contains("mockable-android-22.jar"));
    }
}
