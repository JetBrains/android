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
package com.android.tools.idea.tests.gui.framework;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class TestGroupFilterTest {

  private Description suite;

  @Before
  public void setUp() {
    suite = Description.createSuiteDescription("all the tests");
    for (Class testClass : TestGroupFilterTest.class.getDeclaredClasses()) {
      Description classDescription = Description.createSuiteDescription(testClass);
      suite.addChild(classDescription);
      for (Method method : testClass.getDeclaredMethods()) {
        Description methodDescription = Description.createTestDescription(testClass, method.getName(), method.getAnnotations());
        classDescription.addChild(methodDescription);
      }
    }
  }

  @Test
  public void shouldRun_default() throws Exception {
    TestGroupFilter defaultFilter = new TestGroupFilter(TestGroup.DEFAULT);
    assertThat(methodNamesToRun(defaultFilter)).containsExactly("defaultMethod");
    assertThat(classNamesToRun(defaultFilter)).containsExactly("DefaultTest");
    assertThat(defaultFilter.shouldRun(suite)).isTrue();
  }

  @Test
  public void shouldRun_theme() throws Exception {
    TestGroupFilter themeFilter = new TestGroupFilter(TestGroup.THEME);
    assertThat(methodNamesToRun(themeFilter)).containsExactly("themeMethod");
    assertThat(classNamesToRun(themeFilter)).containsExactly("ThemeTest");
    assertThat(themeFilter.shouldRun(suite)).isTrue();
  }

  @Test
  public void shouldRun_qa() throws Exception {
    TestGroupFilter qaFilter = new TestGroupFilter(TestGroup.QA);
    assertThat(methodNamesToRun(qaFilter)).containsExactly("qaMethodInDefaultTest", "qaMethodInThemeTest");
    assertThat(classNamesToRun(qaFilter)).containsExactly("DefaultTest", "ThemeTest");
    assertThat(qaFilter.shouldRun(suite)).isTrue();
  }

  @Test
  public void shouldRun_editing() throws Exception {
    TestGroupFilter editingFilter = new TestGroupFilter(TestGroup.EDITING);
    assertThat(methodNamesToRun(editingFilter)).isEmpty();
    assertThat(classNamesToRun(editingFilter)).isEmpty();
    assertThat(editingFilter.shouldRun(suite)).isFalse();
  }

  private List<String> classNamesToRun(Filter filter) {
    return suite.getChildren().stream()
      .filter(filter::shouldRun)
      .map(Description::getTestClass)
      .map(Class::getSimpleName)
      .collect(Collectors.toList());
  }

  private List<String> methodNamesToRun(Filter filter) {
    return suite.getChildren().stream()
      .flatMap(d -> d.getChildren().stream())
      .filter(filter::shouldRun)
      .map(Description::getMethodName)
      .collect(Collectors.toList());
  }

  private static class DefaultTest {
    void defaultMethod() {}
    @RunIn(TestGroup.QA) void qaMethodInDefaultTest() {}
  }

  @RunIn(TestGroup.THEME) private static class ThemeTest {
    void themeMethod() {}
    @RunIn(TestGroup.QA) void qaMethodInThemeTest() {}
  }
}
