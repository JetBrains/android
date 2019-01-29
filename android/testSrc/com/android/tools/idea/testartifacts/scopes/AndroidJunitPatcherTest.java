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
package com.android.tools.idea.testartifacts.scopes;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.JavaArtifact;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.stubs.android.AndroidArtifactStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.JavaArtifactStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathsList;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.testutils.TestUtils.getLatestAndroidPlatform;
import static com.android.testutils.TestUtils.getPlatformFile;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.normalize;
import static com.intellij.util.ArrayUtil.contains;
import static com.intellij.util.containers.ContainerUtil.map;

/**
 * Tests for {@link AndroidJunitPatcher}.
 */
public class AndroidJunitPatcherTest extends AndroidTestCase {
  private static final String[] TEST_ARTIFACT_NAMES = {ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST};

  private Set<String> myExampleClassPathSet;
  private String myRealAndroidJar;
  private String myMockableAndroidJar;
  private String myKotlinClasses;
  private String myTestKotlinClasses;
  private Collection<String> myResourcesDirs;

  private AndroidJunitPatcher myPatcher;
  private JavaParameters myJavaParameters;
  private AndroidProjectStub myAndroidProject;
  private String myRoot;
  private VariantStub mySelectedVariant;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpIdeaAndroidProject();

    myPatcher = new AndroidJunitPatcher();
    myJavaParameters = new JavaParameters();
    myJavaParameters.getClassPath().addAll(getExampleClasspath());

