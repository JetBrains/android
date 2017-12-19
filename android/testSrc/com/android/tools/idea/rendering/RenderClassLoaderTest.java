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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestLoggerFactory;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


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
  }

  @After
  public void after() {
    if (myOriginalFactory != null) {
      Logger.setFactory(myOriginalFactory.getClass());
    }
  }

  @Test
  public void testRemovingJarFile() throws IOException {
    ourLoggerInstance = new DefaultLogger("") {
      @Override
      public void error(@NonNls String message, @Nullable Throwable t, @NonNls @NotNull String... details) {
        fail("Logger shouldn't receive any error calls");
      }
    };

    File jarSource = new File(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/lib.jar");
    File testJarFile = File.createTempFile("RenderClassLoader", ".jar");
    FileUtil.copy(jarSource, testJarFile);
    URL testJarFileUrl = testJarFile.toURI().toURL();
    RenderClassLoader loader = new RenderClassLoader(this.getClass().getClassLoader(), 0) {
      @Override
      protected List<URL> getExternalJars() {
        return ImmutableList.of(testJarFileUrl);
      }
    };

    loader.loadClassFromJar("com.myjar.MyJarClass");
    assertTrue(testJarFile.delete());
    loader.loadClassFromJar("com.myjar.MyJarClass");
  }

  @Test
  public void testRemovingClassFile() throws IOException {
    ourLoggerInstance = new DefaultLogger("") {
      @Override
      public void error(@NonNls String message, @Nullable Throwable t, @NonNls @NotNull String... details) {
        fail("Logger shouldn't receive any error calls");
      }
    };

    File classSource = new File(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/MyJarClass.class");
    byte[] classBytes = Files.readAllBytes(classSource.toPath());

    RenderClassLoader loader = new RenderClassLoader(this.getClass().getClassLoader(), 0) {
      @Override
      protected List<URL> getExternalJars() {
        return ImmutableList.of();
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