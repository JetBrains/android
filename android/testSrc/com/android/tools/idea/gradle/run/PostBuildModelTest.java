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

import com.android.builder.model.ProjectBuildOutput;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PostBuildModel}.
 */
public class PostBuildModelTest extends IdeaTestCase {
  @Mock private ProjectBuildOutput myAppOutput;
  @Mock private ProjectBuildOutput myLibOutput;

  private Module myLibModule;
  private Module myJavaLibModule;

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

    List<OutputBuildAction.ModuleBuildOutput> outputs = new ArrayList<>();
    outputs.add(new OutputBuildAction.ModuleBuildOutput(":app", myAppOutput));
    outputs.add(new OutputBuildAction.ModuleBuildOutput(":lib", myLibOutput));

    myJavaLibModule = createModule("javaLib");

    myModel = new PostBuildModel(outputs);
  }

  public void testFindOutputModelForGradlePath() {
    assertSame(myAppOutput, myModel.findOutputModel(":app"));
    assertSame(myLibOutput, myModel.findOutputModel(":lib"));
    assertNull(myModel.findOutputModel(":javaLib"));
  }

  public void testFindOutputModelForModule() {
    assertSame(myAppOutput, myModel.findOutputModel(myModule));
    assertSame(myLibOutput, myModel.findOutputModel(myLibModule));
    assertNull(myModel.findOutputModel(myJavaLibModule));
  }

  public void testFindOutputModelForFacet() {
    AndroidFacet androidFacet = createAndAddAndroidFacet(myModule);
    assertSame(myAppOutput, myModel.findOutputModel(androidFacet));

    androidFacet = createAndAddAndroidFacet(myLibModule);
    assertSame(myLibOutput, myModel.findOutputModel(androidFacet));
  }
}