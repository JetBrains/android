/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.intellij.pom.java.LanguageLevel.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JavaLanguageLevelModuleSetupStep}.
 */
public class JavaLanguageLevelModuleSetupStepTest extends IdeaTestCase {
  @Mock private JavaModuleModel myJavaModuleModel;

  private JavaLanguageLevelModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    mySetupStep = new JavaLanguageLevelModuleSetupStep();

    // Set the module's Java language level to 1.5 to check it changed.
    Module module = getModule();
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    LanguageLevelModuleExtensionImpl moduleExtension = modifiableModel.getModuleExtension(LanguageLevelModuleExtensionImpl.class);
    moduleExtension.setLanguageLevel(JDK_1_5);
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }

  public void testSetUpModuleWithLanguageLevelInJavaProject() {
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    when(myJavaModuleModel.getJavaLanguageLevel()).thenReturn(JDK_1_7);

    Module module = getModule();
    ModuleSetupContext context = new ModuleSetupContext.Factory().create(module, modelsProvider);
    mySetupStep.setUpModule(context, myJavaModuleModel);

    // Commit changes to verify results.
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    verifyLanguageLevel(module, JDK_1_7);
  }

  public void testSetUpModuleWithLanguageLevelComingFromAndroidModules() {
    createAndroidModule("app", JDK_1_7);
    createAndroidModule("androidLib", JDK_1_8);

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    when(myJavaModuleModel.getJavaLanguageLevel()).thenReturn(null);

    Module module = getModule();
    ModuleSetupContext context = new ModuleSetupContext.Factory().create(module, modelsProvider);
    mySetupStep.setUpModule(context, myJavaModuleModel);

    // Commit changes to verify results.
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    verifyLanguageLevel(module, JDK_1_7);
  }

  private void createAndroidModule(@NotNull String name, @NotNull LanguageLevel languageLevel) {
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    when(androidModel.getJavaLanguageLevel()).thenReturn(languageLevel);

    Module module = createModule(name);
    AndroidFacet facet = createAndAddAndroidFacet(module);
    facet.setAndroidModel(androidModel);
  }

  public void testSetUpModuleWithNoLanguageLevel() {
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    when(myJavaModuleModel.getJavaLanguageLevel()).thenReturn(null);

    Module module = getModule();
    ModuleSetupContext context = new ModuleSetupContext.Factory().create(module, modelsProvider);
    mySetupStep.setUpModule(context, myJavaModuleModel);

    // Commit changes to verify results.
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    verifyLanguageLevel(module, JDK_1_6);
  }

  private static void verifyLanguageLevel(@NotNull Module module, @NotNull LanguageLevel expected) {
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    LanguageLevelModuleExtensionImpl moduleExtension = modifiableModel.getModuleExtension(LanguageLevelModuleExtensionImpl.class);
    try {
      assertEquals(expected, moduleExtension.getLanguageLevel());
    }
    finally {
      modifiableModel.dispose();
    }
  }
}