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
package com.android.tools.idea.gradle;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link AndroidJunitPatcher}.
 */
public class AndroidJunitPatcherTest extends AndroidTestCase {
  private List<String> myExampleClassPath;
  private Set<String> myExampleClassPathSet;
  private String myRealAndroidJar;
  private String myMockableAndroidJar;
  private Collection<String> myResourcesDirs;


  private AndroidJunitPatcher myPatcher;
  private JavaParameters myJavaParameters;
  private AndroidProjectStub myAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpIdeaAndroidProject();
    setUpExampleClasspath();

    myPatcher = new AndroidJunitPatcher();
    myJavaParameters = new JavaParameters();
    myJavaParameters.getClassPath().addAll(myExampleClassPath);

    // Sanity check. These should be fixed by the patcher.
    assertContainsElements(myExampleClassPath, myRealAndroidJar);
    assertContainsElements(myExampleClassPath, myMockableAndroidJar);
    assertDoesntContain(myExampleClassPath, myResourcesDirs);
    assertFalse(Iterables.getLast(myExampleClassPath).equals(myMockableAndroidJar));
  }

  private void setUpExampleClasspath() {
    String root = myAndroidProject.getRootDir().getPath();
    myExampleClassPath = Lists.newArrayList(
      root + "/build/intermediates/classes/debug",
      root + "/build/intermediates/classes/test/debug",
      root + "/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.0.0/classes.jar",
      root + "/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.0.0/res",
      root + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/classes.jar",
      root + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/libs/internal_impl-22.0.0.jar",
      root + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/res",
      "/home/user/.gradle/caches/modules-2/files-2.1/junit/junit/4.12/2973d150c0dc1fefe998f834810d68f278ea58ec/junit-4.12.jar",
      "/idea/production/java-runtime",
      "/idea/production/junit_rt");

    myMockableAndroidJar = root + "/build/intermediates/mockable-android-22.jar";
    myRealAndroidJar = FileUtil.toCanonicalPath(getTestSdkPath() + "/platforms/android-1.5/android.jar");
    myResourcesDirs = ImmutableList.of(root + "/build/intermediates/javaResources/debug",
                                       root + "/build/intermediates/javaResources/test/debug");

    myExampleClassPath.add(0, myMockableAndroidJar);
    myExampleClassPath.add(0, myRealAndroidJar);

    myExampleClassPathSet = ImmutableSet.copyOf(myExampleClassPath);
  }

  private void setUpIdeaAndroidProject() {
    myAndroidProject = TestProjects.createBasicProject();
    VariantStub variant = myAndroidProject.getFirstVariant();
    assertNotNull(variant);
    IdeaAndroidProject ideaAndroidProject = new IdeaAndroidProject(GradleConstants.SYSTEM_ID, myAndroidProject.getName(),
                                                                   myAndroidProject.getRootDir(), myAndroidProject, variant.getName(),
                                                                   AndroidProject.ARTIFACT_UNIT_TEST);
    myFacet.setIdeaAndroidProject(ideaAndroidProject);
  }

  public void testPathChanges() throws Exception {
    myPatcher.patchJavaParameters(myModule, myJavaParameters);
    List<String> result = myJavaParameters.getClassPath().getPathList();
    Set<String> resultSet = ImmutableSet.copyOf(result);
    assertDoesntContain(result, myRealAndroidJar);

    // Mockable JAR is at the end:
    assertEquals(Iterables.getLast(result), myMockableAndroidJar);
    // Only the real android.jar was removed:
    assertContainsElements(Sets.difference(myExampleClassPathSet, resultSet), myRealAndroidJar);
    // Only expected entries were added:
    assertContainsElements(Sets.difference(resultSet, myExampleClassPathSet), myResourcesDirs);
  }
}