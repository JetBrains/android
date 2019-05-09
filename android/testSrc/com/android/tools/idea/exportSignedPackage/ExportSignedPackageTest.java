/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.exportSignedPackage;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Sets;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_MULTIFLAVOR;
import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_NO_FLAVORS;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.decapitalize;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class ExportSignedPackageTest extends AndroidGradleTestCase {
  public void testNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getVariantNames().size());

    List<String> assembleTasks =
      ExportSignedPackageWizard.getGradleTasks("", getModel(), singletonList("release"), ExportSignedPackageWizard.APK);
    assertEquals(1, assembleTasks.size());
    assertEquals(":assembleRelease", assembleTasks.get(0));
  }

  public void testFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertEquals(8, androidProject.getVariantNames().size());

    Set<String> assembleTasks = Sets.newHashSet(
      ExportSignedPackageWizard.getGradleTasks("", getModel(), asList("proX86Release", "freeArmRelease"), ExportSignedPackageWizard.APK));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":assembleProX86Release"));
    assertTrue(assembleTasks.contains(":assembleFreeArmRelease"));
  }

  public void testApkLocationCorrect() {
    // This test guarantees user is taken to the folder with the selected build type outputs
    assertEquals(toSystemDependentName("path/to/folder/release"),
                 ExportSignedPackageWizard.getApkLocation("path/to/folder", "release").toString());
  }

  public void testBundleNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getVariantNames().size());

    List<String> assembleTasks =
      ExportSignedPackageWizard.getGradleTasks("", getModel(), singletonList("release"), ExportSignedPackageWizard.BUNDLE);
    assertEquals(1, assembleTasks.size());
    assertEquals(":bundleRelease", assembleTasks.get(0));
  }

  public void testBundleFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertEquals(8, androidProject.getVariantNames().size());

    Set<String> assembleTasks = Sets.newHashSet(ExportSignedPackageWizard
                                                  .getGradleTasks("", getModel(), asList("proX86Release", "freeArmRelease"),
                                                                  ExportSignedPackageWizard.BUNDLE));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":bundleProX86Release"));
    assertTrue(assembleTasks.contains(":bundleFreeArmRelease"));
  }

  public void testReplaceVariantFromTask() {
    assertEquals(":flavorBuildType",
                 ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantName", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskPre() {
    assertEquals(":prefixFlavorBuildType",
                 ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantName", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskSuf() {
    assertEquals(":flavorBuildTypeSuffix",
                 ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantNameSuffix", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskPreSuf() {
    assertEquals(":prefixFlavorBuildTypeSuffix",
                 ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantNameSuffix", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissing() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantName", "NonVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissingPre() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantName", "NonVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissingSuf() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantNameSuffix", "NonVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissingPreSuf() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantNameSuffix", "NonVariantName", "flavorBuildType"));
  }

  /**
   * Verify that assemble task contains variant name in the form expected by {@link ExportSignedPackageWizard#getGradleTasks} when there are
   * no flavors.
   */
  public void testVariantNameInAssembleTaskNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    AndroidModuleModel androidModel = getModel();

    Variant selectedVariant = androidModel.getSelectedVariant();
    String variantName = selectedVariant.getName();
    String assembleTaskName = selectedVariant.getMainArtifact().getAssembleTaskName();
    verifyContainsVariantName(assembleTaskName, variantName);
  }

  /**
   * Verify that bundle task contains variant name in the form expected by {@link ExportSignedPackageWizard#getGradleTasks} when flavors are
   * used.
   */
  public void testVariantNameInAssembleTaskWithFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    AndroidModuleModel androidModel = getModel();

    Variant selectedVariant = androidModel.getSelectedVariant();
    String variantName = selectedVariant.getName();
    String assembleTaskName = selectedVariant.getMainArtifact().getAssembleTaskName();
    verifyContainsVariantName(assembleTaskName, variantName);
  }

  /**
   * Verify that assemble task contains variant name in the form expected by {@link ExportSignedPackageWizard#getGradleTasks} when there are
   * no flavors.
   */
  public void testVariantNameInBundleTaskNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    AndroidModuleModel androidModel = getModel();

    Variant selectedVariant = androidModel.getSelectedVariant();
    String variantName = selectedVariant.getName();
    String bundleTaskName = selectedVariant.getMainArtifact().getBundleTaskName();
    if (bundleTaskName != null) {
      // Only test if bundle task exists
      verifyContainsVariantName(bundleTaskName, variantName);
    }
  }

  /**
   * Verify that bundle task contains variant name in the form expected by {@link ExportSignedPackageWizard#getGradleTasks} when flavors are
   * used.
   */
  public void testVariantNameInBundleTaskWithFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    AndroidModuleModel androidModel = getModel();

    Variant selectedVariant = androidModel.getSelectedVariant();
    String variantName = selectedVariant.getName();
    String bundleTaskName = selectedVariant.getMainArtifact().getBundleTaskName();
    if (bundleTaskName != null) {
      // Only test if bundle task exists
      verifyContainsVariantName(bundleTaskName, variantName);
    }
  }


  private static void verifyContainsVariantName(@NotNull String taskName, @NotNull String variantName) {
    boolean containsName = (taskName.indexOf(capitalize(variantName)) > 1) ||  // :prefixVariantName
                           (taskName.indexOf(decapitalize(variantName)) == 1); // :variantNameSuffix
    assertTrue("Variant name not found in task " + taskName, containsName);
  }
}
