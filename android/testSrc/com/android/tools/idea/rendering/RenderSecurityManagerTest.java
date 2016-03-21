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
package com.android.tools.idea.rendering;

import com.android.ide.common.res2.RecordingLogger;
import com.android.utils.SdkUtils;
import com.google.common.io.Files;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Ignore;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilePermission;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.Permission;
import java.util.Collections;
import java.util.TimeZone;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.io.File.separator;
import static org.junit.Assert.*;

public class RenderSecurityManagerTest {

  private Object myCredential = new Object();

  @Test
  public void testExec() throws Exception {
    assertNull(RenderSecurityManager.getCurrent());
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
      //noinspection ConstantConditions
      assertEquals(RenderSecurityManager.RESTRICT_READS
                          ? "Read access not allowed during rendering (/bin/ls)"
                          : "Exec access not allowed during rendering (/bin/ls)", exception.toString());
      // pass
    }
    finally {
      manager.dispose(myCredential);
      assertNull(RenderSecurityManager.getCurrent());
      assertNull(System.getSecurityManager());
      assertEquals(Collections.<String>emptyList(), logger.getWarningMsgs());
    }
  }

  @Test
  public void testSetSecurityManager() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testReadWrite() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);
      manager.checkPermission(new FilePermission("/foo", "read,write"));
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
  public void testExecute() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testDelete() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testLoadLibrary() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testAllowedLoadLibrary() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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

  @Test
  public void testInvalidRead() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);

      if (RenderSecurityManager.RESTRICT_READS) {
        try {
          File file = new File(System.getProperty("user.home"));
          //noinspection ResultOfMethodCallIgnored
          file.lastModified();

          fail("Should have thrown security exception");
        }
        catch (SecurityException exception) {
          assertEquals("Read access not allowed during rendering (" +
                              System.getProperty("user.home") + ")", exception.toString());
          // pass
        }
      }
      else {
        try {
          File file = new File(System.getProperty("user.home"));
          //noinspection ResultOfMethodCallIgnored
          file.lastModified();
        }
        catch (SecurityException exception) {
          fail("Reading should be allowed");
        }
      }
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testInvalidPropertyWrite() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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

  @Test
  public void testReadOk() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);

      File jdkHome = new File(System.getProperty("java.home"));
      assertTrue(jdkHome.exists());
      //noinspection ResultOfMethodCallIgnored
      File[] files = jdkHome.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isFile()) {
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
  public void testProperties() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);

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
  public void testExit() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testThread() throws Exception {
    final AtomicBoolean failedUnexpectedly = new AtomicBoolean(false);
    Thread otherThread = new Thread("other") {
      @Override
      public void run() {
        try {
          assertNull(RenderSecurityManager.getCurrent());
          System.getProperties();
        }
        catch (SecurityException e) {
          failedUnexpectedly.set(true);
        }
      }
    };
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);

      // Threads cloned from this one should inherit the same security constraints
      final AtomicBoolean failedAsExpected = new AtomicBoolean(false);
      final Thread renderThread = new Thread("render") {
        @Override
        public void run() {
          try {
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
  public void testActive() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);

      try {
        System.getProperties();
        fail("Should have thrown security exception");
      }
      catch (SecurityException exception) {
        // pass
      }

      manager.setActive(false, myCredential);

      try {
        System.getProperties();
      }
      catch (SecurityException exception) {
        fail(exception.toString());
      }

      manager.setActive(true, myCredential);

      try {
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
  public void testThread2() throws Exception {
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

    final Thread thread1 = new Thread("render") {
      @Override
      public void run() {
        try {
          barrier1.await();
          assertNull(RenderSecurityManager.getCurrent());

          RenderSecurityManager manager = new RenderSecurityManager(null, null);
          manager.setActive(true, myCredential);

          barrier2.await();

          Thread thread4 = new Thread() {
            @Override
            public void run() {
              try {
                barrier3.await();

                try {
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
              catch (InterruptedException e) {
                fail(e.toString());
              }
              catch (BrokenBarrierException e) {
                fail(e.toString());
              }
            }
          };
          thread4.start();

          try {
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
        catch (InterruptedException e) {
          fail(e.toString());
        }
        catch (BrokenBarrierException e) {
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
            System.getProperties();
          }
          catch (SecurityException e) {
            fail("Should not have been affected by security manager");
          }

          Thread thread3 = new Thread() {
            @Override
            public void run() {
              try {
                barrier3.await();

                try {
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
              catch (InterruptedException e) {
                fail(e.toString());
              }
              catch (BrokenBarrierException e) {
                fail(e.toString());
              }
            }
          };
          thread3.start();

          barrier3.await();
          barrier4.await();
          barrier5.await();
          assertNull(RenderSecurityManager.getCurrent());
          assertNull(System.getSecurityManager());
          barrier6.await();

        }
        catch (InterruptedException e) {
          fail(e.toString());
        }
        catch (BrokenBarrierException e) {
          fail(e.toString());
        }

      }
    };

    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();
  }

  @Test
  public void testDisabled() throws Exception {
    assertNull(RenderSecurityManager.getCurrent());

    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
    catch (SecurityException exception) {
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
  public void testLogger() throws Exception {
    assertNull(RenderSecurityManager.getCurrent());

    final CyclicBarrier barrier1 = new CyclicBarrier(2);
    final CyclicBarrier barrier2 = new CyclicBarrier(2);
    final CyclicBarrier barrier3 = new CyclicBarrier(2);

    Thread thread = new Thread() {
      @Override
      public void run() {
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
        catch (InterruptedException e) {
          fail(e.toString());
        }
        catch (BrokenBarrierException e) {
          fail(e.toString());
        }
      }
    };
    thread.start();

    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
    catch (InterruptedException e) {
      fail(e.toString());
    }
    catch (BrokenBarrierException e) {
      fail(e.toString());
    }
    finally {
      manager.dispose(myCredential);
      assertNull(RenderSecurityManager.getCurrent());
      assertNotNull(System.getSecurityManager());
      assertEquals("MyTestSecurityManager", System.getSecurityManager().toString());
      System.setSecurityManager(null);
    }
  }

  @Test
  public void testEnterExitSafeRegion() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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

  /**
   * FIXME b.android.com/204441
   *
   * Java 8 broke {@link SecurityManager#checkMemberAccess(Class, int)} by deprecating it and not calling it. As a result, it is possible to
   * access sCredential via reflection on Java 8 and above. The alternative to {@code checkMemberAccess} is
   * {@link SecurityManager#checkPermission(Permission)}, which doesn't allow selectively allowing reflection.
   */
  @Ignore
  @Test
  public void testMemberAccess() {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    Object credential = new Object();
    manager.setActive(true, credential);
    // Try looking up the secret (with getDeclaredField instead of getField)
    try {
      Field field = RenderSecurityManager.class.getDeclaredField("sCredential");
      field.setAccessible(true);
      Object secret = field.get(null);
      manager.dispose(secret);
      fail("Shouldn't be able to find our way to the credential");
    }
    catch (Exception e) {
      // pass
      assertEquals("Reflection access not allowed during rendering " + "(com.android.ide.common.rendering.RenderSecurityManager)",
                   e.toString());
    }
    finally {
      manager.dispose(credential);
    }
  }

  @Test
  public void testImageIo() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
    try {
      manager.setActive(true, myCredential);

      File testDataPath = new File(AndroidTestBase.getTestDataPath());
      File root = new File(testDataPath, "renderSecurityManager");
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
      Thread thread = new Thread() {
        @Override
        public void run() {
          try {
            assertFalse(SwingUtilities.isEventDispatchThread());
            final byte[] buf = Files.toByteArray(icon);
            InputStream stream = new ByteArrayInputStream(buf);
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertNull(ImageIO.getCacheDirectory());
          }
          catch (Throwable t) {
            t.printStackTrace();
            fail(t.toString());
          }
        }
      };

      thread.start();
      thread.join();
    }
    finally {
      manager.dispose(myCredential);
    }
  }

  @Test
  public void testTempDir() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testAppTempDir() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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
  public void testSetTimeZone() throws Exception {
    RenderSecurityManager manager = new RenderSecurityManager(null, null);
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


}
