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
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.google.common.base.Objects;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.RUNTIME;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link DependenciesModel} and {@link ArtifactDependencyModel}.
 */
public class ArtifactDependencyTest extends GradleFileModelTestCase {
  public void testParsingWithCompactNotationAndConfigurationClosure() throws IOException {
    String text = "dependencies {\n" +
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
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(0));
  }

  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure() throws IOException {
    String text = "dependencies {\n" +
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
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ArtifactDependencyModel hibernate = dependencies.get(0);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(hibernate);

    hibernate.setVersion("3.0");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    hibernate = dependencies.get(0);

    expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.0");
    expected.assertMatches(hibernate);
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
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.classifier = "jdk14";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(0));
  }

  public void testAddDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpec newDependency = new ArtifactDependencySpec("appcompat-v7", "com.android.support", "22.1.1");
    dependenciesModel.addArtifact(COMPILE, newDependency);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.classifier = "jdk14";
    expected.extension = "jar";
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
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

    ArtifactDependencySpec guavaSpec = new ArtifactDependencySpec("guava", "com.google.guava", "18.0");
    ArtifactDependencySpec guiceSpec = new ArtifactDependencySpec("guice", "com.google.code.guice", "2.0");

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
    assertEquals(expected.compactNotation(), appcompatDependency.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(appcompatDependency.getFile().getPath()));
    assertEquals("dependencies.compile", appcompatDependency.getPropertyName());
    assertEquals("appcompat", appcompatDependency.getDslText());
    Map<String, GradleNotNullValue<Object>> appcompatResolvedVariables = appcompatDependency.getResolvedVariables();
    assertEquals(1, appcompatResolvedVariables.size());

    GradleNotNullValue<Object> appcompatVariable = appcompatResolvedVariables.get("appcompat");
    assertNotNull(appcompatVariable);
    assertEquals("com.android.support:appcompat-v7:22.1.1", appcompatVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(appcompatVariable.getFile().getPath()));
    assertEquals("ext.appcompat", appcompatVariable.getPropertyName());
    assertEquals("appcompat = 'com.android.support:appcompat-v7:22.1.1'", appcompatVariable.getDslText());
    assertEquals(0, appcompatVariable.getResolvedVariables().size());


    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    GradleNotNullValue<String> guavaDependency = guavaDependencyModel.compactNotation();
    assertEquals(expected.compactNotation(), guavaDependency.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaDependency.getFile().getPath()));
    assertEquals("dependencies.runtime", guavaDependency.getPropertyName());
    assertEquals("\"com.google.guava:guava:$guavaVersion\"", guavaDependency.getDslText());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaVersionVariable.getFile().getPath()));
    assertEquals("ext.guavaVersion", guavaVersionVariable.getPropertyName());
    assertEquals("guavaVersion = '18.0'", guavaVersionVariable.getDslText());
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
    assertEquals(expected.compactNotation(), guavaDependency.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaDependency.getFile().getPath()));
    assertEquals("dependencies.compile", guavaDependency.getPropertyName());
    assertEquals("group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\"", guavaDependency.getDslText());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaVersionVariable.getFile().getPath()));
    assertEquals("ext.guavaVersion", guavaVersionVariable.getPropertyName());
    assertEquals("guavaVersion = '18.0'", guavaVersionVariable.getDslText());
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
    assertEquals("18.0", version.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(version.getFile().getPath()));
    assertEquals("dependencies.compile.version", version.getPropertyName());
    assertEquals("group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\"", version.getDslText());
    guavaResolvedVariables = version.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaVersionVariable.getFile().getPath()));
    assertEquals("ext.guavaVersion", guavaVersionVariable.getPropertyName());
    assertEquals("guavaVersion = '18.0'", guavaVersionVariable.getDslText());
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
    assertEquals(expected.compactNotation(), appcompatDependency.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(appcompatDependency.getFile().getPath()));
    assertEquals("dependencies.compile.compile", appcompatDependency.getPropertyName());
    assertEquals("appcompat", appcompatDependency.getDslText());
    Map<String, GradleNotNullValue<Object>> appcompatResolvedVariables = appcompatDependency.getResolvedVariables();
    assertEquals(1, appcompatResolvedVariables.size());

    GradleNotNullValue<Object> appcompatVariable = appcompatResolvedVariables.get("appcompat");
    assertNotNull(appcompatVariable);
    assertEquals("com.android.support:appcompat-v7:22.1.1", appcompatVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(appcompatVariable.getFile().getPath()));
    assertEquals("ext.appcompat", appcompatVariable.getPropertyName());
    assertEquals("appcompat = 'com.android.support:appcompat-v7:22.1.1'", appcompatVariable.getDslText());
    assertEquals(0, appcompatVariable.getResolvedVariables().size());


    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    GradleNotNullValue<String> guavaDependency = guavaDependencyModel.compactNotation();
    assertEquals(expected.compactNotation(), guavaDependency.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaDependency.getFile().getPath()));
    assertEquals("dependencies.compile.compile", guavaDependency.getPropertyName());
    assertEquals("\"com.google.guava:guava:$guavaVersion\"", guavaDependency.getDslText());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaVersionVariable.getFile().getPath()));
    assertEquals("ext.guavaVersion", guavaVersionVariable.getPropertyName());
    assertEquals("guavaVersion = '18.0'", guavaVersionVariable.getDslText());
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
    assertEquals(expected.compactNotation(), guavaDependency.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaDependency.getFile().getPath()));
    assertEquals("dependencies.compile.compile", guavaDependency.getPropertyName());
    assertEquals("(group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\")", guavaDependency.getDslText());
    Map<String, GradleNotNullValue<Object>> guavaResolvedVariables = guavaDependency.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    GradleNotNullValue<Object> guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaVersionVariable.getFile().getPath()));
    assertEquals("ext.guavaVersion", guavaVersionVariable.getPropertyName());
    assertEquals("guavaVersion = '18.0'", guavaVersionVariable.getDslText());
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
    assertEquals("18.0", version.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(version.getFile().getPath()));
    assertEquals("dependencies.compile.compile.version", version.getPropertyName());
    assertEquals("(group: 'com.google.guava', name: 'guava', version: \"$guavaVersion\")", version.getDslText());
    guavaResolvedVariables = version.getResolvedVariables();
    assertEquals(1, guavaResolvedVariables.size());

    guavaVersionVariable = guavaResolvedVariables.get("guavaVersion");
    assertNotNull(guavaVersionVariable);
    assertEquals("18.0", guavaVersionVariable.value());
    assertEquals(FileUtil.toSystemIndependentName(myBuildFile.getPath()), FileUtil.toSystemIndependentName(guavaVersionVariable.getFile().getPath()));
    assertEquals("ext.guavaVersion", guavaVersionVariable.getPropertyName());
    assertEquals("guavaVersion = '18.0'", guavaVersionVariable.getDslText());
    assertEquals(0, guavaVersionVariable.getResolvedVariables().size());
  }

  public static class ExpectedArtifactDependency extends ArtifactDependencySpec {
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
      assertEquals("group", group, actual.group().value());
      assertEquals("name", name, actual.name().value());
      assertEquals("version", version, actual.version().value());
      assertEquals("classifier", classifier, actual.classifier().value());
      assertEquals("extension", extension, actual.extension().value());
    }

    public boolean matches(@NotNull ArtifactDependencyModel dependency) {
      return configurationName.equals(dependency.configurationName()) &&
             name.equals(dependency.name().value()) &&
             Objects.equal(group, dependency.group().value()) &&
             Objects.equal(version, dependency.version().value()) &&
             Objects.equal(classifier, dependency.classifier().value()) &&
             Objects.equal(extension, dependency.extension().value());
    }
  }
}
