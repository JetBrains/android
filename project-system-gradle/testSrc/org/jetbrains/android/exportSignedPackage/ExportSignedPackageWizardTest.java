/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage;

import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_MULTIFLAVOR;
import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_NO_FLAVORS;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.getTaskNamesFromSelectedVariant;

import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class ExportSignedPackageWizardTest extends AndroidGradleTestCase {
  private final char[] testKeystorePassword = "android".toCharArray();
  private final String testKeyAlias = "androiddebugkey";
  private final char[] testKeyPassword = "android".toCharArray();
  private final Consumer<ListenableFuture<AssembleInvocationResult>> successHandler = future -> {
    try {
      AssembleInvocationResult result = future.get();
      if (!result.isBuildSuccessful()) {
        fail(getInvocationErrorsCause(result));
      }
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  };
  private final Consumer<ListenableFuture<AssembleInvocationResult>> badStorePassHandler = future -> {
    try {
      AssembleInvocationResult result = future.get();
      if (result.isBuildSuccessful()) {
        fail("Build was successful even with a bad store password");
      }
      String buildErrorCause = getInvocationErrorsCause(result);
      assertTrue("Build failed but without the expected cause: " + buildErrorCause,
                 buildErrorCause.contains("Keystore was tampered with, or password was incorrect"));
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  };
  private final Consumer<ListenableFuture<AssembleInvocationResult>> badKeyAliasHandler = future -> {
    try {
      AssembleInvocationResult result = future.get();
      if (result.isBuildSuccessful()) {
        fail("Build was successful even with a bad key alias");
      }
      String buildErrorCause = getInvocationErrorsCause(result);
      assertTrue("Build failed but without the expected cause: " + buildErrorCause,
                 buildErrorCause.contains("No key with alias 'badKeyAlias' found in keystore"));
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  };
  private final Consumer<ListenableFuture<AssembleInvocationResult>> badKeyPassHandler = future -> {
    try {
      AssembleInvocationResult result = future.get();
      if (result.isBuildSuccessful()) {
        fail("Build was successful even with a bad key alias");
      }
      String buildErrorCause = getInvocationErrorsCause(result);
      assertTrue("Build failed but without the expected cause: " + buildErrorCause,
                 buildErrorCause.contains("Failed to read key androiddebugkey from store"));
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  };

  public void testNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getBasicVariants().size());

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
    assertEquals(8, androidProject.getBasicVariants().size());

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
    assertEquals(2, androidProject.getBasicVariants().size());

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
    assertEquals(8, androidProject.getBasicVariants().size());

    Set<String> assembleTasks = Sets.newHashSet(ExportSignedPackageWizard
                                                  .getGradleTasks("", getModel(), asList("proX86Release", "freeArmRelease"),
                                                                  ExportSignedPackageWizard.BUNDLE));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":bundleProX86Release"));
    assertTrue(assembleTasks.contains(":bundleFreeArmRelease"));
  }

  public void testGetTaskNamesFromSelectedVariantWithNoFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(singletonList("release"), "debug", ":assembleDebug");
    assertEquals(1, tasks.size());
    assertEquals(":assembleRelease", tasks.get(0));
  }

  public void testGetTaskNamesFromSelectedVariantWithFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(asList("proX86Release", "freeArmRelease"), "proX86Debug", ":assembleProX86Debug");
    assertEquals(2, tasks.size());
    assertTrue(tasks.contains(":assembleProX86Release"));
    assertTrue(tasks.contains(":assembleFreeArmRelease"));
  }

  public void testGetTaskNamesFromSelectedVariantWithBundleNoFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(singletonList("release"), "debug", ":bundleDebug");
    assertEquals(1, tasks.size());
    assertEquals(":bundleRelease", tasks.get(0));
  }

  public void testGetTaskNamesFromSelectedVariantWithBundleFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(asList("proX86Release", "freeArmRelease"), "proX86Debug", ":bundleProX86Debug");
    assertEquals(2, tasks.size());
    assertTrue(tasks.contains(":bundleProX86Release"));
    assertTrue(tasks.contains(":bundleFreeArmRelease"));
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

  public void testBuildModeFromApkTargetType() {
    assertEquals(BuildMode.ASSEMBLE, ExportSignedPackageWizard.getBuildModeFromTarget(ExportSignedPackageWizard.APK));
  }

  public void testBuildModeFromBundleTargetType() {
    assertEquals(BuildMode.BUNDLE, ExportSignedPackageWizard.getBuildModeFromTarget(ExportSignedPackageWizard.BUNDLE));
  }

  /**
   * Perform a sign for a bundle and confirm no errors were caused.
   */
  public void testSignBundle() throws Exception {
    verifySigning(ExportSignedPackageWizard.BUNDLE, testKeystorePassword, testKeyAlias, testKeyPassword, successHandler);
  }

  /**
   * Perform a sign for a bundle and confirm no errors were caused.
   */
  public void testSignApk() throws Exception {
    verifySigning(ExportSignedPackageWizard.APK, testKeystorePassword, testKeyAlias, testKeyPassword, successHandler);
  }

  /**
   * Sign bundle with bad store password should fail
   */
  public void testSignBundleBadStorePass() throws Exception {
    verifySigning(ExportSignedPackageWizard.BUNDLE, "badStorePass".toCharArray(), testKeyAlias, testKeyPassword, badStorePassHandler);
  }

  /**
   * Sign apk with bad store password should fail
   */
  public void testSignApkBadStorePass() throws Exception {
    verifySigning(ExportSignedPackageWizard.APK, "badStorePass".toCharArray(), testKeyAlias, testKeyPassword, badStorePassHandler);
  }

  /**
   * Sign bundle with bad key alias should fail
   */
  public void testSignBundleBadKeyAlias() throws Exception {
    verifySigning(ExportSignedPackageWizard.BUNDLE, testKeystorePassword, "badKeyAlias", testKeyPassword, badKeyAliasHandler);
  }

  /**
   * Sign apk with bad key alias should fail
   */
  public void testSignApkBadKeyAlias() throws Exception {
    verifySigning(ExportSignedPackageWizard.APK, testKeystorePassword, "badKeyAlias", testKeyPassword, badKeyAliasHandler);
  }

  /**
   * Sign bundle with bad key password should fail
   */
  public void testSignBundleBadKeyPass() throws Exception {
    verifySigning(ExportSignedPackageWizard.BUNDLE, testKeystorePassword, testKeyAlias, "badKeyPass".toCharArray(), badKeyPassHandler);
  }

  /**
   * Sign apk with bad key password should fail
   */
  public void testSignApkBadKeyPass() throws Exception {
    verifySigning(ExportSignedPackageWizard.APK, testKeystorePassword, testKeyAlias, "badKeyPass".toCharArray(), badKeyPassHandler);
  }

  private void verifySigning(@NotNull String targetType,
                             char[] storePass,
                             String keyAlias,
                             char[] keyPass,
                             Consumer<ListenableFuture<AssembleInvocationResult>> buildResultHandler) throws Exception {
    loadProject(SIMPLE_APPLICATION);
    Project project = getProject();
    List<AndroidFacet> facets =
      ProjectSystemUtil.getAndroidFacets(project).stream().filter(facet -> facet.getConfiguration().isAppProject()).toList();
    assertFalse(facets.isEmpty());
    AndroidFacet facet = facets.get(0);
    GradleAndroidModel androidModel = GradleAndroidModel.get(facet);
    assertNotNull(androidModel);
    List<String> variants = singletonList("release");
    String keyStorePath = getTestDataPath() + File.separator + "signingKey" + File.separator + "debug.keystore";
    GradleSigningInfo signingInfo = new GradleSigningInfo(keyStorePath, storePass, keyAlias, keyPass);
    String apkPath = androidModel.getRootDirPath().getPath();
    List<Module> modules = ImmutableList.of(facet.getMainModule());
    ExportSignedPackageWizard.doBuildAndSignGradleProject(getProject(), facet, variants, modules, signingInfo, apkPath, targetType,
                                                          buildResultHandler);
  }

  private String getInvocationErrorsCause(AssembleInvocationResult result) {
    StringBuilder errorMessages = new StringBuilder("The build was not successful\n");
    for (GradleInvocationResult invocation : result.getInvocationResult().getInvocations()) {
      Throwable buildError = invocation.getBuildError();
      String prefix = "";
      while (buildError != null) {
        prefix += "  ";
        errorMessages.append(prefix).append(buildError.getMessage()).append("\n");
        Throwable cause = buildError.getCause();
        if (cause == buildError) {
          break;
        }
        buildError = cause;
      }
    }
    return errorMessages.toString();
  }
}
