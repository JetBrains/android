/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.layoutlib.reflection.TrackingThreadLocal;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestLoggerFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;


public class RenderClassLoaderTest {
  private static Logger ourLoggerInstance;
  private Logger.Factory myOriginalFactory;

  @AfterClass
  public static void afterClass() {
    Logger.setFactory(TestLoggerFactory.class);
  }

  @Before
  public void before() {
    try {
      Field factoryField = Logger.class.getDeclaredField("ourFactory");
      factoryField.setAccessible(true);
      myOriginalFactory = (Logger.Factory)factoryField.get(null);
      factoryField.setAccessible(false);
    }
    catch (IllegalAccessException | NoSuchFieldException ignore) {
    }
    Logger.setFactory(MyLoggerFactory.class);
    ourLoggerInstance = new DefaultLogger("") {
      @Override
      public void error(@NonNls String message, @Nullable Throwable t, @NonNls @NotNull String... details) {
        fail("Logger shouldn't receive any error calls");
      }
    };
  }

  @After
  public void after() {
    if (myOriginalFactory != null) {
      Logger.setFactory(myOriginalFactory.getClass());
    }
  }

  @Test
  public void testRemovingJarFile() throws IOException, ClassNotFoundException {

    File jarSource = new File(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/lib.jar");
    File testJarFile = File.createTempFile("RenderClassLoader", ".jar");
    FileUtil.copy(jarSource, testJarFile);
    RenderClassLoader loader = new RenderClassLoader(this.getClass().getClassLoader()) {
      @Override
      protected List<Path> getExternalJars() {
        return List.of(testJarFile.toPath());
      }
    };

    loader.loadClassFromNonProjectDependency("com.myjar.MyJarClass");
    assertTrue(testJarFile.delete());
    try {
      loader.loadClassFromNonProjectDependency("com.myjar.MyJarClass");
      fail("Class should be available anymore");
    } catch (ClassNotFoundException ignore) {
    }
  }

  @Test
  public void testRemovingClassFile() throws IOException {
    File classSource = new File(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/MyJarClass.class");
    byte[] classBytes = Files.readAllBytes(classSource.toPath());

    RenderClassLoader loader = new RenderClassLoader(this.getClass().getClassLoader()) {
      @Override
      protected List<Path> getExternalJars() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      protected Class<?> defineClassAndPackage(@NotNull String name, @NotNull byte[] b, int offset, int len) {
        // We do not really want to define the class in the test, only make sure that this call is made.
        return RenderClassLoaderTest.class;
      }
    };
    VirtualFile vFile = mock(VirtualFile.class);
    when(vFile.contentsToByteArray()).thenReturn(classBytes);
    assertEquals(RenderClassLoaderTest.class, loader.loadClassFile("com.myjar.MyJarClass", vFile));
    vFile = mock(VirtualFile.class);
    when(vFile.contentsToByteArray()).thenThrow(new FileNotFoundException(""));
    assertNull(loader.loadClassFile("com.myjar.MyJarClass", vFile));
  }

  @Test
  public void testThreadLocalsRemapper_threadLocalAncestor() throws Exception {
    Path testJarPath = Paths.get(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/mythreadlocals.jar");
    RenderClassLoader loader = new RenderClassLoader(this.getClass().getClassLoader(), cv -> new ThreadLocalRenameTransform(cv)) {
      @Override
      protected List<Path> getExternalJars() {
        return ImmutableList.of(testJarPath);
      }
    };

    Class<?> customThreadLocalClass = loader.loadClassFromNonProjectDependency("com.mythreadlocalsjar.CustomThreadLocal");
    Constructor<?> constructor = customThreadLocalClass.getConstructor();
    Object threadLocal = constructor.newInstance();
    assertTrue(threadLocal instanceof TrackingThreadLocal);

    Set<ThreadLocal<?>> trackedThreadLocals = TrackingThreadLocal.Companion.clearThreadLocals(loader);
    assertThat(trackedThreadLocals).containsExactly(threadLocal);
    trackedThreadLocals.forEach(tl -> tl.remove());
  }

  @Test
  public void testThreadLocalsRemapper_threadLocalContainer() throws Exception {
    Path testJarPath = Paths.get(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/mythreadlocals.jar");
    RenderClassLoader loader = new RenderClassLoader(this.getClass().getClassLoader(), cv -> new ThreadLocalRenameTransform(cv)) {
      @Override
      protected List<Path> getExternalJars() {
        return ImmutableList.of(testJarPath);
      }
    };

    Class<?> customThreadLocalClass = loader.loadClassFromNonProjectDependency("com.mythreadlocalsjar.ThreadLocalContainer");
    Field threadLocalField = customThreadLocalClass.getField("threadLocal");
    Object threadLocal = threadLocalField.get(null);
    assertTrue(threadLocal instanceof TrackingThreadLocal);

    Set<ThreadLocal<?>> trackedThreadLocals = TrackingThreadLocal.Companion.clearThreadLocals(loader);
    assertThat(trackedThreadLocals).containsExactly(threadLocal);
    trackedThreadLocals.forEach(tl -> tl.remove());
  }

  public static class MyLoggerFactory implements Logger.Factory {
    public MyLoggerFactory() {
    }

    @NotNull
    @Override
    public Logger getLoggerInstance(@NotNull String category) {
      return ourLoggerInstance;
    }
  }
}