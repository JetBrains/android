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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link PsAndroidModule}.
 */
public class PsAndroidModuleTest extends AndroidGradleTestCase {
  public void testProductFlavors() throws Throwable {
    loadProject("projects/projectWithAppandLib");

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsProductFlavor> productFlavors = getProductFlavors(appModule);
    assertThat(productFlavors).hasSize(2);

    PsProductFlavor basic = appModule.findProductFlavor("basic");
    assertNotNull(basic);
    assertTrue(basic.isDeclared());

    PsProductFlavor release = appModule.findProductFlavor("paid");
    assertNotNull(release);
    assertTrue(release.isDeclared());
  }

  @NotNull
  private static List<PsProductFlavor> getProductFlavors(@NotNull PsAndroidModule module) {
    List<PsProductFlavor> productFlavors = Lists.newArrayList();
    module.forEachProductFlavor(productFlavors::add);
    return productFlavors;
  }

  public void testVariants() throws Throwable {
    loadProject("projects/projectWithAppandLib");

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
    loadProject("projects/projectWithAppandLib");

    Project resolvedProject = myFixture.getProject();
    Module appModule = ModuleManager.getInstance(resolvedProject).findModuleByName("app");
    assertNotNull(appModule);

    // Make sure 'app' has an artifact dependency with version not including a '+'
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertNotNull(buildModel);
    for (ArtifactDependencyModel dependency : buildModel.dependencies().artifacts("compile")) {
      if ("com.android.support".equals(dependency.group().value()) && "appcompat-v7".equals(dependency.name().value())) {
        dependency.setVersion("23.1.1");
        break;
      }
    }

    runWriteCommandAction(resolvedProject, buildModel::applyChanges);

    //noinspection ConstantConditions
    importProject(resolvedProject, resolvedProject.getName(), new File(resolvedProject.getBasePath()), null);

    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule module = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(module);

    List<PsAndroidDependency> declaredDependencies = getDeclaredDependencies(module);
    assertThat(declaredDependencies).hasSize(1);

    // Verify that appcompat is considered a "editable" dependency, and it was matched properly
    PsLibraryAndroidDependency appCompatV7 = (PsLibraryAndroidDependency)declaredDependencies.get(0);
    assertTrue(appCompatV7.isDeclared());

    PsArtifactDependencySpec resolvedSpec = appCompatV7.getResolvedSpec();
    assertEquals("com.android.support", resolvedSpec.group);
    assertEquals("appcompat-v7", resolvedSpec.name);

    // Verify that the variants where appcompat is are properly registered.
    Collection<String> variants = appCompatV7.getVariants();
    assertThat(variants).containsExactly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

    for (String variant : variants) {
      assertNotNull(module.findVariant(variant));
    }
  }

  public void testEditableDependenciesWithPlusInVersion() throws Throwable {
    loadProject("projects/projectWithAppandLib");

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
    assertEquals("com.android.support", resolvedSpec.group);
    assertEquals("appcompat-v7", resolvedSpec.name);
    assertThat(resolvedSpec.version).isNotEqualTo("+");

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
    loadProject("projects/projectWithAppandLib");

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    PsAndroidModule libModule = (PsAndroidModule)project.findModuleByName("lib");
    assertNotNull(libModule);

    assertTrue(appModule.canDependOn(libModule));
    assertFalse(libModule.canDependOn(appModule));
  }
}
