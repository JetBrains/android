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

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link PostBuildModel}.
 */
public class PostBuildModelTest extends HeavyPlatformTestCase {
  @Mock private ProjectBuildOutput myAppOutput;
  @Mock private ProjectBuildOutput myLibOutput;
  @Mock private InstantAppProjectBuildOutput myInstantAppOutput;

  private PostBuildModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    OutputBuildAction.PostBuildProjectModels outputs = new PostBuildProjectModelsBuilder().
      setModelForModule(":app", ProjectBuildOutput.class, myAppOutput).
      setModelForModule(":lib", ProjectBuildOutput.class, myLibOutput).
      setModelForModule(":instantapp", InstantAppProjectBuildOutput.class, myInstantAppOutput).
      build();

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

  private static class PostBuildProjectModelsBuilder {
    @NotNull private final Map<String, OutputBuildAction.PostBuildModuleModels> myModels = new HashMap<>();

    @NotNull
    private <T> PostBuildModelTest.PostBuildProjectModelsBuilder setModelForModule(@NotNull String gradlePath,
                                                                                   @NotNull Class<T> modelType,
                                                                                   @NotNull T model) {
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