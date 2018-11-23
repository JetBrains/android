/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_MULTI_TYPE_APPLICATION_STATEMENT_DOES_NOT_THROW_EXCEPTION;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_PARSING_WITH_COMPACT_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_PARSING_WITH_DEPENDENCY_ON_ROOT;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_PARSING_WITH_MAP_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_SET_NAME_ON_COMPACT_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITHOUT_CONFIGURATION;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITH_CONFIGURATION;
import static com.android.tools.idea.gradle.dsl.TestFileName.MODULE_DEPENDENCY_SET_NAME_WITH_PATH_HAVING_SAME_SEGMENT_NAMES;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link DependenciesModelImpl} and {@link ModuleDependencyModelImpl}.
 */
public class ModuleDependencyTest extends GradleFileModelTestCase {
  @Test
  public void testParsingWithCompactNotation() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_PARSING_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    assertMatches(expected, dependencies.get(0));
  }

  @Test
  public void testParsingWithDependencyOnRoot() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_PARSING_WITH_DEPENDENCY_ON_ROOT);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":";
    assertMatches(expected, actual);

    assertEquals("", actual.name());
  }

  @Test
  public void testParsingWithMapNotation() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_PARSING_WITH_MAP_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(3);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":androidlib1";
    expected.configuration = "flavor1Release";
    assertMatches(expected, dependencies.get(0));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":androidlib2";
    expected.configuration = "flavor2Release";
    assertMatches(expected, dependencies.get(1));

    expected.reset();

    expected.configurationName = "runtime";
    expected.path = ":javalib2";
    assertMatches(expected, dependencies.get(2));
  }

  @Test
  public void testSetNameOnCompactNotation() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_ON_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    assertMatches(expected, dependency);
  }

  @Test
  public void testSetNameOnMapNotationWithConfiguration() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITH_CONFIGURATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    expected.configuration = "flavor1Release";
    assertMatches(expected, dependency);
  }

  @Test
  public void testSetNameOnMapNotationWithoutConfiguration() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITHOUT_CONFIGURATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    assertMatches(expected, dependency);
  }

  @Test
  public void testSetNameWithPathHavingSameSegmentNames() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_WITH_PATH_HAVING_SAME_SEGMENT_NAMES);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("helloWorld");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":name:helloWorld";
    assertMatches(expected, actual);

    assertEquals("helloWorld", actual.name());
  }

  @Test
  public void testReset() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    assertMatches(expected, dependency);
  }

  // Test for b/68188327
  @Test
  public void testMultiTypeApplicationStatementDoesNotThrowException() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_MULTI_TYPE_APPLICATION_STATEMENT_DOES_NOT_THROW_EXCEPTION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();

    // Note: this is not correct behaviour, this tests that no exception occurs.
    // TODO(b/69115152): fix the implementation of this
    assertThat(dependencies).hasSize(0);
  }

  private static void assertMatches(@NotNull ExpectedModuleDependency expected, @NotNull ModuleDependencyModel actual) {
    assertEquals("configurationName", expected.configurationName, actual.configurationName());
    assertEquals("path", expected.path, actual.path().forceString());
    assertEquals("configuration", expected.configuration, actual.configuration().toString());
  }
}
