/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.tests.gui.framework.aspects.AspectsAgentLogTest;
import com.google.common.base.Verify;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.tools.idea.tests.gui.framework.GuiTests.GUI_TESTS_RUNNING_IN_SUITE_PROPERTY;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

/**
 * <p>{@link Runner} that finds and runs classes {@link RunWith} {@link GuiTestRemoteRunner}.</p>
 *
 * <p>This runner will specify a {@link TestGroupFilter} when the {@code ui.test.group}
 * property is set to a {@link TestGroup} name. This runner will also specify a
 * {@link SingleClassFilter} when the {@code ui.test.class} property is set to
 * a fully qualified class name of a UI test class. When both filters are set,
 * each test needs to pass both filters to run.</p>
 *
 */
public class GuiTestSuiteRunner extends Suite {

  /** The name of a property specifying a {@link TestGroup} to run. If unspecified, tests will not be filtered based on group. */
  private static final String TEST_GROUP_PROPERTY_NAME = "ui.test.group";
  /** The name of a property specifying a specific class whose fully qualified class name matches the property value to run.
    * If unspecified, tests will not be filtered by the class name of their containing class. */
  private static final String TEST_CLASS_PROPERTY_NAME = "ui.test.class";

  public GuiTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError, IOException {
    super(builder, suiteClass, getGuiTestClasses(suiteClass, System.getProperty(TEST_GROUP_PROPERTY_NAME)));
    System.setProperty(GUI_TESTS_RUNNING_IN_SUITE_PROPERTY, "true");
    try {
      String testGroupProperty = System.getProperty(TEST_GROUP_PROPERTY_NAME);
      if (testGroupProperty != null) {
        filter(new TestGroupFilter(TestGroup.valueOf(testGroupProperty)));
      }

      String testClassProperty = System.getProperty(TEST_CLASS_PROPERTY_NAME);
      if (testClassProperty != null) {
        filter(new SingleClassFilter(testClassProperty));
      }
    } catch (NoTestsRemainException e) {
      throw new InitializationError(e);
    }
  }

  @NotNull
  private static Class<?>[] getGuiTestClasses(@NotNull Class<?> suiteClass, @Nullable String testGroup)
    throws InitializationError, IOException {
    List<Class<?>> guiTestClasses = new ArrayList<>();
    List<File> guiTestClassFiles = new ArrayList<>();
    File parentDir = getParentDir(suiteClass);

    String packagePath = suiteClass.getPackage().getName().replace('.', File.separatorChar);
    int packagePathIndex = parentDir.getPath().indexOf(packagePath);
    Verify.verify(parentDir.getPath().endsWith(packagePath));
    String testDirPath = parentDir.getPath().substring(0, packagePathIndex);

    findPotentialGuiTestClassFiles(parentDir, guiTestClassFiles);
    ClassLoader classLoader = suiteClass.getClassLoader();
    for (File classFile : guiTestClassFiles) {
      String path = classFile.getPath();
      String className = path.substring(testDirPath.length(), path.indexOf(DOT_CLASS)).replace(File.separatorChar, '.');
      try {
        Class<?> testClass = classLoader.loadClass(className);
        if (isGuiTest(testClass)) {
          guiTestClasses.add(testClass);
        }
      }
      catch (ClassNotFoundException e) {
        throw new InitializationError(e);
      }
    }
    if (testGroup != null && TestGroup.valueOf(testGroup) == TestGroup.UNRELIABLE) {
      // Add AspectsAgentLogTest after other tests since it reads a log written while they run.
      guiTestClasses.add(AspectsAgentLogTest.class);
    }
    return guiTestClasses.toArray(new Class<?>[guiTestClasses.size()]);
  }

  private static boolean isGuiTest(Class<?> testClass) {
    RunWith runWith = testClass.getAnnotation(RunWith.class);
    if (runWith == null) {
      return false;
    }

    String runWithClassName = runWith.value().getSimpleName();

    // True if running with GuiTestRemoteRunner
    if (runWithClassName.equals(GuiTestRemoteRunner.class.getSimpleName())) {
      return true;
    }
    return false;
  }

  private static void findPotentialGuiTestClassFiles(@NotNull File directory, @NotNull List<File> guiTestClassFiles) {
    File[] children = notNullize(directory.listFiles());
    Arrays.sort(children);  // avoid file-system-dependent order
    for (File child : children) {
      if (child.isDirectory()) {
        findPotentialGuiTestClassFiles(child, guiTestClassFiles);
        continue;
      }
      if (child.isFile() && !child.isHidden() && child.getName().endsWith("Test.class")) {
        guiTestClassFiles.add(child);
      }
    }
  }

  @NotNull
  private static File getParentDir(@NotNull Class<?> clazz) throws InitializationError {
    URL classUrl = clazz.getResource(clazz.getSimpleName() + DOT_CLASS);
    try {
      return new File(classUrl.toURI()).getParentFile();
    }
    catch (URISyntaxException e) {
      throw new InitializationError(e);
    }
  }

}
