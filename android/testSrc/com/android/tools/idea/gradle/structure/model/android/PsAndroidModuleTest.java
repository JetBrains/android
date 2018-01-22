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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.BASIC;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static java.util.stream.Collectors.toList;

/**
 * Tests for {@link PsAndroidModule}.
 */
public class PsAndroidModuleTest extends AndroidGradleTestCase {

  public void testFlavorDimensions() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<String> flavorDimensions = getFlavorDimensions(appModule);
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar").inOrder();
  }

  public void testAddFlavorDimension() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    appModule.addNewFlavorDimension("new");
    // A product flavor is required for successful sync.
    PsProductFlavor newInNew = appModule.addNewProductFlavor("new_in_new");
    newInNew.setDimension(new ParsedValue.Set.Parsed<String>("new", null));
    appModule.applyChanges();

    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    List<String> flavorDimensions = getFlavorDimensions(appModule);
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar", "new").inOrder();
  }

  @NotNull
  private static List<String> getFlavorDimensions(@NotNull PsAndroidModule module) {
    return Lists.newArrayList(module.getFlavorDimensions());
  }

  public void testProductFlavors() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsProductFlavor> productFlavors = getProductFlavors(appModule);
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid").inOrder();
    assertThat(productFlavors).hasSize(2);

    PsProductFlavor basic = appModule.findProductFlavor("basic");
    assertNotNull(basic);
    assertTrue(basic.isDeclared());

    PsProductFlavor release = appModule.findProductFlavor("paid");
    assertNotNull(release);
    assertTrue(release.isDeclared());
  }

  public void testAddProductFlavor() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsProductFlavor> productFlavors = getProductFlavors(appModule);
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid").inOrder();

    appModule.addNewProductFlavor("new_flavor");

    productFlavors = getProductFlavors(appModule);
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid", "new_flavor").inOrder();

    PsProductFlavor newFlavor = appModule.findProductFlavor("new_flavor");
    assertNotNull(newFlavor);
    assertNull(newFlavor.getResolvedModel());

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    productFlavors = getProductFlavors(appModule);
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid", "new_flavor").inOrder();

    newFlavor = appModule.findProductFlavor("new_flavor");
    assertNotNull(newFlavor);
    assertNotNull(newFlavor.getResolvedModel());
  }

  @NotNull
  private static List<PsProductFlavor> getProductFlavors(@NotNull PsAndroidModule module) {
    List<PsProductFlavor> productFlavors = Lists.newArrayList();
    module.forEachProductFlavor(productFlavors::add);
    return productFlavors;
  }

  public void testBuildTypes() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsBuildType> buildTypes = getBuildTypes(appModule);
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug").inOrder();
    assertThat(buildTypes).hasSize(2);

    PsBuildType release = appModule.findBuildType("release");
    assertNotNull(release);
    assertTrue(release.isDeclared());

    PsBuildType debug = appModule.findBuildType("debug");
    assertNotNull(debug);
    assertTrue(!debug.isDeclared());
  }

  public void testAddBuildType() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsBuildType> buildTypes = getBuildTypes(appModule);
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug").inOrder();

    appModule.addNewBuildType("new_build_type");

    buildTypes = getBuildTypes(appModule);
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug", "new_build_type").inOrder();

    PsBuildType newBuildType = appModule.findBuildType("new_build_type");
    assertNotNull(newBuildType);
    assertNull(newBuildType.getResolvedModel());

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    buildTypes = getBuildTypes(appModule);
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "new_build_type", "debug").inOrder();  // "debug" is not declared and goes last.

    newBuildType = appModule.findBuildType("new_build_type");
    assertNotNull(newBuildType);
    assertNotNull(newBuildType.getResolvedModel());
  }

  @NotNull
  private static List<PsBuildType> getBuildTypes(@NotNull PsAndroidModule module) {
    List<PsBuildType> buildTypes = Lists.newArrayList();
    module.forEachBuildType(buildTypes::add);
    return buildTypes;
  }

  public void testVariants() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    Collection<PsVariant> variants = getVariants(appModule);
    assertThat(variants).hasSize(4);

    PsVariant paidDebug = appModule.findVariant("paidDebug");
    assertNotNull(paidDebug);
    List<String> flavors = paidDebug.getProductFlavors();
    assertThat(flavors).containsExactly("paid");

    PsVariant paidRelease = appModule.findVariant("paidRelease");
    assertNotNull(paidRelease);
    flavors = paidRelease.getProductFlavors();
    assertThat(flavors).containsExactly("paid");

    PsVariant basicDebug = appModule.findVariant("basicDebug");
    assertNotNull(basicDebug);
    flavors = basicDebug.getProductFlavors();
    assertThat(flavors).containsExactly("basic");

    PsVariant basicRelease = appModule.findVariant("basicRelease");
    assertNotNull(basicRelease);
    flavors = basicRelease.getProductFlavors();
    assertThat(flavors).containsExactly("basic");
  }

  @NotNull
  private static List<PsVariant> getVariants(@NotNull PsAndroidModule module) {
    List<PsVariant> variants = Lists.newArrayList();
    module.forEachVariant(variants::add);
    return variants;
  }

  public void testEditableDependencies() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    Module appModule = ModuleManager.getInstance(resolvedProject).findModuleByName("app");
    assertNotNull(appModule);

    // Make sure 'app' has an artifact dependency with version not including a '+'
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertNotNull(buildModel);
    for (ArtifactDependencyModel dependency : buildModel.dependencies().artifacts("compile")) {
      if ("com.android.support".equals(dependency.group().value()) && "appcompat-v7".equals(dependency.name().value())) {
        dependency.setVersion("+");
        break;
      }
    }

    runWriteCommandAction(resolvedProject, buildModel::applyChanges);

    //noinspection ConstantConditions
    importProject(resolvedProject.getName(), new File(resolvedProject.getBasePath()), null);

    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule module = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(module);

    List<PsAndroidDependency> declaredDependencies = getDeclaredDependencies(module);
    assertThat(declaredDependencies).hasSize(1);

    // Verify that appcompat is considered a "editable" dependency, and it was matched properly
    PsLibraryAndroidDependency appCompatV7 = (PsLibraryAndroidDependency)declaredDependencies.get(0);
    assertTrue(appCompatV7.isDeclared());

    PsArtifactDependencySpec resolvedSpec = appCompatV7.getResolvedSpec();
    assertEquals("com.android.support", resolvedSpec.getGroup());
    assertEquals("appcompat-v7", resolvedSpec.getName());

    // Verify that the variants where appcompat is are properly registered.
    Collection<String> variants = appCompatV7.getVariants();
    assertThat(variants).containsExactly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

    for (String variant : variants) {
      assertNotNull(module.findVariant(variant));
    }
  }

  public void testEditableDependenciesWithPlusInVersion() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsAndroidDependency> declaredDependencies = getDeclaredDependencies(appModule);
    assertThat(declaredDependencies).hasSize(1);

    // Verify that appcompat is considered a "editable" dependency, and it was matched properly
    PsLibraryAndroidDependency appCompatV7 = (PsLibraryAndroidDependency)declaredDependencies.get(0);
    assertTrue(appCompatV7.isDeclared());

    PsArtifactDependencySpec declaredSpec = appCompatV7.getDeclaredSpec();
    assertNotNull(declaredSpec);
    assertEquals("com.android.support:appcompat-v7:+", declaredSpec.toString());

    PsArtifactDependencySpec resolvedSpec = appCompatV7.getResolvedSpec();
    assertEquals("com.android.support", resolvedSpec.getGroup());
    assertEquals("appcompat-v7", resolvedSpec.getName());
    assertThat(resolvedSpec.getVersion()).isNotEqualTo("+");

    // Verify that the variants where appcompat is are properly registered.
    Collection<String> variants = appCompatV7.getVariants();
    assertThat(variants).containsExactly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

    for (String variant : variants) {
      assertNotNull(appModule.findVariant(variant));
    }
  }

  @NotNull
  private static List<PsAndroidDependency> getDeclaredDependencies(@NotNull PsAndroidModule module) {
    List<PsAndroidDependency> dependencies = Lists.newArrayList();
    module.forEachDeclaredDependency(dependencies::add);
    return dependencies;
  }

  public void testCanDependOnModules() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    PsAndroidModule libModule = (PsAndroidModule)project.findModuleByName("lib");
    assertNotNull(libModule);

    assertTrue(appModule.canDependOn(libModule));
    assertFalse(libModule.canDependOn(appModule));
  }

  public void testSigningConfigs() throws Throwable {
    loadProject(BASIC);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByGradlePath(":");
    assertNotNull(appModule);

    List<PsSigningConfig> signingConfigs = getSigningConfigs(appModule);
    assertThat(signingConfigs).hasSize(2);

    PsSigningConfig myConfig = appModule.findSigningConfig("myConfig");
    assertNotNull(myConfig);
    assertTrue(myConfig.isDeclared());

    PsSigningConfig debugConfig = appModule.findSigningConfig("debug");
    assertNotNull(debugConfig);
    assertTrue(!debugConfig.isDeclared());
  }

  public void testAddSigningConfig() throws Throwable {
    loadProject(BASIC);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByGradlePath(":");
    assertNotNull(appModule);

    List<PsSigningConfig> signingConfigs = getSigningConfigs(appModule);
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "debug").inOrder();

    PsSigningConfig myConfig = appModule.addNewSigningConfig("config2");
    myConfig.setStoreFile(new ParsedValue.Set.Parsed<File>(new File("/tmp/1"), null));

    assertNotNull(myConfig);
    assertTrue(myConfig.isDeclared());

    signingConfigs = getSigningConfigs(appModule);
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "debug", "config2").inOrder();

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByGradlePath(":");

    signingConfigs = getSigningConfigs(appModule);
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "config2", "debug").inOrder();
  }

  @NotNull
  private static List<PsSigningConfig> getSigningConfigs(@NotNull PsAndroidModule module) {
    List<PsSigningConfig> signingConfigs = Lists.newArrayList();
    module.forEachSigningConfig(signingConfigs::add);
    return signingConfigs;
  }
}
