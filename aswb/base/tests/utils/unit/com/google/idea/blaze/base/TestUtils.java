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
package com.google.idea.blaze.base;

import static org.junit.Assert.fail;

import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.PlatformUtils;
import com.intellij.util.pico.DefaultPicoContainer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

/** Test utilities. */
public class TestUtils {

  static class BlazeMockApplication extends MockApplication {
    private final ExecutorService executor = MoreExecutors.newDirectExecutorService();

    public BlazeMockApplication(@NotNull Disposable parentDisposable) {
      super(parentDisposable);
    }

    @NotNull
    @Override
    public Future<?> executeOnPooledThread(@NotNull Runnable action) {
      return executor.submit(action);
    }

    @NotNull
    @Override
    public <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action) {
      return executor.submit(action);
    }
  }

  @NotNull
  public static MockProject mockProject(
      @Nullable PicoContainer container, Disposable parentDisposable) {
    container = container != null ? container : new DefaultPicoContainer();
    return new MockProject(container, parentDisposable);
  }

  public static void assertIsSerializable(@NotNull Serializable object) {
    ObjectOutputStream out = null;
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      out = new ObjectOutputStream(byteArrayOutputStream);
      out.writeObject(object);
    } catch (NotSerializableException e) {
      fail("An object is not serializable: " + e.getMessage());
    } catch (IOException e) {
      fail("Could not serialize object: " + e.getMessage());
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException e) {
          // ignore
        }
      }
      try {
        byteArrayOutputStream.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /**
   * Sets the platform prefix system property, reverting to the previous value when the supplied
   * parent disposable is disposed.
   */
  public static void setPlatformPrefix(Disposable parentDisposable, String platformPrefix) {
    String prevValue = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, platformPrefix);
    Disposer.register(
        parentDisposable,
        () -> {
          if (prevValue != null) {
            System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prevValue);
          } else {
            System.clearProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
          }
        });
  }

  /**
   * Sets a system property, reverting to the previous value when the supplied parent disposable is
   * disposed.
   */
  public static void setSystemProperties(Disposable parentDisposable, String key, String value) {
    String prevValue = System.getProperty(key);
    System.setProperty(key, value);
    Disposer.register(
        parentDisposable,
        () -> {
          if (prevValue != null) {
            System.setProperty(key, value);
          } else {
            System.clearProperty(key);
          }
        });
  }
}
