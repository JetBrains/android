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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link DependenciesModel} and {@link ModuleDependencyModel}.
 */
public class ModuleDependencyTest extends GradleFileModelTestCase {
  public void testParsingWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':javalib1')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    expected.assertMatches(dependencies.get(0));
  }

  public void testParsingWithDependencyOnRoot() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":";
    expected.assertMatches(actual);

    assertEquals("", actual.name());
  }

  public void testParsingWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':androidlib1', configuration: 'flavor1Release')\n" +
                  "    compile project(path: ':androidlib2', configuration: 'flavor2Release')\n" +
                  "    runtime project(path: ':javalib2')\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(3);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":androidlib1";
    expected.configuration = "flavor1Release";
    expected.assertMatches(dependencies.get(0));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":androidlib2";
    expected.configuration = "flavor2Release";
    expected.assertMatches(dependencies.get(1));

    expected.reset();

    expected.configurationName = "runtime";
    expected.path = ":javalib2";
    expected.assertMatches(dependencies.get(2));
  }

  public void testSetNameOnCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':javalib1')\n" +
                  "}";
    writeToBuildFile(text);

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
    expected.assertMatches(dependency);
  }

  public void testSetNameOnMapNotationWithConfiguration() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':androidlib1', configuration: 'flavor1Release')\n" +
                  "}";

    writeToBuildFile(text);

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
    expected.assertMatches(dependency);
  }

  public void testSetNameOnMapNotationWithoutConfiguration() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':androidlib1')\n" +
                  "}";

    writeToBuildFile(text);

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
    expected.assertMatches(dependency);
  }

  public void testSetNameWithPathHavingSameSegmentNames() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':name:name')\n" +
                  "}";

    writeToBuildFile(text);

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
    expected.assertMatches(actual);

    assertEquals("helloWorld", actual.name());
  }

  public void testReset() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':javalib1')\n" +
                  "}";
    writeToBuildFile(text);

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
    expected.assertMatches(dependency);
  }

  public static class ExpectedModuleDependency {
    public String configurationName;
    public String path;
    public String configuration;

    public void assertMatches(@NotNull ModuleDependencyModel actual) {
      assertEquals("configurationName", configurationName, actual.configurationName());
      assertEquals("path", path, actual.path());
      assertEquals("configuration", configuration, actual.configuration());
    }

    public void reset() {
      configurationName = path = configuration = null;
    }
  }
}
