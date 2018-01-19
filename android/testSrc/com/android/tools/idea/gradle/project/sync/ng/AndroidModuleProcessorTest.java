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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.ng.AndroidModuleProcessor.MODULE_GRADLE_MODELS_KEY;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModuleProcessor}.
 */
public class AndroidModuleProcessorTest extends IdeaTestCase {
  @Mock private GradleSyncState mySyncState;
  @Mock private AndroidModuleValidator.Factory myModuleValidatorFactory;
  @Mock private GradleModuleModels myAppModels;
  @Mock private GradleModuleModels myLibModels;
  @Mock private AndroidModuleModel myAppAndroidModel;
  @Mock private AndroidModuleModel myLibAndroidModel;

  private Module myAppModule;
  private Module myLibModule;
  private AndroidModuleValidatorStub myModuleValidator;

  private AndroidModuleProcessor myModuleProcessor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();

    myModuleValidator = new AndroidModuleValidatorStub();
    when(myModuleValidatorFactory.create(project)).thenReturn(myModuleValidator);

    myAppModule = createAndroidModule("app", myAppModels, myAppAndroidModel);
    myLibModule = createAndroidModule("lib", myLibModels, myLibAndroidModel);
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    myModuleProcessor = new AndroidModuleProcessor(project, modelsProvider, myModuleValidatorFactory);
  }

  @NotNull
  private Module createAndroidModule(@NotNull String moduleName,
                                     @NotNull GradleModuleModels moduleModels,
                                     @NotNull AndroidModuleModel androidModel) {
    Module module = createModule(moduleName);
    module.putUserData(MODULE_GRADLE_MODELS_KEY, moduleModels);

    AndroidFacet facet = createAndAddAndroidFacet(module);
    facet.getConfiguration().setModel(androidModel);

    return module;
  }

  public void testProcessAndroidModels() {
    // sync skipped.
    when(mySyncState.isSyncSkipped()).thenReturn(true);

    myModuleProcessor.processAndroidModels(Arrays.asList(myAppModule, myLibModule));

    myModuleValidator.assertModuleWasValidated(myAppModule, myAppAndroidModel);
    myModuleValidator.assertModuleWasValidated(myLibModule, myLibAndroidModel);

    myModuleValidator.assertFoundIssuesWereFixedAndReported(myAppModule, myLibModule);
  }

  private static class AndroidModuleValidatorStub extends AndroidModuleValidator {
    @NotNull private final Map<String, Pair<Module, AndroidModuleModel>> myValidatedModules = new LinkedHashMap<>();
    private boolean myFoundIssuesFixedAndReported;

    @Override
    public void validate(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
      myValidatedModules.put(module.getName(), Pair.create(module, androidModel));
    }

    void assertModuleWasValidated(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
      Pair<Module, AndroidModuleModel> data = findValidatedModule(module);
      assertSame(module, data.getFirst());
      assertSame(androidModel, data.getSecond());
    }

    @Override
    public void fixAndReportFoundIssues() {
      myFoundIssuesFixedAndReported = true;
    }

    void assertFoundIssuesWereFixedAndReported(@NotNull Module... expectedValidatedModules) {
      assertTrue(myFoundIssuesFixedAndReported);

      // With this we ensure that 'fixAndReportFoundIssues' is invoked after all modules have been validated.
      for (Module module : expectedValidatedModules) {
        Pair<Module, AndroidModuleModel> data = findValidatedModule(module);
        assertSame(module, data.getFirst());
      }
    }

    @NotNull
    private Pair<Module, AndroidModuleModel> findValidatedModule(@NotNull Module module) {
      Pair<Module, AndroidModuleModel> data = myValidatedModules.get(module.getName());
      return data != null ? data : Pair.empty();
    }
  }
}