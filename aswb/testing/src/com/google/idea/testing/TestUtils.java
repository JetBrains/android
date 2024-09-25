/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.testing;

import com.google.common.base.StandardSystemProperty;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.util.pico.DefaultPicoContainer;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.picocontainer.PicoContainer;

/** Test utilities. */
public final class TestUtils {
  private TestUtils() {}

  /**
   * Gets directory name that should be used for all files created during testing.
   *
   * <p>This method will return a directory that's common to all tests run within the same <i>build
   * target</i>.
   *
   * @return standard absolute file name, for example "/tmp/zogjones/foo_unittest/".
   */
  public static String getTmpDir() {
    return getTmpDirFile().getAbsolutePath();
  }

  /**
   * Gets directory that should be used for all files created during testing.
   *
   * <p>This method will return a directory that's common to all tests run within the same <i>build
   * target</i>.
   *
   * @return standard file, for example the File representing "/tmp/zogjones/foo_unittest/".
   */
  public static File getTmpDirFile() {
    return TmpDirHolder.tmpDir;
  }

  private static final class TmpDirHolder {
    private static final File tmpDir = findTmpDir();

    private static File findTmpDir() {
      File tmpDir;

      // Flag value specified in environment?
      String tmpDirStr = getUserValue("TEST_TMPDIR");
      if ((tmpDirStr != null) && (tmpDirStr.length() > 0)) {
        tmpDir = new File(tmpDirStr);
      } else {
        // Fallback default $TEMP/$USER/tmp/$TESTNAME
        String baseTmpDir = StandardSystemProperty.JAVA_IO_TMPDIR.value();
        tmpDir = new File(baseTmpDir).getAbsoluteFile();

        // .. Add username
        String username = StandardSystemProperty.USER_NAME.value();
        username = username.replace('/', '_');
        username = username.replace('\\', '_');
        username = username.replace('\000', '_');
        tmpDir = new File(tmpDir, username);
        tmpDir = new File(tmpDir, "tmp");
      }

      // Ensure tmpDir exists
      if (!tmpDir.isDirectory()) {
        tmpDir.mkdirs();
      }
      return tmpDir;
    }
  }

  /**
   * Returns the value for system property <code>name</code>, or if that is not found the value of
   * the user's environment variable <code>name</code>. If neither is found, null is returned.
   *
   * @param name the name of property to get
   * @return the value of the property or null if it is not found
   */
  static String getUserValue(String name) {
    String propValue = System.getProperty(name);
    if (propValue == null) {
      return System.getenv(name);
    }
    return propValue;
  }

  private static class MyMockApplication extends MockApplication {
    private final ExecutorService executor = MoreExecutors.newDirectExecutorService();
    private final boolean forceInvokeLater;

    MyMockApplication(Disposable parentDisposable, boolean forceInvokeLater) {
      super(parentDisposable);
      this.forceInvokeLater = forceInvokeLater;
    }

    @Override
    public void invokeLater(Runnable runnable, ModalityState state) {
      if (forceInvokeLater) {
        runnable.run();
      }
    }

    @Override
    public Future<?> executeOnPooledThread(Runnable action) {
      return executor.submit(action);
    }

    @Override
    public <T> Future<T> executeOnPooledThread(Callable<T> action) {
      return executor.submit(action);
    }
  }

  public static MockApplication createMockApplication(Disposable parentDisposable) {
    return createMockApplication(parentDisposable, false);
  }

  public static MockApplication createMockApplication(
      Disposable parentDisposable, boolean forceInvokeLater) {
    final MyMockApplication instance = new MyMockApplication(parentDisposable, forceInvokeLater);
    ApplicationManager.setApplication(instance, FileTypeManager::getInstance, parentDisposable);
    instance.registerService(EncodingManager.class, EncodingManagerImpl.class);
    return instance;
  }

  public static MockProject mockProject(
      @Nullable PicoContainer container, Disposable parentDisposable) {
    container = container != null ? container : new DefaultPicoContainer();
    return new MockProject(container, parentDisposable);
  }
}
