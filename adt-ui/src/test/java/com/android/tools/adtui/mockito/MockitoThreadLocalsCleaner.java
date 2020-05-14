// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.adtui.mockito;

import org.mockito.Mockito;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.listeners.MockCreationListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// FIXME-ank: adt-ui may not depend on android.testFramework, because testFramework already depends on adt-ui.
// This dependency should probably be inverted. testFramework only needs 2 classes to work with Images from adt-ui.
public class MockitoThreadLocalsCleaner {
  protected List<Object> mockitoMocks = new ArrayList<>();
  private MockCreationListener mockCreationListener;

  public void setup() {
    if (mockCreationListener == null) {
      mockCreationListener = (mock, settings) -> mockitoMocks.add(mock);
      Mockito.framework().addListener(mockCreationListener);
    }
  }

  public void cleanupAndTearDown() throws Exception {
    if (mockCreationListener != null) {
      Mockito.framework().removeListener(mockCreationListener);
      mockCreationListener = null;
    }

    resetMocks();
    resetWellKnownThreadLocals();
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
