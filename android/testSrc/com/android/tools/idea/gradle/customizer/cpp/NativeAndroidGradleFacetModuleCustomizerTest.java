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
package com.android.tools.idea.gradle.customizer.cpp;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.stubs.android.NativeAndroidProjectStub;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * Tests for {@link NativeAndroidGradleFacetModuleCustomizer}.
 */
public class NativeAndroidGradleFacetModuleCustomizerTest extends IdeaTestCase {
  private NativeAndroidProjectStub myNativeAndroidProject;
  private NativeAndroidGradleFacetModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDir = getBaseDirPath(myProject);
    myNativeAndroidProject = TestProjects.createNativeProject(rootDir);
    myCustomizer = new NativeAndroidGradleFacetModuleCustomizer();
  }

  public void testCustomizeModule() {
    File rootDir = myNativeAndroidProject.getRootDir();
    NativeAndroidGradleModel model = new NativeAndroidGradleModel(SYSTEM_ID, myNativeAndroidProject.getName(), rootDir, myNativeAndroidProject);
    final IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    try {
      myCustomizer.customizeModule(myProject, myModule, modelsProvider, model);
      ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      rethrowAllAsUnchecked(t);
    }

    // Verify that NativeAndroidGradleFacet was added and configured.
    NativeAndroidGradleFacet facet = NativeAndroidGradleFacet.getInstance(myModule);
    assertNotNull(facet);
    assertSame(model, facet.getNativeAndroidGradleModel());
  }
}
