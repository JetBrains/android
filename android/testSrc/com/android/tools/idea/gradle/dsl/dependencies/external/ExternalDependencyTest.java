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
package com.android.tools.idea.gradle.dsl.dependencies.external;

import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames.RUNTIME;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link ExternalDependency}.
 */
public class ExternalDependencyTest extends GradleFileModelTestCase {
  public void testParsingWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    runtime 'com.google.guava:guava:18.0'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(3);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedExternalDependency("test", "service", "org.gradle.test.classifiers", "1.0");
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

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
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
    ExternalDependencySpec newDependency = new ExternalDependencySpec("appcompat-v7", "com.android.support", "22.1.1");
    buildModel.dependencies().add(COMPILE, newDependency);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.classifier = "jdk14";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  public void testAddDependencyToBuildFileWithoutDependenciesBlock() throws IOException {
    writeToBuildFile("");

    final GradleBuildModel buildModel = getGradleBuildModel();
    ExternalDependencySpec newDependency = new ExternalDependencySpec("appcompat-v7", "com.android.support", "22.1.1");
    buildModel.dependencies().add(COMPILE, newDependency);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));
  }

  public void testSetVersionOnDependencyWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();

    ExternalDependency appCompat = dependencies.get(0);
    appCompat.version("1.2.3");

    assertTrue(buildModel.isModified());
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  public void testSetVersionOnDependencyWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();

    ExternalDependency guice = dependencies.get(0);
    guice.version("1.2.3");

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "guice", "com.google.code.guice", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  public void testParseDependenciesWithCompactNotationInSingleLine() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime /* Hey */ 'org.springframework:spring-core:2.5', /* Hey */ 'org.springframework:spring-aop:2.5'\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
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

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  public void testReset() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();

    ExternalDependency guice = dependencies.get(0);
    guice.version("1.2.3");

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

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(1);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));
  }

  public void testRemoveDependencyWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    runtime 'com.google.guava:guava:18.0'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(3);

    ExternalDependency guava = dependencies.get(1);
    buildModel.dependencies().remove(guava);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());
    buildModel.reparse();

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.classifier = "jdk15";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(1));
  }

  public void testRemoveDependencyWithCompactNotationAndSingleConfigurationName() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime /* Hey */ 'org.springframework:spring-core:2.5', /* Hey */ 'org.springframework:spring-aop:2.5'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(3);

    ExternalDependency springAop = dependencies.get(1);
    buildModel.dependencies().remove(springAop);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());
    buildModel.reparse();

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.classifier = "jdk15";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(1));
  }

  public void testRemoveDependencyWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "    compile group: 'com.google.guava', name: 'guava', version: '18.0'\n" +
                  "    compile group: 'com.android.support', name: 'appcompat-v7', version: '22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(3);

    ExternalDependency guava = dependencies.get(1);
    buildModel.dependencies().remove(guava);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());
    buildModel.reparse();

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency("compile", "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  public void testRemoveDependencyWithMapNotationAndSingleConfigurationName() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime(\n" +
                  "        [group: 'com.google.code.guice', name: 'guice', version: '1.0'],\n" +
                  "        [group: 'com.google.guava', name: 'guava', version: '18.0'],\n" +
                  "        [group: 'com.android.support', name: 'appcompat-v7', version: '22.1.1']\n" +
                  "    )\n" +
                  "}";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();

    List<ExternalDependency> dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(3);

    ExternalDependency guava = dependencies.get(1);
    buildModel.dependencies().remove(guava);

    assertTrue(buildModel.isModified());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    assertFalse(buildModel.isModified());
    buildModel.reparse();

    dependencies = buildModel.dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(RUNTIME, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(RUNTIME, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  public static class ExpectedExternalDependency extends ExternalDependencySpec {
    @NotNull public String configurationName;

    public ExpectedExternalDependency(@NotNull String configurationName,
                                      @NotNull String name,
                                      @Nullable String group,
                                      @Nullable String version) {
      super(name, group, version);
      this.configurationName = configurationName;
    }

    public void assertMatches(@NotNull ExternalDependency actual) {
      assertEquals("configurationName", configurationName, actual.configurationName());
      assertEquals("group", group, actual.group());
      assertEquals("name", name, actual.name());
      assertEquals("version", version, actual.version());
      assertEquals("classifier", classifier, actual.classifier());
      assertEquals("extension", extension, actual.extension());
    }

    public boolean matches(@NotNull ExternalDependency dependency) {
      return configurationName.equals(dependency.configurationName()) &&
             name.equals(dependency.name()) &&
             Objects.equal(group, dependency.group()) &&
             Objects.equal(version, dependency.version()) &&
             Objects.equal(classifier, dependency.classifier()) &&
             Objects.equal(extension, dependency.extension());
    }
  }
}