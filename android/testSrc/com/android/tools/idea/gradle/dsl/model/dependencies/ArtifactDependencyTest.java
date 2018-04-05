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
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.*;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
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

  @Test
  public void testParsingWithCompactNotationAndConfigurationClosure_parens() throws IOException {
    doTestParsingConfigurationVersion(CONFIGURATION_CLOSURE_PARENS);
  }

  @Test
  public void testParsingWithCompactNotationAndConfigurationClosure_noParens() throws IOException {
    doTestParsingConfigurationVersion(CONFIGURATION_CLOSURE_NO_PARENS);
  }

  @Test
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

    assertEquals(Boolean.TRUE, configuration.force().toBoolean());
    assertEquals(Boolean.FALSE, configuration.transitive().toBoolean());

    List<ExcludedDependencyModel> excludedDependencies = configuration.excludes();
    assertThat(excludedDependencies).hasSize(3);

    ExcludedDependencyModel first = excludedDependencies.get(0);
    assertNull(first.group().toString());
    assertEquals("cglib", first.module().toString());

    ExcludedDependencyModel second = excludedDependencies.get(1);
    assertEquals("org.jmock", second.group().toString());
    assertNull(second.module().toString());

    ExcludedDependencyModel third = excludedDependencies.get(2);
    assertEquals("org.unwanted", third.group().toString());
    assertEquals("iAmBuggy", third.module().toString());
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_parens() throws IOException {
    doTestSetVersionWithConfigurationClosure(CONFIGURATION_CLOSURE_PARENS);
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_noParens() throws IOException {
    doTestSetVersionWithConfigurationClosure(CONFIGURATION_CLOSURE_NO_PARENS);
  }

  @Test
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

    hibernate.version().setValue("3.0");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    hibernate = dependencies.get(0);

    expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.0");
    expected.assertMatches(hibernate);
    verifyDependencyConfiguration(hibernate.configuration());
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    assertEquals("com.android.support", first.group().toString());
    assertEquals("support-v4", first.module().toString());

    ExcludedDependencyModel second = excludedDependencies.get(1);
    assertEquals("com.android.support", second.group().toString());
    assertEquals("support-annotations", second.module().toString());

    ExcludedDependencyModel third = excludedDependencies.get(2);
    assertEquals("com.android.support", third.group().toString());
    assertEquals("recyclerview-v7", third.module().toString());

    ExcludedDependencyModel fourth = excludedDependencies.get(3);
    assertEquals("com.android.support", fourth.group().toString());
    assertEquals("design", fourth.module().toString());
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel appCompat = dependencies.get(0);
    appCompat.version().setValue("1.2.3");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testSetVersionOnDependencyWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel guice = dependencies.get(0);
    guice.version().setValue("1.2.3");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testReset() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel guice = dependencies.get(0);
    guice.version().setValue("1.2.3");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    assertEquals(expected.compactNotation(), appcompatDependencyModel.compactNotation());
    List<GradlePropertyModel> appcompatResolvedVariables = appcompatDependencyModel.completeModel().getDependencies();
    assertEquals(1, appcompatResolvedVariables.size());

    GradlePropertyModel appcompatVariable = appcompatResolvedVariables.get(0);
    verifyPropertyModel(appcompatVariable.resolve(), STRING_TYPE, "com.android.support:appcompat-v7:22.1.1", STRING, REGULAR, 0,
                        "appcompat", "ext.appcompat");

    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");
  }

  @Test
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
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");

    // Now test that resolved variables are not reported for group and name properties.
    GradlePropertyModel group = guavaDependencyModel.group();
    verifyPropertyModel(group, STRING_TYPE, "com.google.guava", STRING, DERIVED, 0, "group");

    GradlePropertyModel name = guavaDependencyModel.name();
    verifyPropertyModel(name, STRING_TYPE, "guava", STRING, DERIVED, 0, "name");

    // and thee guavaVersion variable is reported for version property.
    GradlePropertyModel version = guavaDependencyModel.version();
    verifyPropertyModel(version, STRING_TYPE, "18.0", STRING, DERIVED, 1, "version");
    assertThat(version.getRawValue(STRING_TYPE)).isEqualTo("$guavaVersion");
  }

  @Test
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
    assertEquals(expected.compactNotation(), appcompatDependencyModel.compactNotation());
    List<GradlePropertyModel> appcompatResolvedVariables = appcompatDependencyModel.completeModel().getDependencies();
    assertEquals(1, appcompatResolvedVariables.size());

    GradlePropertyModel appcompatVariable = appcompatResolvedVariables.get(0);
    verifyPropertyModel(appcompatVariable, STRING_TYPE, "com.android.support:appcompat-v7:22.1.1", STRING, REGULAR, 0, "appcompat",
                        "ext.appcompat");


    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");
  }

  @Test
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
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");

    // Now test that resolved variables are not reported for group and name properties.
    GradlePropertyModel group = guavaDependencyModel.group();
    verifyPropertyModel(group, STRING_TYPE, "com.google.guava", STRING, DERIVED, 0, "group");

    GradlePropertyModel name = guavaDependencyModel.name();
    verifyPropertyModel(name, STRING_TYPE, "guava", STRING, DERIVED, 0, "name");

    // and thee guavaVersion variable is reported for version property.
    GradlePropertyModel version = guavaDependencyModel.version();
    verifyPropertyModel(version, STRING_TYPE, "18.0", STRING, DERIVED, 1, "version");
    assertThat(version.getRawValue(STRING_TYPE)).isEqualTo("$guavaVersion");

    guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");
  }

  @Test
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

  @Test
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

  @Test
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
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies().artifacts();

    assertThat(dependencies).hasSize(3);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
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

  @Test
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

  @Test
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

    ArtifactDependencyModel artifactModel = dependencies.get(0);
    assertMissingProperty(artifactModel.group());
    assertMissingProperty(artifactModel.version());
    assertMissingProperty(artifactModel.classifier());
    assertMissingProperty(artifactModel.extension());

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0:class@aar");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.setClassifier("class");
    expected.setExtension("aar");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testDeleteGroupAndVersion() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0',  'com.google.guava:guava:+'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.assertMatches(artifacts.get(0));
    expected = new ExpectedArtifactDependency(TEST_COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(artifacts.get(1));

    // Remove version from the first artifact and group from the second. Even though these now become invalid dependencies we should still
    // allow them from the model.
    ArtifactDependencyModel first = artifacts.get(0);
    first.version().delete();
    ArtifactDependencyModel second = artifacts.get(1);
    second.group().delete();

    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();

    first = artifacts.get(0);
    assertThat(first.completeModel().toString()).isEqualTo("org.gradle.test.classifiers:service");
    second = artifacts.get(1);
    assertThat(second.completeModel().toString()).isEqualTo("guava:+");
  }

  @Test
  public void testDeleteNameAndRenameUnsupported() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.assertMatches(artifacts.get(0));

    ArtifactDependencyModel first = artifacts.get(0);
    try {
      first.name().delete();
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      first.name().rename("Hello");
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    assertFalse(buildModel.isModified());
  }

  @Test
  public void testDeleteInMethodCallWithProperties() throws IOException {
    String text = "dependencies {\n" +
                  "  compile('com.google.code.guice:guice:1.0', 'com.google.guava:guava:18.0')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    // Attempt to delete the first one.
    ArtifactDependencyModel first = artifacts.get(0);
    first.completeModel().delete();

    applyChangesAndReparse(buildModel);

    buildModel = getGradleBuildModel();
    dependencies = buildModel.dependencies();

    artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(artifacts.get(0));
  }

  @Test
  public void testMissingPropertiesCompact() throws IOException {
    String text = "dependencies {\n" +
                  "    compile 'com.android.support:appcompat-v7:22.1.1'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    assertMissingProperty(artifact.extension());
    assertMissingProperty(artifact.classifier());
    verifyPropertyModel(artifact.name(), STRING_TYPE, "appcompat-v7", STRING, DERIVED, 0, "name");
    verifyPropertyModel(artifact.group(), STRING_TYPE, "com.android.support", STRING, DERIVED, 0, "group");
    verifyPropertyModel(artifact.version(), STRING_TYPE, "22.1.1", STRING, DERIVED, 0, "version");

    verifyPropertyModel(artifact.completeModel(), STRING_TYPE, "com.android.support:appcompat-v7:22.1.1", STRING, REGULAR, 0, "compile");
  }

  @Test
  public void testMissingPropertiesMap() throws IOException {
    String text = "dependencies {\n" +
                  "    compile name: 'name'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    assertMissingProperty(artifact.extension());
    assertMissingProperty(artifact.classifier());
    assertMissingProperty(artifact.version());
    assertMissingProperty(artifact.extension());
    verifyPropertyModel(artifact.name(), STRING_TYPE, "name", STRING, DERIVED, 0, "name");

    verifyMapProperty(artifact.completeModel(), ImmutableMap.of("name", "name"), "compile", "dependencies.compile");
  }

  @Test
  public void testCompactNotationPsiElement() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'org.gradle.test.classifiers:service:1.0'");
  }

  @Test
  public void testMultipleCompactNotationPsiElements() throws IOException {
    String text = "dependencies {\n" +
                  "  compile('com.google.code.guice:guice:1.0', 'com.google.guava:guava:18.0')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.code.guice:guice:1.0'");

    artifact = artifacts.get(1);
    psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.guava:guava:18.0'");
  }

  @Test
  public void testMethodCallCompactPsiElement() throws IOException {
    String text = "dependencies {\n" +
                  "  testCompile('org.hibernate:hibernate:3.1') { \n" +
                  "    force = true\n" + // Note: We currently preserve the whole closure.
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'org.hibernate:hibernate:3.1'");
  }

  @Test
  public void testMethodCallMultipleCompactPsiElement() throws IOException {
    String text = "dependencies {\n" +
                  "  compile('com.google.code.guice:guice:1.0', 'com.google.guava:guava:18.0')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.code.guice:guice:1.0'");

    artifact = artifacts.get(1);
    psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.guava:guava:18.0'");
  }

  @Test
  public void testMapNotationPsiElement() throws IOException {
    String text = "dependencies {\n" +
                  "    compile group: 'com.google.code.guice', name: 'guice', version: '1.0', classifier: 'high', ext: 'bleh'\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrArgumentList.class);
    }
    assertThat(psiElement.getText())
      .isEqualTo("group: 'com.google.code.guice', name: 'guice', version: '1.0', classifier: 'high', ext: 'bleh'");
  }

  @Test
  public void testCompactNotationSetToReference() throws IOException {
    String text = "ext {\n" +
                  "  version = '3.6'\n" +
                  "  name = \"guava\"\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0',  'com.google.guava:guava:+'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    // Get the version variable model
    ExtModel extModel = buildModel.ext();
    GradlePropertyModel name = extModel.findProperty("name");

    ArtifactDependencyModel firstModel = artifacts.get(0);
    firstModel.version().setValue(new ReferenceTo("version"));
    ArtifactDependencyModel secondModel = artifacts.get(1);
    secondModel.name().setValue(new ReferenceTo(name));

    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    firstModel = artifacts.get(0);
    verifyPropertyModel(firstModel.version(), STRING_TYPE, "3.6", STRING, DERIVED, 1, "version");
    assertThat(firstModel.completeModel().getRawValue(STRING_TYPE)).isEqualTo("org.gradle.test.classifiers:service:${version}");

    secondModel = artifacts.get(1);
    verifyPropertyModel(secondModel.name(), STRING_TYPE, "guava", STRING, DERIVED, 1, "name");
    assertThat(secondModel.completeModel().getRawValue(STRING_TYPE)).isEqualTo("com.google.guava:${ext.name}:+");
  }

  @Test
  public void testParseFullReferencesCompactApplication() throws IOException {
    String text = "ext {\n" +
                  "  service = 'org.gradle.test.classifiers:service:1.0'\n" +
                  "  guavaPart = 'google.guava:guava:+'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile service,  \"com.${guavaPart}\"\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    verifyPropertyModel(model, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, DERIVED, 1, "testCompile");
    model = artifacts.get(1).completeModel();
    verifyPropertyModel(model, STRING_TYPE, "com.google.guava:guava:+", STRING, DERIVED, 1, "testCompile");
  }

  private void runSetFullReferencesTest(@NotNull String text) throws IOException {
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    model.setValue(new ReferenceTo("service"));
    model = artifacts.get(1).completeModel();
    model.setValue(iStr("com.${guavaPart}"));

    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    System.out.println(FileUtils.readFileToString(myBuildFile));

    model = artifacts.get(0).completeModel();
    verifyPropertyModel(model, STRING_TYPE, "com.google.guava:guava:+", STRING, REGULAR, 1, "testCompile");
    model = artifacts.get(1).completeModel();
    verifyPropertyModel(model, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, REGULAR, 1, "testCompile");
  }

  private void runSetFullSingleReferenceTest(@NotNull String text) throws IOException {
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    model.setValue(new ReferenceTo("service"));

    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    model = artifacts.get(0).completeModel();
    verifyPropertyModel(model, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, REGULAR, 1, "testCompile");
  }

  @Test
  public void testSetSingleReferenceCompactApplication() throws IOException {
    String text = "ext {\n" +
                  "  service = 'org.gradle.test.classifiers:service:1.0'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile 'some:gradle:thing'\n" +
                  "}";
    runSetFullSingleReferenceTest(text);
  }

  @Test
  @Ignore("Setting methods currently fails")
  public void testSetSingleReferenceCompactMethod() throws IOException {
    String text = "ext {\n" +
                  "  service = 'org.gradle.test.classifiers:service:1.0'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile('some:gradle:thing')\n" +
                  "}";
    runSetFullSingleReferenceTest(text);
  }

  @Test
  public void testSetFullReferencesCompactApplication() throws IOException {
    String text = "ext {\n" +
                  "  service = 'org.gradle.test.classifiers:service:1.0'\n" +
                  "  guavaPart = 'google.guava:guava:+'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile 'some:gradle:thing',  'some:other:gradle:thing'\n" +
                  "}";
    runSetFullReferencesTest(text);
  }

  @Test
  @Ignore("Setting methods currently fails")
  public void testSetFullReferenceCompactMethod() throws IOException {
    String text = "ext {\n" +
                  "  service = 'org.gradle.test.classifiers:service:1.0'\n" +
                  "  guavaPart = 'google.guava:guava:+'\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile('some:gradle:thing',  'some:other:gradle:thing')\n" +
                  "}";
    runSetFullReferencesTest(text);
  }

  @Test
  public void testParseFullReferenceMap() throws IOException {
    String text = "ext {\n" +
                  "  dependency = [group: 'group', name: 'name', version: '1.0']\n" +
                  "  guavaGroup = 'com.google.guava'\n" +
                  "  guavaName = 'guava'\n" +
                  "  otherDependency = [group: 'g', name: 'n', version: '2.0']\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile dependency\n" +
                  "  compile group: guavaGroup, name: guavaName, version: '4.0', { }\n" +
                  "  testCompile(otherDependency) { }\n" +
                  "  compile(group: guavaName, name: guavaGroup, version: '3.0')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(4, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "group", "name", "name", "version", "1.0"));
    model = artifacts.get(1).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "g", "name", "n", "version", "2.0"));
    model = artifacts.get(2).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "com.google.guava", "name", "guava", "version", "4.0"));
    model = artifacts.get(3).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "guava", "name", "com.google.guava", "version", "3.0"));
  }

  @Test
  public void testSetFullReferenceMap() throws IOException {
    String text = "ext {\n" +
                  "  dependency = [group: 'group', name: 'name', version: '1.0']\n" +
                  "  guavaGroup = 'com.google.guava'\n" +
                  "  guavaName = 'guava'\n" +
                  "  otherDependency = [group: 'g', name: 'n', version: '2.0']\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "  testCompile 'org.gradle.test.classifiers:service:1.0'\n" +
                  "  compile group: 'boo', name: 'boo', version: '4.0', { }\n" +
                  "  testCompile([group: 'up', name: 'down', version: '1.0']) { }\n" +
                  "  compile(group: 'bar', name: 'bar', version: '3.0')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(4, artifacts);

    ArtifactDependencyModel model = artifacts.get(0);
    model.completeModel().setValue(new ReferenceTo("dependency"));
    //model = artifacts.get(1);
    //model.completeModel().setValue(new ReferenceTo("otherDependency"));
    model = artifacts.get(2);
    model.group().setValue(new ReferenceTo("guavaGroup"));
    model.name().setValue(new ReferenceTo("guavaName"));
    //model = artifacts.get(3);
    //model.group().setValue(new ReferenceTo("guavaName"));
    //model.name().setValue(new ReferenceTo("guavaGroup"));

    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(4, artifacts);

    System.out.println(FileUtils.readFileToString(myBuildFile));

    model = artifacts.get(0);
    verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "com.google.guava", "name", "guava", "version", "4.0"));
    //model = artifacts.get(1);
    //verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "g", "name", "n", "version", "2.0"));
    //model = artifacts.get(2);
    //verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "guava", "name", "com.google.guava", "version", "3.0"));g
    model = artifacts.get(3);
    verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "group", "name", "name", "version", "1.0"));
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
      assertEquals("group", getGroup(), actual.group().toString());
      assertEquals("name", getName(), actual.name().forceString());
      assertEquals("version", getVersion(), actual.version().toString());
      assertEquals("classifier", getClassifier(), actual.classifier().toString());
      assertEquals("extension", getExtension(), actual.extension().toString());
    }

    public boolean matches(@NotNull ArtifactDependencyModel dependency) {
      return configurationName.equals(dependency.configurationName()) &&
             getName().equals(dependency.name().forceString()) &&
             Objects.equal(getGroup(), dependency.group().toString()) &&
             Objects.equal(getVersion(), dependency.version().toString()) &&
             Objects.equal(getClassifier(), dependency.classifier().toString()) &&
             Objects.equal(getExtension(), dependency.extension().toString());
    }
  }
}
