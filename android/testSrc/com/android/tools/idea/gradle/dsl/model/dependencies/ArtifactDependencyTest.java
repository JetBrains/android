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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.*;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.*;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DependenciesModelImpl} and {@link ArtifactDependencyModelImpl}.
 */
public class ArtifactDependencyTest extends GradleFileModelTestCase {

  private static final String CONFIGURATION_CLOSURE_PARENS =
    "dependencies {\n" +
    "  compile('org.hibernate:hibernate:3.1') {\n" +
    "     //in case of versions conflict '3.1' version of hibernate wins:\n" +
    "     force = true\n" +
    "\n" +
    "     //excluding a particular transitive dependency:\n" +
    "     exclude module: 'cglib' //by artifact name\n" +
    "     exclude group: 'org.jmock' //by group\n" +
    "     exclude group: 'org.unwanted', module: 'iAmBuggy' //by both name and group\n" +
    "\n" +
    "     //disabling all transitive dependencies of this dependency\n" +
    "     transitive = false\n" +
    "   }" +
    "}";

  private static final String CONFIGURATION_CLOSURE_NO_PARENS =
    "dependencies {\n" +
    "  compile 'org.hibernate:hibernate:3.1', {\n" +
    "     //in case of versions conflict '3.1' version of hibernate wins:\n" +
    "     force = true\n" +
    "\n" +
    "     //excluding a particular transitive dependency:\n" +
    "     exclude module: 'cglib' //by artifact name\n" +
    "     exclude group: 'org.jmock' //by group\n" +
    "     exclude group: 'org.unwanted', module: 'iAmBuggy' //by both name and group\n" +
    "\n" +
    "     //disabling all transitive dependencies of this dependency\n" +
    "     transitive = false\n" +
    "   }" +
    "}";

  private static final String CONFIGURATION_CLOSURE_WITHIN_PARENS =
    "dependencies {\n" +
    "  compile('org.hibernate:hibernate:3.1', {\n" +
    "     //in case of versions conflict '3.1' version of hibernate wins:\n" +
    "     force = true\n" +
    "\n" +
    "     //excluding a particular transitive dependency:\n" +
    "     exclude module: 'cglib' //by artifact name\n" +
    "     exclude group: 'org.jmock' //by group\n" +
    "     exclude group: 'org.unwanted', module: 'iAmBuggy' //by both name and group\n" +
    "\n" +
    "     //disabling all transitive dependencies of this dependency\n" +
    "     transitive = false\n" +
    "   })" +
    "}";

  public void testParsingWithCompactNotationAndConfigurationClosure_parens() throws IOException {
    doTestParsingConfigurationVersion(CONFIGURATION_CLOSURE_PARENS);
  }

  public void testParsingWithCompactNotationAndConfigurationClosure_noParens() throws IOException {
    doTestParsingConfigurationVersion(CONFIGURATION_CLOSURE_NO_PARENS);
  }

  public void testParsingWithCompactNotationAndConfigurationClosure_withinParens() throws IOException {
    doTestParsingConfigurationVersion(CONFIGURATION_CLOSURE_WITHIN_PARENS);
  }

  private void doTestParsingConfigurationVersion(@NotNull String text) throws IOException {
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ArtifactDependencyModel dependency = dependencies.get(0);
    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependency);

