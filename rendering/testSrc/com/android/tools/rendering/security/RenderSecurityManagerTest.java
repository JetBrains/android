/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.rendering.security;

import static com.google.common.truth.Truth.assertThat;
import static java.io.File.separator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ide.common.resources.RecordingLogger;
import com.android.test.testutils.TestUtils;
import com.android.utils.SdkUtils;
import com.google.common.io.Files;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Disposer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Calls System.setSecurityManager which is not supported by GoogleTestSecurityManager that runs in bazel")
public class RenderSecurityManagerTest {
  private final Object myCredential = new Object();

  Disposable disposable = Disposer.newDisposable();

  @Before
  public void setUp() {
    new MockApplication(disposable);
    Extensions.getRootArea()
      .registerExtensionPoint(
        RenderPropertiesAccessUtil.EP_NAME.getName(),
        RenderSecurityManagerOverrides.class.getName(),
        ExtensionPoint.Kind.INTERFACE);
  }

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
  }

  @Test
  public void testExec() throws IOException {
    assertNull(RenderSecurityManager.getCurrent());
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    RecordingLogger logger = new RecordingLogger();
    manager.setLogger(logger);
    try {
      assertNull(RenderSecurityManager.getCurrent());
      manager.setActive(true, myCredential);
      assertSame(manager, RenderSecurityManager.getCurrent());
      if (new File("/bin/ls").exists()) {
        Runtime.getRuntime().exec("/bin/ls");
      }
      else {
        manager.checkExec("/bin/ls");
      }
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Exec access not allowed during rendering (/bin/ls)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
      assertNull(RenderSecurityManager.getCurrent());
      assertNull(System.getSecurityManager());
      assertThat(logger.getWarningMsgs()).isEmpty();
    }
  }

  @Test
  public void testSetSecurityManager() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);
      System.setSecurityManager(null);
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Security access not allowed during rendering", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testRead() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(
      "/Users/userHome/Sdk",
      "/Users/userHome/Projects/project1",
      true,
      () -> true);

    // Not allowed paths
    String[] notAllowedPaths = new String[] {
      "/foo",
      "/Users/userHome/Sdk/../foo",
      "/Users/userHome",
      "/Users/userHome/Projects/project1/../../test",
    };
    for (String path: notAllowedPaths) {
      try {
        manager.setActive(true, myCredential);
        manager.checkPermission(new FilePermission(path, "read"));
        fail(String.format("Should have thrown security exception (%s)", path));
      }
      catch (SecurityException exception) {
        assertEquals(
          String.format("Read access not allowed during rendering (%s)", path), exception.toString());
        // pass
      }
      finally {
        manager.dispose(myCredential);
      }
    }

    // allowed paths
    String[] allowedReadPaths = new String[] {
      "/Users/userHome/Sdk/foo",
      "/Users/userHome/Sdk/foo.jar",
      "/Users/userHome/Sdk/foo/test",
      "/Users/userHome/Sdk/foo/../foo.jar",
      "/Users/userHome/Sdk/foo/../../Sdk/test/foo.jar",
      "/Users/userHome/Projects/project1/path/test.kt",
      "/Users/userHome/Projects/project1/test.kt",
      "/Users/userHome/Projects/project1/test.kt",
      "/Users/userHome/Projects/project1/../project1/test.kt",
    };
    for (String path: allowedReadPaths) {
      try {
        manager.setActive(true, myCredential);
        manager.checkPermission(new FilePermission(path, "read"));
      }
      finally {
        manager.dispose(myCredential);
      }
    }
  }

  @Test
  public void testWrite() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(
      "/Users/userHome/Sdk",
      "/Users/userHome/Projects/project1",
      false,
      () -> true);

    String cachePath = PathManager.getSystemPath() + "/caches/";
    String indexPath = PathManager.getIndexRoot() + "/";

    // Not allowed paths
    String[] notAllowedPaths = new String[] {
      "foo",
      cachePath,
      indexPath,
      cachePath + "../foo",
      cachePath + "../../test.jar",
      indexPath + "../foo",
    };
    for (String path: notAllowedPaths) {
      try {
        manager.setActive(true, myCredential);
        manager.checkPermission(new FilePermission(path, "write"));
        fail(String.format("Should have thrown security exception (%s)", path));
      }
      catch (SecurityException exception) {
        assertEquals(
          String.format("Write access not allowed during rendering (%s)", path), exception.toString());
        // pass
      }
      finally {
        manager.dispose(myCredential);
      }
    }

    // allowed paths
    String[] allowedWritePaths = new String[] {
      cachePath + "/test/../test.jar",
      cachePath + "/foo.jar",
    };
    for (String path: allowedWritePaths) {
      try {
        manager.setActive(true, myCredential);
        manager.checkPermission(new FilePermission(path, "write"));
      }
      finally {
        manager.dispose(myCredential);
      }
    }
  }

  @Test
  public void testExecute() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission("/foo", "execute"));
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Write access not allowed during rendering (/foo)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testDelete() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission("/foo", "delete"));
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Write access not allowed during rendering (/foo)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testLoadLibrary() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      // Unit test only runs on OSX
      if (SdkUtils.startsWithIgnoreCase(System.getProperty("os.name"), "Mac") && new File("/usr/lib/libc.dylib").exists()) {
        System.load("/usr/lib/libc.dylib");
        fail("Should have thrown security exception");
      }
    }
    catch (SecurityException exception) {
      assertEquals("Link access not allowed during rendering (/usr/lib/libc.dylib)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testAllowedLoadLibrary() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      System.loadLibrary("jsound");
    }
    catch (UnsatisfiedLinkError e) {
      // pass - library may not be present on all JDKs
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void testInvalidRead() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      try {
        File file = new File(System.getProperty("user.home"));
        //noinspection ResultOfMethodCallIgnored
        file.lastModified();
      }
      catch (SecurityException exception) {
        fail("Reading should be allowed");
      }
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testInvalidPropertyWrite() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      // Try to make java.io.tmpdir point to user.home to grant myself access:
      String userHome = System.getProperty("user.home");
      System.setProperty("java.io.tmpdir", userHome);

      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Write access not allowed during rendering (java.io.tmpdir)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void testReadOk() throws IOException {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      File jdkHome = new File(System.getProperty("java.home"));
      assertTrue(jdkHome.exists());
      File[] files = jdkHome.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isFile()) {
            // noinspection UnstableApiUsage
            Files.toByteArray(file);
          }
        }
      }
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testProperties() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      //noinspection ResultOfMethodCallIgnored
      System.getProperties();

      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Property access not allowed during rendering", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testExit() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      System.exit(-1);

      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertEquals("Exit access not allowed during rendering (-1)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testThread() throws InterruptedException {
    final AtomicBoolean failedUnexpectedly = new AtomicBoolean(false);
    Thread otherThread = new Thread("other") {
      @Override
      public void run() {
        try {
          assertNull(RenderSecurityManager.getCurrent());
          //noinspection ResultOfMethodCallIgnored
          System.getProperties();
        }
        catch (SecurityException e) {
          failedUnexpectedly.set(true);
        }
      }
    };
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      // Threads cloned from this one should inherit the same security constraints
      final AtomicBoolean failedAsExpected = new AtomicBoolean(false);
      final Thread renderThread = new Thread("render") {
        @Override
        public void run() {
          try {
            //noinspection ResultOfMethodCallIgnored
            System.getProperties();
          }
          catch (SecurityException e) {
            failedAsExpected.set(true);
          }
        }
      };
      renderThread.start();
      renderThread.join();
      assertTrue(failedAsExpected.get());
      otherThread.start();
      otherThread.join();
      assertFalse(failedUnexpectedly.get());
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testActive() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      try {
        //noinspection ResultOfMethodCallIgnored
        System.getProperties();
        fail("Should have thrown security exception");
      }
      catch (SecurityException exception) {
        // pass
      }

      manager.setActive(false, myCredential);

      try {
        //noinspection ResultOfMethodCallIgnored
        System.getProperties();
      }
      catch (SecurityException exception) {
        fail(exception.toString());
      }

      manager.setActive(true, myCredential);

      try {
        //noinspection ResultOfMethodCallIgnored
        System.getProperties();
        fail("Should have thrown security exception");
      }
      catch (SecurityException exception) {
        // pass
      }
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testThread2() throws InterruptedException {
    ThreadGroup renderThreadGroup = new ThreadGroup("Render thread group");
    Supplier<Boolean> isRenderThread = () -> renderThreadGroup.parentOf(Thread.currentThread().getThreadGroup());
    final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    // Check that when a new thread is created simultaneously from an unrelated
    // thread during rendering, that new thread does not pick up the security manager.
    //
    final CyclicBarrier barrier1 = new CyclicBarrier(2);
    final CyclicBarrier barrier2 = new CyclicBarrier(2);
    final CyclicBarrier barrier3 = new CyclicBarrier(4);
    final CyclicBarrier barrier4 = new CyclicBarrier(4);
    final CyclicBarrier barrier5 = new CyclicBarrier(4);
    final CyclicBarrier barrier6 = new CyclicBarrier(4);

    // First the threads reach barrier1. Then from barrier1 to barrier2, thread1
    // installs the security manager. Then from barrier2 to barrier3, thread2
    // checks that it does not have any security restrictions, and creates thread3.
    // Thread1 will ensure that the security manager is working there, and it will
    // create thread4. Then after barrier3 (where thread3 and thread4 are now also
    // participating) thread3 will ensure that it too has no security restrictions,
    // and thread4 will ensure that it does. At barrier4 the security manager gets
    // uninstalled, and at barrier5 all threads will check that there are no more
    // restrictions. At barrier6 all threads are done.

    final Thread thread1 = new Thread(renderThreadGroup, "render") {
      @Override
      public void run() {
        try {
          barrier1.await();
          assertNull(RenderSecurityManager.getCurrent());

          RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, isRenderThread);
          manager.setActive(true, myCredential);

          barrier2.await();

          Thread thread4 = new Thread(() -> {
            try {
              barrier3.await();

              try {
                //noinspection ResultOfMethodCallIgnored
                System.getProperties();
                fail("Should have thrown security exception");
              }
              catch (SecurityException e) {
                // pass
              }

              barrier4.await();
              barrier5.await();
              assertNull(RenderSecurityManager.getCurrent());
              assertNull(System.getSecurityManager());
              barrier6.await();
            }
            catch (InterruptedException | BrokenBarrierException e) {
              fail(e.toString());
            }
          });
          thread4.start();
          threads.add(thread4);

          try {
            //noinspection ResultOfMethodCallIgnored
            System.getProperties();
            fail("Should have thrown security exception");
          }
          catch (SecurityException e) {
            // expected
          }

          barrier3.await();
          barrier4.await();
          manager.dispose(myCredential);

          assertNull(RenderSecurityManager.getCurrent());
          assertNull(System.getSecurityManager());

          barrier5.await();
          barrier6.await();

        }
        catch (InterruptedException | BrokenBarrierException e) {
          fail(e.toString());
        }
      }
    };

    final Thread thread2 = new Thread("unrelated") {
      @Override
      public void run() {
        try {
          barrier1.await();
          assertNull(RenderSecurityManager.getCurrent());
          barrier2.await();
          assertNull(RenderSecurityManager.getCurrent());
          assertNotNull(System.getSecurityManager());

          try {
            //noinspection ResultOfMethodCallIgnored
            System.getProperties();
          }
          catch (SecurityException e) {
            fail("Should not have been affected by security manager");
          }

          Thread thread3 = new Thread(() -> {
            try {
              barrier3.await();

              try {
                //noinspection ResultOfMethodCallIgnored
                System.getProperties();
              }
              catch (SecurityException e) {
                fail("Should not have been affected by security manager");
              }

              barrier4.await();
              barrier5.await();
              assertNull(RenderSecurityManager.getCurrent());
              assertNull(System.getSecurityManager());
              barrier6.await();

            }
            catch (InterruptedException | BrokenBarrierException e) {
              fail(e.toString());
            }
          });
          thread3.start();
          threads.add(thread3);

          barrier3.await();
          barrier4.await();
          barrier5.await();
          assertNull(RenderSecurityManager.getCurrent());
          assertNull(System.getSecurityManager());
          barrier6.await();

        }
        catch (InterruptedException | BrokenBarrierException e) {
          fail(e.toString());
        }
      }
    };

    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();
    for (Thread thread : threads) {
      thread.join();
    }
  }

  @Test
  public void testDisabled() {
    assertNull(RenderSecurityManager.getCurrent());

    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    RenderSecurityManager.sEnabled = false;
    try {
      assertNull(RenderSecurityManager.getCurrent());
      manager.setActive(true, myCredential);
      assertSame(manager, System.getSecurityManager());
      if (new File("/bin/ls").exists()) {
        Runtime.getRuntime().exec("/bin/ls");
      }
      else {
        manager.checkExec("/bin/ls");
      }
    }
    catch (SecurityException | IOException exception) {
      fail("Should have been disabled");
    }
    finally {
      RenderSecurityManager.sEnabled = true;
      manager.dispose(myCredential);
      assertNull(RenderSecurityManager.getCurrent());
      assertNull(System.getSecurityManager());
    }
  }

  @Test
  public void testLogger() throws InterruptedException {
    assertNull(RenderSecurityManager.getCurrent());

    final CyclicBarrier barrier1 = new CyclicBarrier(2);
    final CyclicBarrier barrier2 = new CyclicBarrier(2);
    final CyclicBarrier barrier3 = new CyclicBarrier(2);

    Thread thread = new Thread(() -> {
      try {
        barrier1.await();
        barrier2.await();

        System.setSecurityManager(new SecurityManager() {
          @Override
          public String toString() {
            return "MyTestSecurityManager";
          }

          @Override
          public void checkPermission(Permission permission) {
          }
        });

        barrier3.await();
        assertNull(RenderSecurityManager.getCurrent());
        assertNotNull(System.getSecurityManager());
        assertEquals("MyTestSecurityManager", System.getSecurityManager().toString());
      }
      catch (InterruptedException | BrokenBarrierException e) {
        fail(e.toString());
      }
    });
    thread.start();

    Thread thisThread = Thread.currentThread();
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> thisThread == Thread.currentThread());
    RecordingLogger logger = new RecordingLogger();
    manager.setLogger(logger);
    try {
      barrier1.await();
      assertNull(RenderSecurityManager.getCurrent());
      manager.setActive(true, myCredential);
      assertSame(manager, RenderSecurityManager.getCurrent());
      barrier2.await();
      barrier3.await();

      assertNull(RenderSecurityManager.getCurrent());
      manager.setActive(false, myCredential);
      assertNull(RenderSecurityManager.getCurrent());

      assertEquals(Collections.singletonList("RenderSecurityManager being replaced by another thread"), logger.getWarningMsgs());
    }
    catch (InterruptedException | BrokenBarrierException e) {
      fail(e.toString());
    }
    finally {
      manager.dispose(myCredential);
      assertNull(RenderSecurityManager.getCurrent());
      assertNotNull(System.getSecurityManager());
      assertEquals("MyTestSecurityManager", System.getSecurityManager().toString());
      System.setSecurityManager(null);
    }
    thread.join();
  }

  @Test
  public void testEnterExitSafeRegion() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    Object credential = new Object();
    try {
      manager.setActive(true, credential);

      boolean token = RenderSecurityManager.enterSafeRegion(credential);
      manager.checkPermission(new FilePermission("/foo", "execute"));
      RenderSecurityManager.exitSafeRegion(token);

      assertNotNull(RenderSecurityManager.getCurrent());
      boolean tokenOuter = RenderSecurityManager.enterSafeRegion(credential);
      assertNull(RenderSecurityManager.getCurrent());
      boolean tokenInner = RenderSecurityManager.enterSafeRegion(credential);
      assertNull(RenderSecurityManager.getCurrent());
      manager.checkPermission(new FilePermission("/foo", "execute"));
      assertNull(RenderSecurityManager.getCurrent());
      manager.checkPermission(new FilePermission("/foo", "execute"));
      RenderSecurityManager.exitSafeRegion(tokenInner);
      assertNull(RenderSecurityManager.getCurrent());
      RenderSecurityManager.exitSafeRegion(tokenOuter);
      assertNotNull(RenderSecurityManager.getCurrent());

      // Wrong credential
      Object wrongCredential = new Object();
      try {
        token = RenderSecurityManager.enterSafeRegion(wrongCredential);
        manager.checkPermission(new FilePermission("/foo", "execute"));
        RenderSecurityManager.exitSafeRegion(token);
        fail("Should have thrown exception");
      }
      catch (SecurityException e) {
        // pass
      }

      // Try turning off the security manager
      try {
        manager.setActive(false, wrongCredential);
      }
      catch (SecurityException e) {
        // pass
      }
      try {
        manager.setActive(false, null);
      }
      catch (SecurityException e) {
        // pass
      }
      try {
        manager.dispose(wrongCredential);
      }
      catch (SecurityException e) {
        // pass
      }

      // Try looking up the secret
      try {
        //noinspection JavaReflectionMemberAccess
        Field field = RenderSecurityManager.class.getField("sCredential");
        field.setAccessible(true);
        Object secret = field.get(null);
        manager.dispose(secret);
        fail("Shouldn't be able to find our way to the credential");
      }
      catch (Exception e) {
        // pass
        assertEquals("java.lang.NoSuchFieldException: sCredential", e.toString());
      }
    }
    finally {
      manager.dispose(credential);
    }
  }

  @Test
  public void testRunSafeRegion() throws Exception {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    Object credential = new Object();
    try {
      manager.setActive(true, credential);

      // Correct call with the right credential
      try {
        RenderSecurityManager.runInSafeRegion(credential, () -> manager.checkPermission(new FilePermission("/foo", "execute")));
        assertEquals(123L, (long)RenderSecurityManager.runInSafeRegion(credential, () -> {
          manager.checkPermission(new FilePermission("/foo", "execute"));
          return 123L;
        }));
      }
      catch (SecurityException e) {
        fail("Unexpected exception");
      }

      // Wrong credential
      Object wrongCredential = new Object();
      try {
        RenderSecurityManager.runInSafeRegion(wrongCredential, () -> manager.checkPermission(new FilePermission("/foo", "execute")));
        fail("Should have thrown exception");
      }
      catch (SecurityException e) {
        // pass
      }

      try {
        RenderSecurityManager.runInSafeRegion(wrongCredential, () -> {
          manager.checkPermission(new FilePermission("/foo", "execute"));
          return 123L;
        });
        fail("Should have thrown exception");
      }
      catch (SecurityException e) {
        // pass
      }
    }
    finally {
      manager.dispose(credential);
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  @Test
  public void testImageIo() throws IOException, InterruptedException {
    // Warm up ImageIO static state that calls write actions forbidden by RenderSecurityManager
    ImageIO.getCacheDirectory();
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      Path testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/rendering/testData");
      File root = new File(testDataPath.toFile(), "renderSecurityManager");
      assertNotNull(root);
      assertTrue(root.exists());
      final File icon = new File(root, "overlay" + separator + "drawable" + separator + "icon2.png");
      assertTrue(icon.exists());
      final byte[] buf = Files.toByteArray(icon);
      InputStream stream = new ByteArrayInputStream(buf);
      assertNotNull(stream);
      BufferedImage image = ImageIO.read(stream);
      assertNotNull(image);
      assertNull(ImageIO.getCacheDirectory());

      // Also run in non AWT thread to test ImageIO thread locals cache dir behavior
      Thread thread = new Thread(() -> {
        try {
          assertFalse(SwingUtilities.isEventDispatchThread());
          final byte[] buf1 = Files.toByteArray(icon);
          InputStream stream1 = new ByteArrayInputStream(buf1);
          assertNotNull(stream1);
          BufferedImage image1 = ImageIO.read(stream1);
          assertNotNull(image1);
          assertNull(ImageIO.getCacheDirectory());
        }
        catch (Throwable t) {
          t.printStackTrace();
          fail(t.toString());
        }
      });

      thread.start();
      thread.join();
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testTempDir() throws IOException {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      String temp = System.getProperty("java.io.tmpdir");
      assertNotNull(temp);

      manager.checkPermission(new FilePermission(temp, "read,write"));
      manager.checkPermission(new FilePermission(temp + separator, "read,write"));

      temp = new File(temp).getCanonicalPath();
      manager.checkPermission(new FilePermission(temp, "read,write"));

    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testAppTempDir() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setAppTempDir("/random/path/");
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission("/random/path/myfile.tmp", "read,write"));
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testSetTimeZone() {
    // Warm up TimeZone.defaultTimeZone initialization that accesses properties forbidden by RenderSecurityManager
    TimeZone.getDefault();
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      /* ICU needs this (needed for Calendar widget rendering)
          at java.util.TimeZone.hasPermission(TimeZone.java:597)
          at java.util.TimeZone.setDefault(TimeZone.java:619)
          at com.ibm.icu.util.TimeZone.setDefault(TimeZone.java:973)
          at libcore.icu.DateIntervalFormat_Delegate.createDateIntervalFormat(DateIntervalFormat_Delegate.java:61)
          at libcore.icu.DateIntervalFormat.createDateIntervalFormat(DateIntervalFormat.java)
          at libcore.icu.DateIntervalFormat.getFormatter(DateIntervalFormat.java:112)
          at libcore.icu.DateIntervalFormat.formatDateRange(DateIntervalFormat.java:102)
          at libcore.icu.DateIntervalFormat.formatDateRange(DateIntervalFormat.java:71)
          at android.text.format.DateUtils.formatDateRange(DateUtils.java:826)
       */
      TimeZone deflt = TimeZone.getDefault();
      String[] availableIDs = TimeZone.getAvailableIDs();
      TimeZone timeZone = TimeZone.getTimeZone(availableIDs[0]);
      TimeZone.setDefault(timeZone);
      TimeZone.setDefault(deflt);
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  /**
   * Regression test for b/223219330.
   */
  @Test
  public void testLogDir() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      String logPath = PathManager.getLogPath();
      assertNotNull(logPath);

      manager.checkPermission(new FilePermission(logPath + separator + "fake.log", "read,write"));

    }
    finally {
      manager.dispose(myCredential);
    }
  }
  @Test
  public void testLogException() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      String logPath = PathManager.getLogPath();
      assertNotNull(logPath);

      Logger.Factory oldFactory = Logger.getFactory();
      try {
        TestLoggerWithPropertyAccess loggerWithPropertyAccess = new TestLoggerWithPropertyAccess(Logger.getInstance(RenderSecurityManager.class));
        Logger.setFactory(category -> loggerWithPropertyAccess);
        Logger.getInstance(RenderSecurityManagerTest.class).error("test", new TestException());
      } catch (Throwable t) {
        // We expect the actual cause to be the TestException if the sandboxing is working correctly. If not, it will throw a security
        // exception.
        assertTrue("Unexpected exception " + t, t.getCause() instanceof TestException);
      } finally {
        Logger.setFactory(oldFactory);
      }
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testNoLinkCreationAllowed() throws IOException {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    Path testTemp = java.nio.file.Files.createTempDirectory("linkTest");
    Path attackLink = testTemp.resolve("attack");
    File victimFile = new File(PathManager.getConfigPath() + "/victim-" + UUID.randomUUID().toString());
    victimFile.deleteOnExit();
    try {
      manager.setActive(true, myCredential);
      java.nio.file.Files.createSymbolicLink(attackLink, victimFile.toPath());
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertThat(exception.toString()).startsWith("SymbolicLinks access not allowed during rendering");
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testCheckWriteToNonExistingLink() throws IOException {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);

    Path testTemp = java.nio.file.Files.createTempDirectory("linkTest");
    Path attackLink = testTemp.resolve("attack");
    File victimFile = new File(PathManager.getConfigPath() + "/victim-" + UUID.randomUUID());
    victimFile.deleteOnExit();
    java.nio.file.Files.createSymbolicLink(attackLink, victimFile.toPath());

    try {
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission(attackLink.toString(), "read,write"));
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertThat(exception.toString()).startsWith("Write access not allowed during rendering");
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testCheckWriteToExistingLink() throws IOException {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);

    Path testTemp = java.nio.file.Files.createTempDirectory("linkTest");
    Path attackLink = testTemp.resolve("attack");
    File victimFile = new File(PathManager.getConfigPath() + "/victim-" + UUID.randomUUID());
    victimFile.deleteOnExit();
    java.nio.file.Files.createSymbolicLink(attackLink, victimFile.toPath());
    java.nio.file.Files.writeString(victimFile.toPath(), "existing-file", Charset.defaultCharset());
    try {
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission(attackLink.toString(), "read,write"));
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertThat(exception.toString()).startsWith("Write access not allowed during rendering");
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  /**
   * Regression test for b/236865896.
   */
  @Test
  public void testPathTraversal() throws IOException {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission("/tmp/../dev/null", "read,write"));
      fail("Should have thrown security exception");
    }
    catch (SecurityException exception) {
      assertThat(exception.toString()).startsWith("Write access not allowed during rendering");
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testSystemPropertiesAccess() {
    RenderSecurityManager manager = RenderSecurityManager.createForTests(null, null, false, () -> true);
    try {
      manager.setActive(true, myCredential);

      try {
        //noinspection ResultOfMethodCallIgnored
        System.getProperties();
        fail("Expected to throw RenderSecurityException");
      } catch (RenderSecurityException ignore) {
        // Expected to throw a security exception.
      }
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  private static class TestException extends Throwable { }
}
