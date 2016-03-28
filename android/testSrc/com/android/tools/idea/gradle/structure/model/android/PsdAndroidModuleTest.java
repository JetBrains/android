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
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link PsAndroidModule}.
 */
public class PsdAndroidModuleTest extends AndroidGradleTestCase {
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
    final List<PsProductFlavor> productFlavors = Lists.newArrayList();
    module.forEachProductFlavor(new Predicate<PsProductFlavor>() {
      @Override
      public boolean apply(@Nullable PsProductFlavor productFlavor) {
        productFlavors.add(productFlavor);
        return true;
      }
    });
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
    assertThat(flavors).containsOnly("paid");

    PsVariant paidRelease = appModule.findVariant("paidRelease");
    assertNotNull(paidRelease);
    flavors = paidRelease.getProductFlavors();
    assertThat(flavors).containsOnly("paid");

    PsVariant basicDebug = appModule.findVariant("basicDebug");
    assertNotNull(basicDebug);
    flavors = basicDebug.getProductFlavors();
    assertThat(flavors).containsOnly("basic");

    PsVariant basicRelease = appModule.findVariant("basicRelease");
    assertNotNull(basicRelease);
    flavors = basicRelease.getProductFlavors();
    assertThat(flavors).containsOnly("basic");
  }

  @NotNull
  private static List<PsVariant> getVariants(@NotNull PsAndroidModule module) {
    final List<PsVariant> variants = Lists.newArrayList();
    module.forEachVariant(new Predicate<PsVariant>() {
      @Override
      public boolean apply(@Nullable PsVariant variant) {
        variants.add(variant);
        return true;
      }
    });
    return variants;
  }

  public void testEditableDependencies() throws Throwable {
    loadProject("projects/projectWithAppandLib");

    Project resolvedProject = myFixture.getProject();
    Module appModule = ModuleManager.getInstance(resolvedProject).findModuleByName("app");
    assertNotNull(appModule);

    // Make sure 'app' has an artifact dependency with version not including a '+'
    final GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertNotNull(buildModel);
    for (ArtifactDependencyModel dependency : buildModel.dependencies().artifacts("compile")) {
      if ("com.android.support".equals(dependency.group().value()) && "appcompat-v7".equals(dependency.name().value())) {
        dependency.setVersion("23.1.1");
        break;
      }
    }

    runWriteCommandAction(resolvedProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    //noinspection ConstantConditions
    importProject(resolvedProject, resolvedProject.getName(), new File(resolvedProject.getBasePath()), null);

    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule module = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(module);

    List<PsAndroidDependency> declaredDependencies = getDeclaredDependencies(module);
    assertThat(declaredDependencies).hasSize(1);

    // Verify that appcompat is considered a "editable" dependency, and it was matched properly
    PsLibraryDependency appCompatV7 = (PsLibraryDependency)declaredDependencies.get(0);
    assertTrue(appCompatV7.isDeclared());

    PsArtifactDependencySpec resolvedSpec = appCompatV7.getResolvedSpec();
    assertEquals("com.android.support", resolvedSpec.group);
    assertEquals("appcompat-v7", resolvedSpec.name);

    // Verify that the variants where appcompat is are properly registered.
    Set<String> variants = appCompatV7.getVariants();
    assertThat(variants).containsOnly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

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
    PsLibraryDependency appCompatV7 = (PsLibraryDependency)declaredDependencies.get(0);
    assertTrue(appCompatV7.isDeclared());

    PsArtifactDependencySpec declaredSpec = appCompatV7.getDeclaredSpec();
    assertNotNull(declaredSpec);
    assertEquals("com.android.support:appcompat-v7:+", declaredSpec.toString());

    PsArtifactDependencySpec resolvedSpec = appCompatV7.getResolvedSpec();
    assertEquals("com.android.support", resolvedSpec.group);
    assertEquals("appcompat-v7", resolvedSpec.name);
    assertThat(resolvedSpec.version).isNotEqualTo("+");

    // Verify that the variants where appcompat is are properly registered.
    Set<String> variants = appCompatV7.getVariants();
    assertThat(variants).containsOnly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

    for (String variant : variants) {
      assertNotNull(appModule.findVariant(variant));
    }
  }

  @NotNull
  private static List<PsAndroidDependency> getDeclaredDependencies(@NotNull PsAndroidModule module) {
    final List<PsAndroidDependency> dependencies = Lists.newArrayList();
    module.forEachDeclaredDependency(new Predicate<PsAndroidDependency>() {
      @Override
      public boolean apply(@Nullable PsAndroidDependency dependency) {
        dependencies.add(dependency);
        return true;
      }
    });
    return dependencies;
  }
}
