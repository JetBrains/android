// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.testutils;

import com.intellij.openapi.diagnostic.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.mockito.Mockito;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.listeners.MockCreationListener;

public class MockitoThreadLocalsCleaner {
  protected List<Object> mockitoMocks = new ArrayList<>();
  private MockCreationListener mockCreationListener;

  private static MockitoThreadLocalsCleaner activeInst = null;
  private static Throwable activeInstSetupTrace = null;

  public void setup() {
    if (activeInst != null && activeInst != this) {
      logPreviousAndOwnStackTrace();

      try {
        activeInst.cleanupAndTearDown();
        activeInst = null;
        activeInstSetupTrace = null;
      }
      catch (Exception e) {
        Logger.getInstance(MockitoThreadLocalsCleaner.class).error("Cannot shutdown previous MockitoThreadLocalsCleaner instance", e);
      }
    }


    if (mockCreationListener == null) {
      mockCreationListener = (mock, settings) -> mockitoMocks.add(mock);
      Mockito.framework().addListener(mockCreationListener);
      activeInst = this;
      activeInstSetupTrace = new Throwable();
    }
  }

  private void logPreviousAndOwnStackTrace() {
    Logger.getInstance(MockitoThreadLocalsCleaner.class).warn(
      "Previous test didn't clean up properly. Previous Mockito cleaner setup trace:", activeInstSetupTrace
    );

    Logger.getInstance(MockitoThreadLocalsCleaner.class).warn(
      "Previous test didn't clean up properly. Own setup trace:", new Throwable()
    );
  }

  public void cleanupAndTearDown() throws Exception {
    if (mockCreationListener != null) {
      Mockito.framework().removeListener(mockCreationListener);
      mockCreationListener = null;
    }
    resetMocks();
    resetWellKnownThreadLocals();

    if (activeInst == this) {
      activeInst = null;
      activeInstSetupTrace = null;
    }
  }

  private void resetMocks() {
    for (Object o : mockitoMocks) {
      Mockito.reset(o);
    }
    mockitoMocks.clear();
  }

  protected void resetWellKnownThreadLocals()
    throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    Field provider = ThreadSafeMockingProgress.class.getDeclaredField("MOCKING_PROGRESS_PROVIDER");
    provider.setAccessible(true);
    ThreadLocal<?> key = (ThreadLocal<?>)provider.get(ThreadSafeMockingProgress.class);
    Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
    threadLocalsField.setAccessible(true);
    Method remove = threadLocalsField.getType().getDeclaredMethod("remove", ThreadLocal.class);
    remove.setAccessible(true);
    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    for (Thread thread : threads) {
      Object o = threadLocalsField.get(thread);
      if (o != null) {
        remove.invoke(o, key);
      }
    }
  }
}
