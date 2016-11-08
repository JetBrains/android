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
import com.intellij.testFramework.TestLoggerFactory;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class RenderClassLoaderTest {
  private static Logger ourLoggerInstance;

  @AfterClass
  public static void afterClass() {
    Logger.setFactory(TestLoggerFactory.class);
  }

  @Test
  public void testRemovingJarFile() throws IOException {
    ourLoggerInstance = new DefaultLogger("") {
      @Override
      public void error(@NonNls String message, @Nullable Throwable t, @NonNls @NotNull String... details) {
        fail("Logger shouldn't receive any error calls");
      }
    };

    Logger.setFactory(MyLoggerFactory.class);

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

  public static class MyLoggerFactory implements Logger.Factory {
    public MyLoggerFactory() {
    }

    @Override
    public Logger getLoggerInstance(String category) {
      return ourLoggerInstance;
    }
  }
}