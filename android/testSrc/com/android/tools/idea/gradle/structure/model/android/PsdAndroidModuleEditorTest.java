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
import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link PsdAndroidModuleModel}.
 */
public class PsdAndroidModuleEditorTest extends AndroidGradleTestCase {
  public void testProductFlavors() throws Throwable {
    loadProject("projects/projectWithAppandLib");

    Project project = myFixture.getProject();
    PsdProjectModel projectEditor = new PsdProjectModel(project);

    PsdAndroidModuleModel appModuleEditor = (PsdAndroidModuleModel)projectEditor.findModelForModule("app");
    assertNotNull(appModuleEditor);

    List<PsdProductFlavorModel> flavorEditors = appModuleEditor.getProductFlavors();
    assertThat(flavorEditors).hasSize(2);

    PsdProductFlavorModel basic = appModuleEditor.findProductFlavorModel("basic");
    assertNotNull(basic);
    assertTrue(basic.isEditable());

    PsdProductFlavorModel release = appModuleEditor.findProductFlavorModel("paid");
    assertNotNull(release);
    assertTrue(release.isEditable());
  }

  public void testVariants() throws Throwable {
    loadProject("projects/projectWithAppandLib");

    Project project = myFixture.getProject();
    PsdProjectModel projectEditor = new PsdProjectModel(project);

    PsdAndroidModuleModel appModuleEditor = (PsdAndroidModuleModel)projectEditor.findModelForModule("app");
    assertNotNull(appModuleEditor);

    Collection<PsdVariantModel> variantEditors = appModuleEditor.getVariants();
    assertThat(variantEditors).hasSize(4);

    PsdVariantModel paidDebug = appModuleEditor.findVariantModel("paidDebug");
    assertNotNull(paidDebug);
    List<String> flavors = paidDebug.getProductFlavors();
    assertThat(flavors).containsOnly("paid");

    PsdVariantModel paidRelease = appModuleEditor.findVariantModel("paidRelease");
    assertNotNull(paidRelease);
    flavors = paidRelease.getProductFlavors();
    assertThat(flavors).containsOnly("paid");

    PsdVariantModel basicDebug = appModuleEditor.findVariantModel("basicDebug");
    assertNotNull(basicDebug);
    flavors = basicDebug.getProductFlavors();
    assertThat(flavors).containsOnly("basic");

    PsdVariantModel basicRelease = appModuleEditor.findVariantModel("basicRelease");
    assertNotNull(basicRelease);
    flavors = basicRelease.getProductFlavors();
    assertThat(flavors).containsOnly("basic");
  }

  public void testEditableDependencies() throws Throwable {
    loadProject("projects/projectWithAppandLib");

    Project project = myFixture.getProject();
    Module appModule = ModuleManager.getInstance(project).findModuleByName("app");
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

    runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    //noinspection ConstantConditions
    importProject(project, project.getName(), new File(project.getBasePath()), null);

    PsdProjectModel projectEditor = new PsdProjectModel(project);

    PsdAndroidModuleModel appModuleEditor = (PsdAndroidModuleModel)projectEditor.findModelForModule("app");
    assertNotNull(appModuleEditor);

    List<PsdAndroidDependencyModel> declaredDependencies = appModuleEditor.getDeclaredDependencies();
    assertThat(declaredDependencies).hasSize(1);

    // Verify that appcompat is considered a "editable" dependency, and it was matched properly
    PsdLibraryDependencyModel appCompatV7Editor = (PsdLibraryDependencyModel)declaredDependencies.get(0);
    assertTrue(appCompatV7Editor.isEditable());
    assertEquals("com.android.support:appcompat-v7:23.1.1", appCompatV7Editor.getResolvedSpec().toString());

    // Verify that the variants where appcompat is are properly registered.
    Set<String> variants = appCompatV7Editor.getVariants();
    assertThat(variants).containsOnly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

    // Verify that the variants where appcompat is have editors
    for (String variant : variants) {
      assertNotNull(appModuleEditor.findVariantModel(variant));
    }
  }

  public void testEditableDependenciesWithPlusInVersion() throws Throwable {
    loadProject("projects/projectWithAppandLib");

    Project project = myFixture.getProject();
    PsdProjectModel projectEditor = new PsdProjectModel(project);

    PsdAndroidModuleModel appModuleEditor = (PsdAndroidModuleModel)projectEditor.findModelForModule("app");
    assertNotNull(appModuleEditor);

    List<PsdAndroidDependencyModel> declaredDependencies = appModuleEditor.getDeclaredDependencies();
    assertThat(declaredDependencies).hasSize(1);

    // Verify that appcompat is considered a "editable" dependency, and it was matched properly
    PsdLibraryDependencyModel appCompatV7Editor = (PsdLibraryDependencyModel)declaredDependencies.get(0);
    assertTrue(appCompatV7Editor.isEditable());
    PsdArtifactDependencySpec declaredSpec = appCompatV7Editor.getDeclaredSpec();
    assertNotNull(declaredSpec);
    assertEquals("com.android.support:appcompat-v7:+", declaredSpec.toString());
    assertEquals("com.android.support:appcompat-v7:23.1.1", appCompatV7Editor.getResolvedSpec().toString());

    // Verify that the variants where appcompat is are properly registered.
    Set<String> variants = appCompatV7Editor.getVariants();
    assertThat(variants).containsOnly("paidDebug", "paidRelease", "basicDebug", "basicRelease");

    // Verify that the variants where appcompat is have editors
    for (String variant : variants) {
      assertNotNull(appModuleEditor.findVariantModel(variant));
    }
  }
}
