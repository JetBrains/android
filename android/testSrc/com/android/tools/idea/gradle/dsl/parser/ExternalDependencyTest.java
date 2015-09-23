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
package com.android.tools.idea.gradle.dsl.parser;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.parser.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.dsl.parser.CommonConfigurationNames.RUNTIME;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link ExternalDependency}.
 */
public class ExternalDependencyTest extends GradleBuildModelParserTestCase {
  public void testParsingWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    runtime 'com.google.guava:guava:18.0'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(3);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "com.android.support", "appcompat-v7", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "com.google.guava", "guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedExternalDependency("test", "org.gradle.test.classifiers", "service", "1.0");
    expected.classifier = "jdk15";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(2));
  }

  public void testParsingWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "org.gradle.test.classifiers", "service", "1.0");
    expected.classifier = "jdk14";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(0));
  }

  public void testAddDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    NewExternalDependency newDependency = new NewExternalDependency(COMPILE, "com.android.support", "appcompat-v7", "22.1.1");
    buildModel.getDependencies().add(newDependency);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "org.gradle.test.classifiers", "service", "1.0");
    expected.classifier = "jdk14";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(COMPILE, "com.android.support", "appcompat-v7", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  public void testAddDependencyToBuildFileWithoutDependenciesBlock() throws IOException {
    writeToBuildFile("");

    final GradleBuildModel buildModel = getGradleBuildModel();
    NewExternalDependency newDependency = new NewExternalDependency(COMPILE, "com.android.support", "appcompat-v7", "22.1.1");
    buildModel.getDependencies().add(newDependency);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "com.android.support", "appcompat-v7", "22.1.1");
    expected.assertMatches(dependencies.get(0));
  }

  public void testSetVersionOnDependencyWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();

    ExternalDependency appCompat = dependencies.get(0);
    appCompat.setVersion("1.2.3");

    assertTrue(buildModel.isModified());
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "com.android.support", "appcompat-v7", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  public void testSetVersionOnDependencyWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();

    ExternalDependency guice = dependencies.get(0);
    guice.setVersion("1.2.3");

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "com.google.code.guice", "guice", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  public void testParseDependenciesWithCompactNotationInSingleLine() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime /* Hey */ 'org.springframework:spring-core:2.5', /* Hey */ 'org.springframework:spring-aop:2.5'\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "org.springframework", "spring-core", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "org.springframework", "spring-aop", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  public void testParseDependenciesWithMapNotationUsingSingleConfigurationName() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime(\n" +
                  "        [group: 'org.springframework', name: 'spring-core', version: '2.5'],\n" +
                  "        [group: 'org.springframework', name: 'spring-aop', version: '2.5']\n" +
                  "    )\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "org.springframework", "spring-core", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "org.springframework", "spring-aop", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  public void testReset() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.getDependencies().getExternal();

    ExternalDependency guice = dependencies.get(0);
    guice.setVersion("1.2.3");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    dependencies = buildModel.getDependencies().getExternal();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "com.google.code.guice", "guice", "1.0");
    expected.assertMatches(dependencies.get(0));
  }

  public static class ExpectedExternalDependency extends NewExternalDependency {
    public ExpectedExternalDependency(@NotNull String configurationName,
                                      @NotNull String group,
                                      @NotNull String name,
                                      @NotNull String version) {
      super(configurationName, group, name, version);
    }

    public void assertMatches(@NotNull ExternalDependency actual) {
      assertEquals("configurationName", configurationName, actual.getConfigurationName());
      assertEquals("group", group, actual.getGroup());
      assertEquals("name", name, actual.getName());
      assertEquals("version", version, actual.getVersion());
      assertEquals("classifier", classifier, actual.getClassifier());
      assertEquals("extension", extension, actual.getExtension());
    }

    public boolean matches(@NotNull ExternalDependency model) {
      return configurationName.equals(model.getConfigurationName()) &&
             group.equals(model.getGroup()) &&
             name.equals(model.getName()) &&
             version.equals(model.getVersion()) &&
             Objects.equal(classifier, model.getClassifier()) &&
             Objects.equal(extension, model.getExtension());
    }
  }
}