    verifyDependencyConfiguration(dependency.configuration());
  }

  private static void verifyDependencyConfiguration(@Nullable DependencyConfigurationModel configuration) {
    assertNotNull(configuration);

    assertEquals(Boolean.TRUE, configuration.force().value());
    assertEquals(Boolean.FALSE, configuration.transitive().value());

    List<ExcludedDependencyModel> excludedDependencies = configuration.excludes();
    assertThat(excludedDependencies).hasSize(3);

    ExcludedDependencyModel first = excludedDependencies.get(0);
    assertNull(first.group().value());
    assertEquals("cglib", first.module().value());

    ExcludedDependencyModel second = excludedDependencies.get(1);
    assertEquals("org.jmock", second.group().value());
    assertNull(second.module().value());

    ExcludedDependencyModel third = excludedDependencies.get(2);
    assertEquals("org.unwanted", third.group().value());
    assertEquals("iAmBuggy", third.module().value());
  }

  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_parens() throws IOException {
    doTestSetVersionWithConfigurationClosure(CONFIGURATION_CLOSURE_PARENS);
  }

  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_noParens() throws IOException {
    doTestSetVersionWithConfigurationClosure(CONFIGURATION_CLOSURE_NO_PARENS);
  }

  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_withinParens() throws IOException {
    doTestSetVersionWithConfigurationClosure(CONFIGURATION_CLOSURE_WITHIN_PARENS);
  }

  private void doTestSetVersionWithConfigurationClosure(@NotNull String text) throws IOException {
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ArtifactDependencyModel hibernate = dependencies.get(0);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(hibernate);
    verifyDependencyConfiguration(hibernate.configuration());

    hibernate.setVersion("3.0");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    hibernate = dependencies.get(0);

    expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.0");
    expected.assertMatches(hibernate);
    verifyDependencyConfiguration(hibernate.configuration());
  }

  public void testGetOnlyArtifacts() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    compile('com.google.guava:guava:18.0')\n" +
                  "    compile project(':javaLib')\n" +
                  "    compile fileTree('libs')\n" +
                  "    compile files('lib.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  public void testParsingWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    runtime 'com.google.guava:guava:18.0'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedArtifactDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(2));
  }

  public void testParsingWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk14");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(0));
  }

  public void testAddDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpecImpl newDependency = new ArtifactDependencySpecImpl("appcompat-v7", "com.android.support", "22.1.1");
    dependenciesModel.addArtifact(COMPILE, newDependency);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk14");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  public void testAddDependencyWithConfigurationClosure() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpecImpl newDependency =
      new ArtifactDependencySpecImpl("espresso-contrib", "com.android.support.test.espresso", "2.2.2");
    dependenciesModel.addArtifact(ANDROID_TEST_COMPILE,
                                  newDependency,
                                  ImmutableList.of(new ArtifactDependencySpecImpl("support-v4", "com.android.support", null),
                                                   new ArtifactDependencySpecImpl("support-annotations", "com.android.support", null),
                                                   new ArtifactDependencySpecImpl("recyclerview-v7", "com.android.support", null),
                                                   new ArtifactDependencySpecImpl("design", "com.android.support", null)));

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ArtifactDependencyModel jdkDependency = dependencies.get(0);
    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk14");
    expected.setExtension("jar");
    expected.assertMatches(jdkDependency);
    assertNull(jdkDependency.configuration());

    ArtifactDependencyModel espressoDependency = dependencies.get(1);
    expected = new ExpectedArtifactDependency(ANDROID_TEST_COMPILE, "espresso-contrib", "com.android.support.test.espresso", "2.2.2");
    expected.assertMatches(espressoDependency);

    DependencyConfigurationModel configuration = espressoDependency.configuration();
    assertNotNull(configuration);

    configuration.excludes();

    List<ExcludedDependencyModel> excludedDependencies = configuration.excludes();
    assertThat(excludedDependencies).hasSize(4);

    ExcludedDependencyModel first = excludedDependencies.get(0);
    assertEquals("com.android.support", first.group().value());
    assertEquals("support-v4", first.module().value());

    ExcludedDependencyModel second = excludedDependencies.get(1);
    assertEquals("com.android.support", second.group().value());
    assertEquals("support-annotations", second.module().value());

    ExcludedDependencyModel third = excludedDependencies.get(2);
    assertEquals("com.android.support", third.group().value());
    assertEquals("recyclerview-v7", third.module().value());

    ExcludedDependencyModel fourth = excludedDependencies.get(3);
    assertEquals("com.android.support", fourth.group().value());
    assertEquals("design", fourth.module().value());
  }


  public void testSetVersionOnDependencyWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel appCompat = dependencies.get(0);
    appCompat.setVersion("1.2.3");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  public void testSetVersionOnDependencyWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel guice = dependencies.get(0);
    guice.setVersion("1.2.3");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  public void testParseDependenciesWithCompactNotationInSingleLine() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime 'org.springframework:spring-core:2.5', 'org.springframework:spring-aop:2.5'\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  public void testParseDependenciesWithCompactNotationInSingleLineWithComments() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime /* Hey */ 'org.springframework:spring-core:2.5', /* Hey */ 'org.springframework:spring-aop:2.5'\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
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
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }


  public void testReset() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel guice = dependencies.get(0);
    guice.setVersion("1.2.3");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));
  }

  public void testRemoveDependencyWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    runtime 'com.google.guava:guava:18.0'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel guava = dependencies.get(1);
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));
  }

  public void testRemoveDependencyWithCompactNotationAndSingleConfigurationName() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime /* Hey */ 'org.springframework:spring-core:2.5', /* Hey */ 'org.springframework:spring-aop:2.5'\n" +
                  "    test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel springAop = dependencies.get(1);
    dependenciesModel.remove(springAop);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));
  }

  public void testRemoveDependencyWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "    compile group: 'com.google.guava', name: 'guava', version: '18.0'\n" +
                  "    compile group: 'com.android.support', name: 'appcompat-v7', version: '22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel guava = dependencies.get(1);
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("compile", "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
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

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel guava = dependencies.get(1);
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  public void testContains() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "    compile group: 'com.google.guava', name: 'guava', version: '18.0'\n" +
                  "    compile group: 'com.android.support', name: 'appcompat-v7', version: '22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpecImpl guavaSpec = new ArtifactDependencySpecImpl("guava", "com.google.guava", "18.0");
    ArtifactDependencySpecImpl guiceSpec = new ArtifactDependencySpecImpl("guice", "com.google.code.guice", "2.0");

    assertTrue(dependenciesModel.containsArtifact(COMPILE, guavaSpec));
    assertFalse(dependenciesModel.containsArtifact(COMPILE, guiceSpec));
    assertFalse(dependenciesModel.containsArtifact(CLASSPATH, guavaSpec));
  }

  public void testParseCompactNotationWithVariables() throws IOException {
    String text = "ext {\n" +
                  "    appcompat = 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    guavaVersion = '18.0'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    compile appcompat\n" +
                  "    runtime \"com.google.guava:guava:$guavaVersion\"\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    ArtifactDependencyModel appcompatDependencyModel = dependencies.get(0);
    expected.assertMatches(appcompatDependencyModel);
    GradleNotNullValue<String> appcompatDependency = appcompatDependencyModel.compactNotation();
    verifyGradleValue(appcompatDependency, "dependencies.compile", "appcompat");
    assertEquals(expected.compactNotation(), appcompatDependency.value());
    Map<String, GradleNotNullValue<Object>> appcompatResolvedVariables = appcompatDependency.getResolvedVariables();
    assertEquals(1, appcompatResolvedVariables.size());

    GradleNotNullValue<Object> appcompatVariable = appcompatResolvedVariables.get("appcompat");
    assertNotNull(appcompatVariable);
    verifyGradleValue(appcompatVariable, "ext.appcompat", "'com.android.support:appcompat-v7:22.1.1'");
    assertEquals("com.android.support:appcompat-v7:22.1.1", appcompatVariable.value());
    assertEquals(0, appcompatVariable.getResolvedVariables().size());


    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    GradleNotNullValue<String> guavaDependency = guavaDependencyModel.compactNotation();
    verifyGradleValue(guavaDependency, "dependencies.runtime", "\"com.google.guava:guava:$guavaVersion\"");
    assertEquals(expected.compactNotation(), guavaDependency.value());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    verifyGradleValue(guavaVersionVariable, "ext.guavaVersion", "'18.0'");
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());
  }

  public void testParseMapNotationWithVariables() throws IOException {
    String text = "ext {\n" +
                  "    guavaVersion = '18.0'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    compile group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\"\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(0);
    expected.assertMatches(guavaDependencyModel);
    GradleNotNullValue<String> guavaDependency = guavaDependencyModel.compactNotation();
    verifyGradleValue(guavaDependency, "dependencies.compile", "group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\"");
    assertEquals(expected.compactNotation(), guavaDependency.value());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    verifyGradleValue(guavaVersionVariable, "ext.guavaVersion", "'18.0'");
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());

    // Now test that resolved variables are not reported for group and name properties.
    GradleNullableValue<String> group = guavaDependencyModel.group();
    assertEquals("com.google.guava", group.value());
    assertEquals(0, group.getResolvedVariables().size());

    GradleNotNullValue<String> name = guavaDependencyModel.name();
    assertEquals("guava", name.value());
    assertEquals(0, name.getResolvedVariables().size());

    // and thee guavaVersion variable is reported for version property.
    GradleNullableValue<String> version = guavaDependencyModel.version();
    verifyGradleValue(version, "dependencies.compile.version", "\"$guavaVersion\"");
    assertEquals("18.0", version.value());
    guavaResolvedVariables = version.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    verifyGradleValue(guavaVersionVariable, "ext.guavaVersion", "'18.0'");
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());
  }

  public void testParseCompactNotationClosureWithVariables() throws IOException {
    String text = "ext {\n" +
                  "    appcompat = 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "    guavaVersion = '18.0'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    compile(appcompat, \"com.google.guava:guava:$guavaVersion\") {\n" +
                  "    }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    ArtifactDependencyModel appcompatDependencyModel = dependencies.get(0);
    expected.assertMatches(appcompatDependencyModel);
    GradleNotNullValue<String> appcompatDependency = appcompatDependencyModel.compactNotation();
    verifyGradleValue(appcompatDependency, "dependencies.compile.compile", "appcompat");
    assertEquals(expected.compactNotation(), appcompatDependency.value());
    Map<String, GradleNotNullValue<Object>> appcompatResolvedVariables = appcompatDependency.getResolvedVariables();
    assertEquals(1, appcompatResolvedVariables.size());

    GradleNotNullValue<Object> appcompatVariable = appcompatResolvedVariables.get("appcompat");
    assertNotNull(appcompatVariable);
    verifyGradleValue(appcompatVariable, "ext.appcompat", "'com.android.support:appcompat-v7:22.1.1'");
    assertEquals("com.android.support:appcompat-v7:22.1.1", appcompatVariable.value());
    assertEquals(0, appcompatVariable.getResolvedVariables().size());


    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    GradleNotNullValue<String> guavaDependency = guavaDependencyModel.compactNotation();
    verifyGradleValue(guavaDependency, "dependencies.compile.compile", "\"com.google.guava:guava:$guavaVersion\"");
    assertEquals(expected.compactNotation(), guavaDependency.value());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    verifyGradleValue(guavaVersionVariable, "ext.guavaVersion", "'18.0'");
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());
  }

  public void testParseMapNotationClosureWithVariables() throws IOException {
    String text = "ext {\n" +
                  "    guavaVersion = '18.0'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    compile(group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\") {\n" +
                  "    }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(0);
    expected.assertMatches(guavaDependencyModel);
    GradleNotNullValue<String> guavaDependency = guavaDependencyModel.compactNotation();
    verifyGradleValue(guavaDependency, "dependencies.compile.compile",
                      "(group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\")");
    assertEquals(expected.compactNotation(), guavaDependency.value());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    verifyGradleValue(guavaVersionVariable, "ext.guavaVersion", "'18.0'");
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());

    // Now test that resolved variables are not reported for group and name properties.
    GradleNullableValue<String> group = guavaDependencyModel.group();
    assertEquals("com.google.guava", group.value());
    assertEquals(0, group.getResolvedVariables().size());

    GradleNotNullValue<String> name = guavaDependencyModel.name();
    assertEquals("guava", name.value());
    assertEquals(0, name.getResolvedVariables().size());

    // and thee guavaVersion variable is reported for version property.
    GradleNullableValue<String> version = guavaDependencyModel.version();
    verifyGradleValue(version, "dependencies.compile.compile.version", "\"$guavaVersion\"");
    assertEquals("18.0", version.value());
    guavaResolvedVariables = version.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    verifyGradleValue(guavaVersionVariable, "ext.guavaVersion", "'18.0'");
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());
  }

  public void testNonDependencyCodeInDependenciesSection() throws IOException {
    String text = "dependencies {\n" +
                  "  compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "  runtime group: 'com.google.guava', name: 'guava', version: '18.0'\n" +
                  "  apply plugin:'com.test.xyz'\n" + // this line should not affect the dependencies parsing
                  "  testCompile('org.hibernate:hibernate:3.1') { \n" +
                  "    force = true\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(2));
  }

  public void testReplaceDependencyByPsiElement() throws IOException {
    String text = "dependencies {\n" +
                  "  compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies().artifacts();

    assertThat(dependencies).hasSize(1);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(0));
  }

  public void testReplaceDependencyByChildElement() throws IOException {
    String text = "dependencies {\n" +
                  "  test 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "  compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "  testCompile('org.hibernate:hibernate:3.1') { \n" +
                  "    force = true\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).compactNotation().getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies().artifacts();

    assertThat(dependencies).hasSize(3);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  public void testReplaceDependencyFailsIsPsiElementIsNotFound() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile('org.hibernate:hibernate:3.1') { \n" +
                  "    force = true\n" +
                  "  }\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0:jdk15@jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement().getParent(), newDep);
    assertFalse(result);

    result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).getPsiElement().getContainingFile(), newDep);
    assertFalse(result);

    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies().artifacts();

    // Make sure none of the dependencies have changed.
    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));
  }

  public void testReplaceDependencyUsingMapNotationWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies().artifacts();

    // Make sure the new dependency is correct.
    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(0));
  }

  public void testReplaceDependencyUsingMapNotationAddingFields() throws IOException {
    String text = "dependencies {\n" +
                  "    compile name: 'name'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "name", null, null);
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0:class@aar");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.setClassifier("class");
    expected.setExtension("aar");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  public void testReplaceDependencyUsingMapNotationDeleteFields() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0', classifier: 'high', ext: 'bleh'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.setClassifier("high");
    expected.setExtension("bleh");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:+");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  public void testReplaceDependencyInArgumentList() throws IOException {
    String text = "dependencies {\n" +
                  "  compile('com.google.code.guice:guice:1.0', 'com.google.guava:guava:18.0')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));
    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:+");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(buildModel.dependencies().artifacts().get(1));
  }

  public void testReplaceMethodDependencyWithClosure() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile('org.hibernate:hibernate:3.1') { \n" +
                  "    force = true\n" + // Note: We currently preserve the whole closure.
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:+");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  public void testReplaceApplicationDependencies() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0',  'com.google.guava:guava:+'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(dependencies.get(1));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("org.hibernate:hibernate:3.1");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  public static class ExpectedArtifactDependency extends ArtifactDependencySpecImpl {
    @NotNull public String configurationName;

    public ExpectedArtifactDependency(@NotNull String configurationName,
                                      @NotNull String name,
                                      @Nullable String group,
                                      @Nullable String version) {
      super(name, group, version);
      this.configurationName = configurationName;
    }

    public void assertMatches(@NotNull ArtifactDependencyModel actual) {
      assertEquals("configurationName", configurationName, actual.configurationName());
      assertEquals("group", getGroup(), actual.group().value());
      assertEquals("name", getName(), actual.name().value());
      assertEquals("version", getVersion(), actual.version().value());
      assertEquals("classifier", getClassifier(), actual.classifier().value());
      assertEquals("extension", getExtension(), actual.extension().value());
    }

    public boolean matches(@NotNull ArtifactDependencyModel dependency) {
      return configurationName.equals(dependency.configurationName()) &&
             getName().equals(dependency.name().value()) &&
             Objects.equal(getGroup(), dependency.group().value()) &&
             Objects.equal(getVersion(), dependency.version().value()) &&
             Objects.equal(getClassifier(), dependency.classifier().value()) &&
             Objects.equal(getExtension(), dependency.extension().value());
    }
  }
}
