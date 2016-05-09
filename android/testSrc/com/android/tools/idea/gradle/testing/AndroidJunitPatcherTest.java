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
package com.android.tools.idea.gradle.testing;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.JavaArtifactStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.normalize;

/**
 * Tests for {@link AndroidJunitPatcher}.
 */
public class AndroidJunitPatcherTest extends AndroidTestCase {
  private Set<String> myExampleClassPathSet;
  private String myRealAndroidJar;
  private String myMockableAndroidJar;
  private Collection<String> myResourcesDirs;

  private AndroidJunitPatcher myPatcher;
  private JavaParameters myJavaParameters;
  private AndroidProjectStub myAndroidProject;
  private String myRoot;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpIdeaAndroidProject();

    myPatcher = new AndroidJunitPatcher();
    myJavaParameters = new JavaParameters();
    myJavaParameters.getClassPath().addAll(getExampleClasspath());

    // Adding the facet makes Projects#isBuildWithGradle return 'true'.
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FacetManager facetManager = FacetManager.getInstance(myModule);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          AndroidGradleFacet facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
          model.addFacet(facet);
        }
        finally {
          model.commit();
        }
      }
    });
  }

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

    myMockableAndroidJar = myRoot + "/build/intermediates/mockable-android-17.jar";
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(myModule);
    assertNotNull(androidPlatform);
    assertEquals(17, androidPlatform.getApiLevel()); // Sanity check.
    myRealAndroidJar = FileUtil.toCanonicalPath(getTestSdkPath() + "/platforms/android-1.5/android.jar");
    myResourcesDirs = ImmutableList.of(myRoot + "/build/intermediates/javaResources/debug",
                                       myRoot + "/build/intermediates/javaResources/test/debug");

    exampleClassPath.add(0, myMockableAndroidJar);
    exampleClassPath.add(0, myRealAndroidJar);

    myExampleClassPathSet = ImmutableSet.copyOf(exampleClassPath);

    // Sanity check. These should be fixed by the patcher.
    assertContainsElements(exampleClassPath, myRealAndroidJar);
    assertContainsElements(exampleClassPath, myMockableAndroidJar);
    assertDoesntContain(exampleClassPath, myResourcesDirs);
    assertFalse(Iterables.getLast(exampleClassPath).equals(myMockableAndroidJar));

    return exampleClassPath;
  }

  private void setUpIdeaAndroidProject() {
    myAndroidProject = TestProjects.createBasicProject();
    VariantStub variant = myAndroidProject.getFirstVariant();
    assertNotNull(variant);
    AndroidGradleModel androidModel = new AndroidGradleModel(GradleConstants.SYSTEM_ID, myAndroidProject.getName(),
                                                             myAndroidProject.getRootDir(), myAndroidProject, variant.getName(),
                                                             AndroidProject.ARTIFACT_UNIT_TEST);
    myFacet.setAndroidModel(androidModel);
    TestArtifactSearchScopes.initializeScopes(getProject());
  }

  public void testPathChanges() throws Exception {
    myPatcher.patchJavaParameters(myModule, myJavaParameters);
    List<String> result = ContainerUtil.map(myJavaParameters.getClassPath().getPathList(), new Function<String, String>() {
      @Override
      public String fun(String s) {
        return normalize(s);
      }
    });
    Set<String> resultSet = ImmutableSet.copyOf(result);
    assertDoesntContain(result, myRealAndroidJar);

    // Mockable JAR is at the end:
    assertEquals(myMockableAndroidJar, Iterables.getLast(result));
    // Only the real android.jar was removed:
    assertContainsElements(Sets.difference(myExampleClassPathSet, resultSet), myRealAndroidJar);
    // Only expected entries were added:
    assertContainsElements(Sets.difference(resultSet, myExampleClassPathSet), myResourcesDirs);
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

  @SuppressWarnings("ConstantConditions") // No risk of NPEs.
  public void testMultipleMockableJars_newModel() throws Exception {
    myJavaParameters.getClassPath().remove(myMockableAndroidJar);

    JavaArtifactStub artifact = (JavaArtifactStub)AndroidGradleModel.get(myFacet).findSelectedTestArtifactInSelectedVariant();
    artifact.setMockablePlatformJar(new File(myMockableAndroidJar));
    myPatcher.patchJavaParameters(myModule, myJavaParameters);

    assertEquals(normalize(myMockableAndroidJar), normalize(Iterables.getLast(myJavaParameters.getClassPath().getPathList())));
  }
}