    // Adding the facet makes Projects#isBuildWithGradle return 'true'.
    createAndAddGradleFacet(myModule);
  }

  @NotNull
  private List<String> getExampleClasspath() {
    myRoot = normalize(myAndroidProject.getRootDir().getPath());
    List<String> exampleClassPath =
      Lists.newArrayList(myRoot + "/build/intermediates/classes/debug", myRoot + "/build/intermediates/classes/test/debug",
                         myRoot + "/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.0.0/classes.jar",
                         myRoot + "/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.0.0/res",
                         myRoot + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/classes.jar",
                         myRoot + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/libs/internal_impl-22.0.0.jar",
                         myRoot + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/res",
                         "/home/user/.gradle/caches/modules-2/files-2.1/junit/junit/4.12/2973d150c0dc1fefe998f834810d68f278ea58ec/junit-4.12.jar",
                         "/idea/production/java-runtime", "/idea/production/junit_rt");

    myMockableAndroidJar = myRoot + "/build/intermediates/mockable-" + getLatestAndroidPlatform() + ".jar";
    myKotlinClasses = myRoot + "/build/tmp/kotlin-classes/debug";
    myTestKotlinClasses = myRoot + "/build/tmp/kotlin-classes/debugUnitTest";
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(myModule);
    assertNotNull(androidPlatform);
    myRealAndroidJar = getPlatformFile("android.jar").toString();
    myResourcesDirs = ImmutableList.of(myRoot + "/build/intermediates/javaResources/debug",
                                       myRoot + "/build/intermediates/javaResources/test/debug");

    exampleClassPath.add(0, myMockableAndroidJar);
    exampleClassPath.add(0, myRealAndroidJar);

    myExampleClassPathSet = ImmutableSet.copyOf(exampleClassPath);

    // Sanity check. These should be fixed by the patcher.
    assertThat(exampleClassPath).containsAllOf(myRealAndroidJar, myMockableAndroidJar);
    assertDoesntContain(exampleClassPath, myResourcesDirs);
    assertFalse(Iterables.getLast(exampleClassPath).equals(myMockableAndroidJar));

    return exampleClassPath;
  }

  private void setUpIdeaAndroidProject() {
    myAndroidProject = TestProjects.createBasicProject();
    createAndSetAndroidModel();
    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      TestArtifactSearchScopes.initializeScope(module);
    }
  }

  public void testPathChanges() throws Exception {
    myPatcher.patchJavaParameters(myModule, myJavaParameters);
    List<String> result = map(myJavaParameters.getClassPath().getPathList(), FileUtil::normalize);
    Set<String> resultSet = ImmutableSet.copyOf(result);
    assertThat(result).doesNotContain(myRealAndroidJar);

    // Mockable JAR is at the end:
    assertEquals(myMockableAndroidJar, Iterables.getLast(result));
    // Only the real android.jar was removed:
    assertThat(Sets.difference(myExampleClassPathSet, resultSet)).contains(myRealAndroidJar);
    // Only expected entries were added:
    assertThat(Sets.difference(resultSet, myExampleClassPathSet)).containsAllIn(myResourcesDirs);
  }

  public void testCaseInsensitivity() throws Exception {
    if (!SystemInfo.isWindows) {
      // This test only makes sense on Windows.
      System.out.println("Skipping AndroidJunitPatcherTest#testCaseInsensitivity: not running on Windows.");
      return;
    }

    myJavaParameters.getClassPath().remove(myRealAndroidJar);
    // It's still the same file on Windows:
    String alsoRealAndroidJar = myRealAndroidJar.replace("platforms", "Platforms");
    myJavaParameters.getClassPath().addFirst(alsoRealAndroidJar);

    myPatcher.patchJavaParameters(myModule, myJavaParameters);
    List<String> result = myJavaParameters.getClassPath().getPathList();
    assertThat(result).containsNoneOf(alsoRealAndroidJar, myRealAndroidJar);
  }

  public void testMultipleMockableJars_oldModel() throws Exception {
    String jar22 = myRoot + "lib1/build/intermediates/mockable-android-22.jar";
    String jar15 = myRoot + "lib2/build/intermediates/mockable-android-15.jar";
    PathsList classPath = myJavaParameters.getClassPath();
    classPath.addFirst(jar22);
    classPath.addFirst(jar15);

    myPatcher.patchJavaParameters(myModule, myJavaParameters);

    List<String> pathList = classPath.getPathList();
    assertEquals(myMockableAndroidJar, Iterables.getLast(pathList));
    assertThat(pathList).containsNoneOf(jar15, jar22);
  }

  public void testMultipleMockableJars_newModel() throws Exception {
    myJavaParameters.getClassPath().remove(myMockableAndroidJar);

    JavaArtifactStub testArtifact = getUnitTestArtifact();
    assert testArtifact != null;
    testArtifact.setMockablePlatformJar(new File(myMockableAndroidJar));
    createAndSetAndroidModel();

    myPatcher.patchJavaParameters(myModule, myJavaParameters);

    assertEquals(normalize(myMockableAndroidJar), normalize(Iterables.getLast(myJavaParameters.getClassPath().getPathList())));
  }

  public void testKotlinClasses() throws Exception {
    myJavaParameters.getClassPath().remove(myMockableAndroidJar);

    AndroidModuleModel model = AndroidModuleModel.get(myFacet);
    assert model != null;

    AndroidArtifactStub artifact = mySelectedVariant.getMainArtifact();
    artifact.addAdditionalClassesFolder(new File(myKotlinClasses));
    JavaArtifactStub testArtifact = getUnitTestArtifact();
    assert testArtifact != null;
    File testKotlinClassesDir = new File(myTestKotlinClasses);
    testArtifact.addAdditionalClassesFolder(testKotlinClassesDir);
    createAndSetAndroidModel();

    myPatcher.patchJavaParameters(myModule, myJavaParameters);

    assertThat(myJavaParameters.getClassPath().getPathList()).contains(testKotlinClassesDir.getPath());
  }

  @Nullable
  public JavaArtifactStub getUnitTestArtifact() {
    for (JavaArtifact artifact : mySelectedVariant.getExtraJavaArtifacts()) {
      JavaArtifactStub stub = (JavaArtifactStub)artifact;
      if (isTestArtifact(stub)) {
        return stub;
      }
    }
    return null;
  }

  private static boolean isTestArtifact(@NotNull BaseArtifact artifact) {
    String artifactName = artifact.getName();
    return isTestArtifact(artifactName);
  }

  private static boolean isTestArtifact(@Nullable String artifactName) {
    return contains(artifactName, TEST_ARTIFACT_NAMES);
  }

  private void createAndSetAndroidModel() {
    mySelectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(mySelectedVariant);
    AndroidModuleModel model = new AndroidModuleModel(myAndroidProject.getName(), myAndroidProject.getRootDir(), myAndroidProject,
                                                      mySelectedVariant.getName(), new IdeDependenciesFactory());
    myFacet.getConfiguration().setModel(model);
  }
}