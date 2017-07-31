/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.testutils.TestUtils;
import com.android.tools.idea.experimental.codeanalysis.datastructs.Modifier;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.intellij.openapi.application.PathManager;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.android.testutils.TestUtils.getWorkspaceFile;
import static org.junit.Assert.fail;

public class PublicMethodsBuildActionTest {

  @Test
  public void testOutputBuildActionAgainstGuava() throws IOException, ClassNotFoundException {
    testActionAgainstJar(OutputBuildAction.class, getGuavaJar());
  }

  @Test
  public void testSyncActionAgainstGuava() throws IOException, ClassNotFoundException {
    testActionAgainstJar(SyncAction.class, getGuavaJar());
  }

  private static void testActionAgainstJar(@NotNull Class<? extends BuildAction<?>> clazz, @NotNull JarFile jarFile)
    throws ClassNotFoundException {
    testInForbidenClasses(getNonPrivateMethods(clazz), getClassesNames(jarFile));
  }

  private static void testInForbidenClasses(@NotNull List<Method> nonPrivateMethods, @NotNull Set<String> forbidenClasses) {
    for (Method method : nonPrivateMethods) {
      testClassInForbidenClasses(method, method.getReturnType(), forbidenClasses);
      for (Class<?> clazz : method.getParameterTypes()) {
        testClassInForbidenClasses(method, clazz, forbidenClasses);
      }
    }
  }

  private static void testClassInForbidenClasses(@NotNull Method method, @NotNull Class<?> clazz, @NotNull Set<String> forbidenClasses) {
    String className = clazz.getCanonicalName();
    if (forbidenClasses.contains(className)) {
      fail(String.format("Class %s is used in public method %s of class %s.", className, method.getName(), method.getDeclaringClass().getName()));
    }
  }

  @NotNull
  private static List<Method> getNonPrivateMethods(@NotNull Class<?> clazz) {
    List<Method> list = new LinkedList<>();
    addNonPrivateMethodsToList(list, clazz);
    return list;
  }

  private static void addNonPrivateMethodsToList(@NotNull List<Method> list, @NotNull Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if (!Modifier.isPrivate(method.getModifiers())) {
        list.add(method);
      }
    }
    for (Class<?> innerClass : clazz.getDeclaredClasses()) {
      addNonPrivateMethodsToList(list, innerClass);
    }
  }

  @NotNull
  private static Set<String> getClassesNames(@NotNull JarFile jarFile) throws ClassNotFoundException {
    Set<String> classes = new HashSet<>();
    Enumeration <JarEntry> entries = jarFile.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
        String className = entry.getName().replaceAll("\\.class$", "").replace('/', '.');
        classes.add(className);
      }
    }
    return classes;
  }

  @NotNull
  private static JarFile getGuavaJar() throws IOException {
    String path = "prebuilts/tools/common/m2/repository";

    File prebuiltsRepoDir;
    if (TestUtils.runningFromBazel()) {
      // Based on EmbeddedDistributionPaths#findAndroidStudioLocalMavenRepoPaths:
      File tmp = new File(PathManager.getHomePath()).getParentFile().getParentFile();
      prebuiltsRepoDir = new File(tmp, path);
    }
    else {
      prebuiltsRepoDir = getWorkspaceFile(path);
    }

    return new JarFile(new File(prebuiltsRepoDir, "/com/google/guava/guava/19.0/guava-19.0.jar"));
  }
}
