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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PostBuildModel}.
 */
public class PostBuildModelTest extends IdeaTestCase {
  @Mock private ProjectBuildOutput myAppOutput;
  @Mock private ProjectBuildOutput myLibOutput;
  @Mock private InstantAppProjectBuildOutput myInstantAppOutput;

  private Module myLibModule;
  private Module myJavaLibModule;
  private Module myInstantAppModule;

  private PostBuildModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    GradleFacet facet = createAndAddGradleFacet(myModule);
    facet.getConfiguration().GRADLE_PROJECT_PATH = ":app";

    myLibModule = createModule("lib");
    facet = createAndAddGradleFacet(myLibModule);
    facet.getConfiguration().GRADLE_PROJECT_PATH = ":lib";

    myInstantAppModule = createModule("instantapp");
    facet = createAndAddGradleFacet(myInstantAppModule);
    facet.getConfiguration().GRADLE_PROJECT_PATH = ":instantapp";

    OutputBuildAction.PostBuildProjectModels outputs = new PostBuildProjectModelsBuilder().
      setModelForModule(":app", ProjectBuildOutput.class, myAppOutput).
      setModelForModule(":lib", ProjectBuildOutput.class, myLibOutput).
      setModelForModule(":instantapp", InstantAppProjectBuildOutput.class, myInstantAppOutput).
      build();

    myJavaLibModule = createModule("javaLib");

    myModel = new PostBuildModel(outputs);
  }

  public void testFindOutputModelForGradlePath() {
    assertSame(myAppOutput, myModel.findProjectBuildOutput(":app"));
    assertNull(myModel.findInstantAppProjectBuildOutput(":app"));
    assertSame(myLibOutput, myModel.findProjectBuildOutput(":lib"));
    assertNull(myModel.findInstantAppProjectBuildOutput(":lib"));
    assertNull(myModel.findProjectBuildOutput(":instantapp"));
    assertSame(myInstantAppOutput, myModel.findInstantAppProjectBuildOutput(":instantapp"));
    assertNull(myModel.findProjectBuildOutput(":javaLib"));
  }

  public void testFindOutputModelForModule() {
    assertSame(myAppOutput, myModel.findProjectBuildOutput(myModule));
    assertNull(myModel.findInstantAppProjectBuildOutput(myModule));
    assertSame(myLibOutput, myModel.findProjectBuildOutput(myLibModule));
    assertNull(myModel.findInstantAppProjectBuildOutput(myLibModule));
    assertNull(myModel.findProjectBuildOutput(myInstantAppModule));
    assertSame(myInstantAppOutput, myModel.findInstantAppProjectBuildOutput(myInstantAppModule));
    assertNull(myModel.findProjectBuildOutput(myJavaLibModule));
  }

  public void testFindOutputModelForFacet() {
    AndroidFacet androidFacet = createAndAddAndroidFacet(myModule);
    assertSame(myAppOutput, myModel.findProjectBuildOutput(androidFacet));
    assertNull(myModel.findInstantAppProjectBuildOutput(androidFacet));

    androidFacet = createAndAddAndroidFacet(myLibModule);
    assertSame(myLibOutput, myModel.findProjectBuildOutput(androidFacet));
    assertNull(myModel.findInstantAppProjectBuildOutput(androidFacet));

    androidFacet = createAndAddAndroidFacet(myInstantAppModule);
    assertNull(myModel.findProjectBuildOutput(androidFacet));
    assertSame(myInstantAppOutput, myModel.findInstantAppProjectBuildOutput(androidFacet));
  }

  private static class PostBuildProjectModelsBuilder {
    @NotNull private final Map<String, OutputBuildAction.PostBuildModuleModels> myModels = new HashMap<>();

    @NotNull
    private <T> PostBuildProjectModelsBuilder setModelForModule(@NotNull String gradlePath, @NotNull Class<T> modelType, @NotNull T model) {
      if (!myModels.containsKey(gradlePath)) {
        myModels.put(gradlePath, mock(OutputBuildAction.PostBuildModuleModels.class));
      }

      OutputBuildAction.PostBuildModuleModels moduleModels = myModels.get(gradlePath);
      when(moduleModels.findModel(eq(modelType))).thenReturn(model);
      when(moduleModels.hasModel(eq(modelType))).thenReturn(true);
      when(moduleModels.getGradlePath()).thenReturn(gradlePath);

      return this;
    }

    @NotNull
    private OutputBuildAction.PostBuildProjectModels build() {
      OutputBuildAction.PostBuildProjectModels models = mock(OutputBuildAction.PostBuildProjectModels.class);
      for (String path : myModels.keySet()) {
        when(models.getModels(eq(path))).thenReturn(myModels.get(path));
      }
      return models;
    }
  }
}