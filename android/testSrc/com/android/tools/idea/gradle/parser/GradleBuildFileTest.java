/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.parser.GradleBuildFile.UNRECOGNIZED_VALUE;

public class GradleBuildFileTest extends IdeaTestCase {
  private Document myDocument;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = null;
  }

  public void testGetTopLevelValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    assertEquals("17.0.0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testNestedValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertEquals("1", file.getValue(closure, BuildFileKey.TARGET_SDK_VERSION));
  }

  public void testSetTopLevelValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TOOLS_VERSION, "18.0.0");
      }
    });
    String expected = getSimpleTestFile().replaceAll("17\\.0\\.0", "18.0.0");
    assertContents(expected);
  }

  public void testSetNestedValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        GrStatementOwner closure = file.getClosure("android/defaultConfig");
        file.setValue(closure, BuildFileKey.TARGET_SDK_VERSION, 2);
      }
    });
    String expected = getSimpleTestFile().replaceAll("targetSdkVersion 1", "targetSdkVersion 2");
    assertContents(expected);
  }

  public void testSetStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TOOLS_VERSION, "99.0.0");
      }
    });
    String expected = getSimpleTestFile().replaceAll("buildToolsVersion '17\\.0\\.0'", "buildToolsVersion '99.0.0'");
    assertContents(expected);
    assertEquals("99.0.0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testExistingStringValueWithQuote() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TOOLS_VERSION, "17'0'0");
      }
    });
    String expected = getSimpleTestFile().replace("buildToolsVersion '17.0.0'", "buildToolsVersion '17\\'0\\'0'");
    assertContents(expected);
    assertEquals("17'0'0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testNewStringValueWithQuote() throws Exception {
    final GradleBuildFile file = getTestFile("");
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TOOLS_VERSION, "17'0'0");
      }
    });
    String expected =
      "android {\n" +
      "    buildToolsVersion '17\\'0\\'0'\n" +
      "}";
    assertContents(expected);
    assertEquals("17'0'0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testNewDependencyWithQuote() throws Exception {
    final GradleBuildFile file = getTestFile("");
    Dependency dep = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, "abc'def");
    final List<Dependency> dependencyList = ImmutableList.of(dep);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, dependencyList);
      }
    });
    String expected =
      "dependencies {\n" +
      "    compile files('abc\\'def')\n" +
      "}";
    assertContents(expected);
    assertEquals(dependencyList, file.getValue(BuildFileKey.DEPENDENCIES));
  }

  public void testNewRepositoryWithQuote() throws Exception {
    final GradleBuildFile file = getTestFile("");
    Repository rep = new Repository(Repository.Type.URL, "http://www.foo.com?q=abc'def");
    final List<Repository> repositoryList = ImmutableList.of(rep);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.LIBRARY_REPOSITORY, repositoryList);
      }
    });
    String expected =
      "repositories {\n" +
      "    maven { url 'http://www.foo.com?q=abc\\'def' }\n" +
      "}";
    assertContents(expected);
    assertEquals(repositoryList, file.getValue(BuildFileKey.LIBRARY_REPOSITORY));
  }

  public void testSetIntegerValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.VERSION_CODE, 99);
      }
    });
    String expected = getSimpleTestFile().replaceAll("versionCode 1337", "versionCode 99");
    assertContents(expected);
    assertEquals(99, file.getValue(closure, BuildFileKey.VERSION_CODE));
  }

  public void testSetBooleanValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/buildTypes/debug");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.DEBUGGABLE, false);
      }
    });
    String expected = getSimpleTestFile().replaceAll("debuggable true", "debuggable false");
    assertContents(expected);
    assertEquals(false, file.getValue(closure, BuildFileKey.DEBUGGABLE));
  }

  public void testSetFileValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/signingConfigs/debug");
    final File replacementFile = new File("abc/def/foo.keystore");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.STORE_FILE, replacementFile);
      }
    });
    // We always expect system independent paths in build.gradle files.
    String expected = getSimpleTestFile().replaceAll("debug.keystore", "abc/def/foo.keystore");
    assertContents(expected);
    assertEquals(replacementFile, file.getValue(closure, BuildFileKey.STORE_FILE));
  }

  public void testSetFileStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/productFlavors/flavor1");
    final File replacementFile = new File("abc/def/foo.txt");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.PROGUARD_FILE, replacementFile);
      }
    });
    // We always expect system independent paths in build.gradle files.
    String expected = getSimpleTestFile().replaceAll("proguard-flavor1\\.txt", "abc/def/foo.txt");
    assertContents(expected);
    assertEquals(replacementFile, file.getValue(closure, BuildFileKey.PROGUARD_FILE));
  }

  @SuppressWarnings("unchecked")
  public void testSetNamedObjectValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    Object value = file.getValue(BuildFileKey.FLAVORS);
    assert value != null;
    assert value instanceof List;
    final List<NamedObject> flavors = (List<NamedObject>)value;
    assertEquals(2, flavors.size());
    NamedObject flavor3 = new NamedObject("flavor3");
    flavor3.setValue(BuildFileKey.APPLICATION_ID, "flavor3.packagename");
    flavors.add(flavor3);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.FLAVORS, flavors);
      }
    });
    Object newValue = file.getValue(BuildFileKey.FLAVORS);
    assert newValue != null;
    assert newValue instanceof List;
    final List<NamedObject> newFlavors = (List<NamedObject>)newValue;

    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("}\n", expected.indexOf("flavor2 {\n")) + 2;
    expected.insert(position,
        "        flavor3 {\n" +
        "            applicationId 'flavor3.packagename'\n" +
        "        }\n"
                               );
    assertContents(expected.toString());
    assertEquals(flavors, newFlavors);
  }

  @SuppressWarnings("unchecked")
  public void testRenameNamedObjectWithMultipleSubvalues() throws Exception {
    String contents = getSimpleTestFile();
    final GradleBuildFile file = getTestFile(contents);
    Object value = file.getValue(BuildFileKey.BUILD_TYPES);
    assert value != null;
    assert value instanceof List;
    final List<NamedObject> buildTypes = (List<NamedObject>)value;
    assertEquals(2, buildTypes.size());
    NamedObject buildType = buildTypes.get(1);
    assertEquals("release", buildType.getName());
    buildTypes.remove(1);
    NamedObject newBuildType = new NamedObject("release1a");
    newBuildType.setValue(BuildFileKey.DEBUGGABLE, false);
    newBuildType.setValue(BuildFileKey.ZIP_ALIGN, true);
    buildTypes.add(newBuildType);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, buildTypes);
      }
    });
    assertContents(contents.replaceAll("release", "release1a")
                           .replaceAll("debuggable false\n", "debuggable false\n            zipAlignEnabled true\n"));
  }

  @SuppressWarnings("unchecked")
  public void testRemovePropertyFromNamedValue() throws Exception {
    String contents =
      "android {\n" +
      "    buildTypes {\n" +
      "        release {\n" +
      "            debuggable false\n" +
      "            zipAlignEnabled true\n" +
      "        }\n" +
      "    }\n" +
      "}";
    final GradleBuildFile file = getTestFile(contents);
    Object value = file.getValue(BuildFileKey.BUILD_TYPES);
    assert value != null;
    assert value instanceof List;
    final List<NamedObject> buildTypes = (List<NamedObject>)value;
    assertEquals(1, buildTypes.size());
    NamedObject buildType = buildTypes.get(0);
    assertEquals("release", buildType.getName());
    buildType.setValue(BuildFileKey.DEBUGGABLE, null);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, buildTypes);
      }
    });
    assertContents(contents.replaceAll(" *debuggable false\n", ""));
  }

  public void testCreateStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.IGNORE_ASSETS_PATTERN, "foo");
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.length() - 1;
    expected.insert(position,
                    "    aaptOptions {\n" +
                    "        ignoreAssetsPattern 'foo'\n" +
                    "    }\n");
    assertContents(expected.toString());
    assertEquals("foo", file.getValue(BuildFileKey.IGNORE_ASSETS_PATTERN));
  }

  @SuppressWarnings("unchecked")
  public void testCreateIntegerValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/productFlavors/flavor1");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.VERSION_CODE, 199);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("\n", expected.indexOf("proguard-flavor1.txt")) + 1;
    expected.insert(position, "            versionCode 199\n");
    assertContents(expected.toString());
    Object value = file.getValue(BuildFileKey.FLAVORS);
    assertNotNull("flavors should be parsed", value);
    final List<NamedObject> flavors = (List<NamedObject>)value;
    assertEquals(2, flavors.size());
    assertEquals(199, flavors.get(0).getValue(BuildFileKey.VERSION_CODE));
  }

  public void testCreateBooleanValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.INCREMENTAL_DEX, true);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.length() - 1;
    expected.insert(position,
                    "    dexOptions {\n" +
                    "        incremental true\n" +
                    "    }\n");
    assertContents(expected.toString());
    assertEquals(true, file.getValue(BuildFileKey.INCREMENTAL_DEX));
  }

  @SuppressWarnings("unchecked")
  public void testCreateFileValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/signingConfigs/config2");
    final File newFile = new File("foo.keystore");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.STORE_FILE, newFile);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("\n", expected.indexOf("config2 {")) + 1;
    expected.insert(position, "            storeFile file('foo.keystore')\n");
    assertContents(expected.toString());
    Object value = file.getValue(BuildFileKey.SIGNING_CONFIGS);
    final List<NamedObject> configs = (List<NamedObject>)value;
    assertNotNull("signing configs should be parsed", configs);
    assertEquals(2, configs.size());
    assertEquals(newFile, configs.get(1).getValue(BuildFileKey.STORE_FILE));
  }

  @SuppressWarnings("unchecked")
  public void testCreateFileStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/productFlavors/flavor2");
    final File newFile = new File("foo.txt");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.PROGUARD_FILE, newFile);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("\n", expected.indexOf("flavor2 {")) + 1;
    expected.insert(position, "            proguardFile 'foo.txt'\n");
    assertContents(expected.toString());
    Object value = file.getValue(BuildFileKey.FLAVORS);
    List<NamedObject> configs = (List<NamedObject>)value;
    assertNotNull("flavors should be parsed", configs);
    assertEquals(2, configs.size());
    assertEquals(newFile, configs.get(1).getValue(BuildFileKey.PROGUARD_FILE));
  }

  public void testRemoveValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.removeValue(null, BuildFileKey.COMPILE_SDK_VERSION);
      }
    });

    String expected = getSimpleTestFile().replace("    compileSdkVersion 17\n", "");
    assertContents(expected);
    assertNull(file.getValue(BuildFileKey.COMPILE_SDK_VERSION));
  }

  public void testGetClosureChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getClosure("/");
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testGetValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getValue(BuildFileKey.TARGET_SDK_VERSION);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testGetNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getValue(getDummyClosure(), BuildFileKey.TARGET_SDK_VERSION);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testSetValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.setValue(BuildFileKey.TARGET_SDK_VERSION, 2);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testSetNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.setValue(getDummyClosure(), BuildFileKey.TARGET_SDK_VERSION, 2);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testGetsPropertyFromRedundantBlock() throws Exception {
    GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildToolsVersion '17.0.0'\n" +
      "}\n" +
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "}\n"
    );
    assertEquals("17", file.getValue(BuildFileKey.COMPILE_SDK_VERSION));
    assertEquals("17.0.0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testGetDependencyInAlternativeFormat() throws IOException {
    GradleBuildFile file = getTestFile(
      "    dependencies {\n" +
      "        compile group: 'com.google.guava', name: 'guava', version: '12.0'\n" +
      "    }\n"
    );
    List<BuildFileStatement> dependencies = file.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency dependency = (Dependency)dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("com.google.guava:guava:12.0", dependency.getValueAsString());
  }

  public void testGetNewDependencyInAlternativeFormat() throws IOException {
    GradleBuildFile file = getTestFile(
      "    dependencies {\n" +
      "        implementation group: 'com.google.guava', name: 'guava', version: '12.0'\n" +
      "    }\n"
    );
    List<BuildFileStatement> dependencies = file.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency dependency = (Dependency)dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("com.google.guava:guava:12.0", dependency.getValueAsString());
  }

  public void testGetDependencyInAlternativeFormatWithArtifactType() throws IOException {
    GradleBuildFile file = getTestFile(
      "    dependencies {\n" +
      "        compile group: 'com.google.guava', name: 'guava', version: '12.0', ext: 'jar'\n" +
      "    }\n"
    );
    List<BuildFileStatement> dependencies = file.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency dependency = (Dependency)dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("com.google.guava:guava:12.0@jar", dependency.getValueAsString());
  }

  @SuppressWarnings("unchecked")
  public void testGetDependencyInAnotherAlternateFormat() throws IOException {
    GradleBuildFile file = getTestFile(
      "    dependencies {\n" +
      "        compile project(path: ':foo', configuration: 'bar')\n" +
      "    }\n"
    );
    List<BuildFileStatement> dependencies = file.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency dependency = (Dependency)dependencies.get(0);
    assertNotNull(dependency);
    Map<String, Object> expected = ImmutableMap.of("path", ":foo", "configuration", (Object)"bar");
    assert(Maps.difference(expected, (Map<? extends String, ?>)dependency.data).areEqual());
  }

  public void testGetsMavenRepositories() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    List<Repository> expectedRepositories = Lists.newArrayList(
        new Repository(Repository.Type.MAVEN_CENTRAL, null),
        new Repository(Repository.Type.URL, "www.foo1.com"),
        new Repository(Repository.Type.URL, "www.foo2.com"),
        new Repository(Repository.Type.URL, "www.foo3.com"),
        new Repository(Repository.Type.URL, "www.foo4.com"));
    assertEquals(expectedRepositories, file.getValue(BuildFileKey.LIBRARY_REPOSITORY));
  }

  public void testSetsMavenRepositories() throws Exception {
    final GradleBuildFile file = getTestFile("");
    final List<Repository> newRepositories = Lists.newArrayList(
      new Repository(Repository.Type.MAVEN_CENTRAL, null),
      new Repository(Repository.Type.MAVEN_LOCAL, null),
      new Repository(Repository.Type.URL, "www.foo.com"));
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.LIBRARY_REPOSITORY, newRepositories);
      }
    });
    String expected =
      "repositories {\n" +
      "    mavenCentral()\n" +
      "    mavenLocal()\n" +
      "    maven { url 'www.foo.com' }\n" +
      "}";
    assertContents(expected);
  }

  public void testGetsFiletreeDependencies() throws Exception {
    GradleBuildFile file = getTestFile(
      "dependencies {\n" +
      "    compile fileTree(dir: 'libs', includes: ['*.jar', '*.aar'])\n" +
      "}"
    );
    ImmutableList<String> fileList = ImmutableList.of("*.jar", "*.aar");
    Map<String, Object> nvMap = ImmutableMap.of(
      "dir", "libs",
      "includes", (Object)fileList
    );
    Dependency dep = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILETREE, nvMap);
    List<Dependency> expected = ImmutableList.of(dep);
    assertEquals(expected, file.getValue(BuildFileKey.DEPENDENCIES));
  }

  public void testNewGetsFiletreeDependencies() throws Exception {
    GradleBuildFile file = getTestFile(
      "dependencies {\n" +
      "    api fileTree(dir: 'libs', includes: ['*.jar', '*.aar'])\n" +
      "}"
    );
    ImmutableList<String> fileList = ImmutableList.of("*.jar", "*.aar");
    Map<String, Object> nvMap = ImmutableMap.of(
      "dir", "libs",
      "includes", (Object)fileList
    );
    Dependency dep = new Dependency(Dependency.Scope.API, Dependency.Type.FILETREE, nvMap);
    List<Dependency> expected = ImmutableList.of(dep);
    assertEquals(expected, file.getValue(BuildFileKey.DEPENDENCIES));
  }

  public void testSetsFiletreeDependencies() throws Exception {
    final GradleBuildFile file = getTestFile("");
    ImmutableList<String> fileList = ImmutableList.of("*.jar", "*.aar");
    Map<String, Object> nvMap = ImmutableMap.of(
      "dir", "libs",
      "includes", (Object)fileList
    );
    final Dependency dep = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILETREE, nvMap);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, ImmutableList.of(dep));
      }
    });
    String expected =
      "dependencies {\n" +
      "    compile fileTree(dir: 'libs', includes: ['*.jar', '*.aar'])\n" +
      "}";
    assertContents(expected);
  }

  public void testReadsUnparseableDependencies() throws Exception {
    GradleBuildFile file = getTestFile(
      "dependencies {\n" +
      "    // Comment 1\n" +
      "    compile 'foo.com:1.0.0'\n" +
      "    androidTestImplementation 'foo.com:1.0.0'\n" +
      "    api 'foo.com:1.0.0'\n" +
      "    implementation 'foo.com:1.0.0'\n" +
      "    compile random.expression\n" +
      "    functionCall()\n" +
      "    random.expression\n" +
      "}"
    );
    List<BuildFileStatement> expected = ImmutableList.of(
      new UnparseableStatement("// Comment 1", getProject()),
      new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "foo.com:1.0.0"),
      new Dependency(Dependency.Scope.ANDROID_TEST_IMPLEMENTATION, Dependency.Type.EXTERNAL, "foo.com:1.0.0"),
      new Dependency(Dependency.Scope.API, Dependency.Type.EXTERNAL, "foo.com:1.0.0"),
      new Dependency(Dependency.Scope.IMPLEMENTATION, Dependency.Type.EXTERNAL, "foo.com:1.0.0"),
      new UnparseableStatement("compile random.expression", getProject()),
      new UnparseableStatement("functionCall()", getProject()), new UnparseableStatement("random.expression", getProject())
    );
    assertEquals(expected, file.getValue(BuildFileKey.DEPENDENCIES));
  }

  public void testWritesUnparseableDependencies() throws Exception {
    final GradleBuildFile file = getTestFile("");
    final List<BuildFileStatement> deps = ImmutableList.of(
      new UnparseableStatement("// Comment 1", getProject()),
      new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "foo.com:1.0.0"),
      new UnparseableStatement("compile random.expression", getProject()),
      new UnparseableStatement("functionCall()", getProject()), new UnparseableStatement("random.expression", getProject())
    );
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, deps);
      }
    });
    String expected =
      "dependencies {\n" +
      "    // Comment 1\n" +
      "    compile 'foo.com:1.0.0'\n" +
      "    compile random.expression\n" +
      "    functionCall()\n" +
      "    random.expression\n" +
      "}";

    assertContents(expected);
  }

  public void testReadsUnparseableRepositories() throws Exception {
    GradleBuildFile file = getTestFile(
      "repositories {\n" +
      "    // Comment 1\n" +
      "    mavenCentral()\n" +
      "    maven { url random.expression }\n" +
      "    functionCall()\n" +
      "    random.expression\n" +
      "}"
    );
    List<BuildFileStatement> expected = ImmutableList.of(
      new UnparseableStatement("// Comment 1", getProject()),
      new Repository(Repository.Type.MAVEN_CENTRAL, null),
      new UnparseableStatement("maven { url random.expression }", getProject()),
      new UnparseableStatement("functionCall()", getProject()),
      new UnparseableStatement("random.expression", getProject())
    );
    assertEquals(expected, file.getValue(BuildFileKey.LIBRARY_REPOSITORY));
  }

  public void testWritesUnparseableRepositories() throws Exception {
    final GradleBuildFile file = getTestFile("");
    final List<BuildFileStatement> deps = ImmutableList.of(
      new UnparseableStatement("// Comment 1", getProject()),
      new Repository(Repository.Type.MAVEN_CENTRAL, null),
      new UnparseableStatement("maven { url random.expression }", getProject()),
      new UnparseableStatement("functionCall()", getProject()),
      new UnparseableStatement("random.expression", getProject())
    );
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, deps);
      }
    });
    String expected =
      "dependencies {\n" +
      "    // Comment 1\n" +
      "    mavenCentral()\n" +
      "    maven { url random.expression }\n" +
      "    functionCall()\n" +
      "    random.expression\n" +
      "}";

    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testReadsNamedObjectsWithUnparseableValues() throws Exception {
    GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildTypes {\n" +
      "        type1 {\n" +
      "            //Comment\n" +
      "            debuggable true\n" +
      "            minifyEnabled methodCall() {\n" +
      "                whatIsThisMethodCall()\n" +
      "            }\n" +
      "            zipAlignEnabled some.expression\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
    List<NamedObject> objects = (List<NamedObject>)file.getValue(BuildFileKey.BUILD_TYPES);
    assertNotNull("build types should be parsed", objects);
    assertEquals(1, objects.size());
    NamedObject no = objects.get(0);
    assertEquals(true, no.getValue(BuildFileKey.DEBUGGABLE));
    assertSame(UNRECOGNIZED_VALUE, no.getValue(BuildFileKey.MINIFY_ENABLED));
    assertSame(UNRECOGNIZED_VALUE, no.getValue(BuildFileKey.ZIP_ALIGN));
  }

  public void testWritesNamedObjectsWithUnparseableValues() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildTypes {\n" +
      "        type1 {\n" +
      "            //Comment\n" +
      "            debuggable true\n" +
      "            minifyEnabled methodCall() {\n" +
      "                whatIsThisMethodCall()\n" +
      "            }\n" +
      "            zipAlignEnabled some.expression\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
    final List<NamedObject> objects = WriteCommandAction.runWriteCommandAction(myProject, new Computable<List<NamedObject>>() {
      @SuppressWarnings({"ConstantConditions", "unchecked"})
      @Override
      public List<NamedObject> compute() {
        return (List<NamedObject>)file.getValue(BuildFileKey.BUILD_TYPES);
      }
    });
    assertEquals(1, objects.size());
    final NamedObject no = objects.get(0);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        no.setValue(BuildFileKey.DEBUGGABLE, false);
        no.setValue(BuildFileKey.ZIP_ALIGN, false);
        file.setValue(BuildFileKey.BUILD_TYPES, objects);
      }
    });
    String expected =
      "android {\n" +
      "    buildTypes {\n" +
      "        type1 {\n" +
      "            //Comment\n" +
      "            debuggable false\n" +
      "            minifyEnabled methodCall() {\n" +
      "                whatIsThisMethodCall()\n" +
      "            }\n" +
      "            zipAlignEnabled false\n" +
      "        }\n" +
      "    }\n" +
      "}";

    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testRemoveNamedObjectValue() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildTypes {\n" +
      "        type1 {\n" +
      "            //Comment\n" +
      "            debuggable true\n" +
      "            minifyEnabled methodCall() {\n" +
      "                whatIsThisMethodCall()\n" +
      "            }\n" +
      "            zipAlignEnabled some.expression\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
    final List<NamedObject> objects = (List<NamedObject>)file.getValue(BuildFileKey.BUILD_TYPES);
    assertNotNull("build types should be parsed", objects);
    assertEquals(1, objects.size());
    NamedObject no = objects.get(0);
    no.getValues().remove(BuildFileKey.DEBUGGABLE);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, objects);
      }
    });

    String expected =
      "android {\n" +
      "    buildTypes {\n" +
      "        type1 {\n" +
      "            //Comment\n" +
      "            minifyEnabled methodCall() {\n" +
      "                whatIsThisMethodCall()\n" +
      "            }\n" +
      "            zipAlignEnabled some.expression\n" +
      "        }\n" +
      "    }\n" +
      "}";

    assertContents(expected);
  }

  public void testCreatesNamedObjects() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "}"
    );
    NamedObject no = new NamedObject("buildType1");
    no.setValue(BuildFileKey.DEBUGGABLE, true);
    final List<NamedObject> objects = ImmutableList.of(no);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, objects);
      }
    });

    String expected =
      "android {\n" +
      "    buildTypes {\n" +
      "        buildType1 {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "    }\n" +
      "}";

    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testDeletesNamedObjects() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildTypes {\n" +
      "        buildType1 {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "        buildType2 {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
    final List<NamedObject> objects = (List<NamedObject>)file.getValue(BuildFileKey.BUILD_TYPES);
    assertNotNull(objects);
    assertEquals(2, objects.size());
    objects.remove(0);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, objects);
      }
    });

    String expected =
      "android {\n" +
      "    buildTypes {\n" +
      "        buildType2 {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "}";

    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testCreatesNamedObjectsWithUnparseableStatements() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildTypes {\n" +
      "        // I am a comment\n" +
      "        buildType1 {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "        // I am another comment\n" +
      "    }\n" +
      "}"
    );
    final List<NamedObject> objects = (List<NamedObject>)file.getValue(BuildFileKey.BUILD_TYPES);
    assertNotNull(objects);
    assertEquals(1, objects.size());
    NamedObject no = new NamedObject("buildType2");
    no.setValue(BuildFileKey.DEBUGGABLE, false);
    objects.add(no);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, objects);
      }
    });

    String expected =
      "android {\n" +
      "    buildTypes {\n" +
      "        // I am a comment\n" +
      "        buildType1 {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "        // I am another comment\n" +
      "        buildType2 {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "}";

    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testDeletesNamedObjectsWithUnparseableStatements() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildTypes {\n" +
      "        // I am a comment\n" +
      "        buildType1 {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "        // I am another comment\n" +
      "        buildType2 {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
    final List<NamedObject> objects = (List<NamedObject>)file.getValue(BuildFileKey.BUILD_TYPES);
    assertNotNull(objects);
    assertEquals(2, objects.size());
    objects.remove(0);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TYPES, objects);
      }
    });

    String expected =
      "android {\n" +
      "    buildTypes {\n" +
      "        // I am a comment\n" +
      "        // I am another comment\n" +
      "        buildType2 {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "}";

    assertContents(expected);
  }

  public void testGetsPlugins() throws Exception {
    GradleBuildFile file = getTestFile(
      "apply plugin: 'android' \n" +
      "apply plugin: 'java'\n"
    );
    List<String> expected = ImmutableList.of(
      "android",
      "java"
    );
    assertEquals(expected, file.getPlugins());
  }

  public void testAddsSigningConfigsAtBeginning() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "}\n"
    );
    final NamedObject config = new NamedObject("config" );
    config.setValue(BuildFileKey.KEY_ALIAS, "alias" );
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.SIGNING_CONFIGS, ImmutableList.of(config));
      }
    });

    String expected =
      "android {\n" +
      "    signingConfigs {\n" +
      "        config {\n" +
      "            keyAlias 'alias'\n" +
      "        }\n" +
      "    }\n" +
      "    compileSdkVersion 17\n" +
      "}\n";

    assertContents(expected);
  }

  public void testAddsSigningConfigsToEmptyBlock() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "}\n"
    );
    final NamedObject config = new NamedObject("config");
    config.setValue(BuildFileKey.KEY_ALIAS, "alias");
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.SIGNING_CONFIGS, ImmutableList.of(config));
      }
    });

    String expected =
      "android {\n" +
      "    signingConfigs {\n" +
      "        config {\n" +
      "            keyAlias 'alias'\n" +
      "        }\n" +
      "    }\n" +
      "}\n";

    assertContents(expected);
  }

  public void testSetIntegerOrStringAsInteger() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    defaultConfig {\n" +
      "    }\n" +
      "}\n"
    );
    final GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.TARGET_SDK_VERSION, 5);
      }
    });

    String expected =
      "android {\n" +
      "    defaultConfig {\n" +
      "        targetSdkVersion 5\n" +
      "    }\n" +
      "}\n";

    assertContents(expected);
  }


  public void testSetIntegerOrStringAsIntegerValuedString() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    defaultConfig {\n" +
      "    }\n" +
      "}\n"
    );
    final GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.TARGET_SDK_VERSION, "5");
      }
    });

    String expected =
      "android {\n" +
      "    defaultConfig {\n" +
      "        targetSdkVersion 5\n" +
      "    }\n" +
      "}\n";

    assertContents(expected);
  }

  public void testSetIntegerOrStringAsString() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    defaultConfig {\n" +
      "    }\n" +
      "}\n"
    );
    final GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.TARGET_SDK_VERSION, "foo");
      }
    });

    String expected =
      "android {\n" +
      "    defaultConfig {\n" +
      "        targetSdkVersion 'foo'\n" +
      "    }\n" +
      "}\n";

    assertContents(expected);
  }


  public void testSetIntegerOrStringAsStringWithLeadingDigit() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    defaultConfig {\n" +
      "    }\n" +
      "}\n"
    );
    final GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.TARGET_SDK_VERSION, "123foo");
      }
    });

    String expected =
      "android {\n" +
      "    defaultConfig {\n" +
      "        targetSdkVersion '123foo'\n" +
      "    }\n" +
      "}\n";

    assertContents(expected);
    }

  public void testGetIntegerOrStringAsString() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    defaultConfig {\n" +
      "        targetSdkVersion 'foo'\n" +
      "    }\n" +
      "}\n"
    );
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    assertEquals("foo", file.getValue(closure, BuildFileKey.TARGET_SDK_VERSION));
  }

  public void testGetIntegerOrStringAsInteger() throws Exception {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    defaultConfig {\n" +
      "        targetSdkVersion 5\n" +
      "    }\n" +
      "}\n"
    );
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertNotNull(closure);
    assertEquals("5", file.getValue(closure, BuildFileKey.TARGET_SDK_VERSION));
  }

  public void testPreservesDependencyExcludes() throws Exception {
    final GradleBuildFile file = getTestFile(
      "dependencies {\n" +
      "    compile('com.android.support:support-v4:13.0.+') {\n" +
      "        exclude module: 'blah'\n" +
      "    }\n" +
      "}\n");

    final List<BuildFileStatement> dependencies = file.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency newDependency = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.foo:1.0", null);
    dependencies.add(newDependency);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, dependencies);
      }
    });

    String expected =
      "dependencies {\n" +
      "    compile('com.android.support:support-v4:13.0.+') {\n" +
      "        exclude module: 'blah'\n" +
      "    }\n" +
      "    compile 'com.foo:1.0'\n" +
      "}\n";
    assertContents(expected);
  }

  public void testPreservesModuleDependencyExcludes() throws Exception {
    final GradleBuildFile file = getTestFile(
      "dependencies {\n" +
      "    compile(project(':blah')) {\n" +
      "        exclude group: 'blah'\n" +
      "    }\n" +
      "}\n");

    final List<BuildFileStatement> dependencies = file.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency newDependency = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, "com.foo:1.0", null);
    dependencies.add(newDependency);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, dependencies);
      }
    });

    String expected =
      "dependencies {\n" +
      "    compile(project(':blah')) {\n" +
      "        exclude group: 'blah'\n" +
      "    }\n" +
      "    compile 'com.foo:1.0'\n" +
      "}\n";
    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testFiltering() throws IOException {
    final GradleBuildFile file = getTestFile(
      "android {\n" +
      "    signingConfigs {\n" +
      "        debug {\n" +
      "            keyAlias 'a1'\n" +
      "            keyPassword 'a2'\n" +
      "            storeFile file('/a3')\n" +
      "            storePassword 'a4'\n" +
      "        }\n" +
      "    }\n" +
      "}\n");
    final List<NamedObject> signingConfigs = (List<NamedObject>)file.getValue(BuildFileKey.SIGNING_CONFIGS);
    assertNotNull("signing configs should be parsed", signingConfigs);
    assertEquals(1, signingConfigs.size());
    NamedObject signingConfig = signingConfigs.get(0);
    assertEquals("a1", signingConfig.getValue(BuildFileKey.KEY_ALIAS));
    assertEquals("a2", signingConfig.getValue(BuildFileKey.KEY_PASSWORD));
    assertEquals(new File("/a3"), signingConfig.getValue(BuildFileKey.STORE_FILE));
    assertEquals("a4", signingConfig.getValue(BuildFileKey.STORE_PASSWORD));

    signingConfig.setValue(BuildFileKey.KEY_ALIAS, "b1");
    signingConfig.setValue(BuildFileKey.KEY_PASSWORD, "b2");
    signingConfig.setValue(BuildFileKey.STORE_FILE, new File("/b3"));
    signingConfig.setValue(BuildFileKey.STORE_PASSWORD, "b4");

    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.SIGNING_CONFIGS, signingConfigs, new ValueFactory.KeyFilter() {
          @Override
          public boolean shouldWriteKey(BuildFileKey key, Object object) {
            return key == BuildFileKey.KEY_ALIAS;
          }});}});

    String expected =
      "android {\n" +
      "    signingConfigs {\n" +
      "        debug {\n" +
      "            keyAlias 'b1'\n" +
      "            keyPassword 'a2'\n" +
      "            storeFile file('/a3')\n" +
      "            storePassword 'a4'\n" +
      "        }\n" +
      "    }\n" +
      "}\n";
    assertContents(expected);
  }

  @SuppressWarnings("unchecked")
  public void testRepositoryCredentials() throws Exception {
    final GradleBuildFile file = getTestFile(
      "repositories {\n" +
      "    maven {\n" +
      "        url 'www.foo.com'\n" +
      "        credentials {\n" +
      "            username 'user'\n" +
      "            password 'password'\n" +
      "        }\n" +
      "    }\n" +
      "}\n");

    final List<Repository> repositories = (List<Repository>)file.getValue(BuildFileKey.LIBRARY_REPOSITORY);
    assertNotNull("repositories should be parsed", repositories);
    assertEquals(1, repositories.size());
    Repository newRepository = new Repository(Repository.Type.MAVEN_CENTRAL, null);
    repositories.add(newRepository);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.LIBRARY_REPOSITORY, repositories);
      }
    });

    String expected =
      "repositories {\n" +
      "    maven {\n" +
      "        url 'www.foo.com'\n" +
      "        credentials {\n" +
      "            username 'user'\n" +
      "            password 'password'\n" +
      "        }\n" +
      "    }\n" +
      "    mavenCentral()\n" +
      "}\n";
    assertContents(expected);
  }

  public void testShouldWriteValue() {
    List emptyList = ImmutableList.of();
    Repository mc1 = new Repository(Repository.Type.MAVEN_CENTRAL, null);
    Repository mc2 = new Repository(Repository.Type.MAVEN_CENTRAL, null);
    Repository mc3 = new Repository(Repository.Type.MAVEN_CENTRAL, null);
    Repository ml = new Repository(Repository.Type.MAVEN_LOCAL, null);
    BuildFileStatement up1 = new UnparseableStatement("I'm a little teapot", myProject);
    BuildFileStatement up2 = new UnparseableStatement("Here is my spout", myProject);
    List<Repository> listOne = ImmutableList.of(mc1, mc2);
    List<Repository> otherListOne = ImmutableList.of(mc2, mc1);
    List<Repository> listTwo = ImmutableList.of(mc1, ml);
    List<Repository> longList = ImmutableList.of(mc1, mc2, mc3);
    List<BuildFileStatement> unparseableOne = ImmutableList.of(mc1, up1);
    List<BuildFileStatement> unparseableTwo = ImmutableList.of(mc2, up2);

    assertFalse(GradleBuildFile.shouldWriteValue(null, null));
    assertTrue(GradleBuildFile.shouldWriteValue(emptyList, null));
    assertTrue(GradleBuildFile.shouldWriteValue(null, emptyList));
    assertTrue(GradleBuildFile.shouldWriteValue(mc1, ml));
    assertFalse(GradleBuildFile.shouldWriteValue(mc1, mc2));
    assertFalse(GradleBuildFile.shouldWriteValue(listOne, otherListOne));
    assertTrue(GradleBuildFile.shouldWriteValue(listOne, listTwo));
    assertTrue(GradleBuildFile.shouldWriteValue(listOne, longList));

    // Even though the unparseables are different, we ignore them for the purposes of deciding
    // whether to write them out.
    assertFalse(unparseableOne.equals(unparseableTwo));
    assertFalse(GradleBuildFile.shouldWriteValue(unparseableOne, unparseableTwo));
  }

  public void testExternalizedDependencyVersion() throws IOException {
    String fileContent =
      "dependencies {\n" +
      "    compile \"com.android.support:appcompat-v7:${versions.support}\"\n" +
      "}";
    final GradleBuildFile file = getTestFile(fileContent);
    final Object dependencies = file.getValue(BuildFileKey.DEPENDENCIES);
    assertNotNull(dependencies);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, dependencies);
      }
    });
    assertContents(fileContent);
  }

  private static String getSimpleTestFile() throws IOException {
    return
      "buildscript {\n" +
      "    repositories {\n" +
      "        mavenCentral()\n" +
      "    }\n" +
      "    dependencies {\n" +
      "        classpath 'com.android.tools.build:gradle:0.5.+'\n" +
      "    }\n" +
      "}\n" +
      "apply plugin: 'android'\n" +
      "repositories {\n" +
      "    mavenCentral()\n" +
      "    maven('www.foo1.com', 'www.foo2.com')\n" +
      "    maven {\n" +
      "        url 'www.foo3.com'\n" +
      "        url 'www.foo4.com'\n" +
      "    }\n" +
      "}\n" +
      "dependencies {\n" +
      "    compile 'com.android.support:support-v4:13.0.+'\n" +
      "}\n" +
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "    buildToolsVersion '17.0.0'\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion someCrazyMethodCall()\n" +
      "        targetSdkVersion 1\n" +
      "        versionCode 1337\n" +
      "    }\n" +
      "    buildTypes {\n" +
      "        debug {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "        release {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "    signingConfigs {\n" +
      "        debug {\n" +
      "            storeFile file('debug.keystore')\n" +
      "        }\n" +
      "        config2 {\n" +
      "        }\n" +
      "    }\n" +
      "    productFlavors {\n" +
      "        flavor1 {\n" +
      "            proguardFile 'proguard-flavor1.txt'\n" +
      "        }\n" +
      "        flavor2 {\n" +
      "        }\n" +
      "    }\n" +
      "}";
  }

  private GradleBuildFile getTestFile(String contents) throws IOException {
    VirtualFile vf = getVirtualFile(createTempFile(SdkConstants.FN_BUILD_GRADLE, contents));
    assertNotNull("test gradle config should be registered in VFS", vf);
    myDocument = FileDocumentManager.getInstance().getDocument(vf);
    return new GradleBuildFile(vf, getProject());
  }

  private GradleBuildFile getBadGradleBuildFile() {
    // Use an intentionally invalid file path so that GradleBuildFile will remain uninitialized. This simulates the condition of
    // the PSI file not being parsed yet. GradleBuildFile will warn about the PSI file; this is expected.
    VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(FileUtil.getTempDirectory());
    assertNotNull(vf);
    return new GradleBuildFile(vf, getProject());
  }

  private GrStatementOwner getDummyClosure() {
    return GroovyPsiElementFactory.getInstance(myProject).createClosureFromText("{}");
  }

  private void assertContents(String expected) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitDocument(myDocument);
    String actual = myDocument.getText();
    assertEquals(expected, actual);
  }
}
