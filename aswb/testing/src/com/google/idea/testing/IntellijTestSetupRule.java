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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.util.ReflectionUtil;
import java.io.File;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

/**
 * Runs before and after each test, performing the checks in {@link
 * com.intellij.testFramework.UsefulTestCase}
 */
public class IntellijTestSetupRule extends ExternalResource {

  private static final Set<?> DELETE_ON_EXIT_HOOK_DOT_FILES;
  private static final Class<?> DELETE_ON_EXIT_HOOK_CLASS;

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");

    try {
      Class<?> aClass = Class.forName("java.io.DeleteOnExitHook");
      Set<?> files = ReflectionUtil.getStaticFieldValue(aClass, Set.class, "files");
      DELETE_ON_EXIT_HOOK_CLASS = aClass;
      DELETE_ON_EXIT_HOOK_DOT_FILES = files;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Disposable testRootDisposable;

  private String oldPluginPathProperty;

  @Override
  protected void before() throws Throwable {
    if (!isRunThroughBlaze()) {
      // If running directly through the IDE, don't try to load plugins from the sandbox environment
      // Instead we'll rely on the slightly more hermetic module classpath
      oldPluginPathProperty = System.getProperty(PathManager.PROPERTY_PLUGINS_PATH);
      System.setProperty(PathManager.PROPERTY_PLUGINS_PATH, "/dev/null");
    }
    testRootDisposable = Disposer.newDisposable();
  }

  @Override
  protected void after() {
    if (oldPluginPathProperty != null) {
      System.setProperty(PathManager.PROPERTY_PLUGINS_PATH, oldPluginPathProperty);
    } else {
      System.clearProperty(PathManager.PROPERTY_PLUGINS_PATH);
    }
    try {
      Disposer.dispose(testRootDisposable);
      cleanupSwingDataStructures();
      cleanupDeleteOnExitHookList();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isRunThroughBlaze() {
    return System.getenv("JAVA_RUNFILES") != null;
  }

  private static void cleanupSwingDataStructures() throws Exception {
    Object manager =
        ReflectionUtil.getDeclaredMethod(
                Class.forName("javax.swing.KeyboardManager"), "getCurrentManager")
            .invoke(null);
    Map<?, ?> componentKeyStrokeMap =
        ReflectionUtil.getField(
            manager.getClass(), manager, Hashtable.class, "componentKeyStrokeMap");
    componentKeyStrokeMap.clear();
    Map<?, ?> containerMap =
        ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "containerMap");
    containerMap.clear();
  }

  private static void cleanupDeleteOnExitHookList() throws Exception {
    // try to reduce file set retained by java.io.DeleteOnExitHook
    List<String> list;
    synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
      if (DELETE_ON_EXIT_HOOK_DOT_FILES.isEmpty()) {
        return;
      }
      list =
          DELETE_ON_EXIT_HOOK_DOT_FILES
              .stream()
              .filter(p -> p instanceof String)
              .map(p -> (String) p)
              .collect(Collectors.toList());
    }
    for (int i = list.size() - 1; i >= 0; i--) {
      String path = list.get(i);
      if (FileSystemUtil.getAttributes(path) == null || new File(path).delete()) {
        synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
          DELETE_ON_EXIT_HOOK_DOT_FILES.remove(path);
        }
      }
    }
  }
}
