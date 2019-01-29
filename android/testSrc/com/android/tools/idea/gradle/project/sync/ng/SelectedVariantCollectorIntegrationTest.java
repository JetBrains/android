/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.Projects;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.Arrays.asList;

/**
 * Tests for {@link SelectedVariantCollector}.
 */
public class SelectedVariantCollectorIntegrationTest extends AndroidGradleTestCase {
  @NotNull private static final String COMPOSITE_BUILD_ROOT_PROJECT = COMPOSITE_BUILD + "/TestCompositeApp";

  private SelectedVariantCollector myCollector;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    prepareCompositeProject();
    myCollector = new SelectedVariantCollector(getProject());
  }

  // Copy included projects, update wrapper and gradle files for included projects.
  private void prepareCompositeProject() throws IOException {
    File testDataPath = new File(getTestDataPath(), toSystemDependentName(COMPOSITE_BUILD));
    File projectFolderPath = Projects.getBaseDirPath(myFixture.getProject());

    List<String> includedProjects = asList("TestCompositeLib1", "TestCompositeLib2", "TestCompositeLib3", "TestCompositeLib4");
    for (String includedProject : includedProjects) {
      File srcRoot = new File(testDataPath, includedProject);
      File includedProjectRoot = new File(projectFolderPath, includedProject);
      prepareProjectForImport(srcRoot, includedProjectRoot);
    }
  }

  public void testCollectSelectedVariantsWithCompositeBuild() throws Exception {
    loadProject(COMPOSITE_BUILD_ROOT_PROJECT);
    Map<String, String> expected = getExpectedSelectedVariantPerModule();

    SelectedVariants selectedVariants = myCollector.collectSelectedVariants();
    assertEquals(expected.size(), selectedVariants.size());

    for (Map.Entry<String, String> entry : expected.entrySet()) {
      String moduleId = entry.getKey();
      assertEquals(entry.getValue(), selectedVariants.getSelectedVariant(moduleId));
    }
  }

  @NotNull
  private Map<String, String> getExpectedSelectedVariantPerModule() {
    Map<String, String> expectedVariantPerModule = new HashMap<>();
    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        GradleFacet gradleFacet = GradleFacet.getInstance(module);
        assertNotNull(gradleFacet);
        GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
        assertNotNull(gradleModel);
        String moduleId = createUniqueModuleId(gradleModel.getRootFolderPath(), gradleFacet.getConfiguration().GRADLE_PROJECT_PATH);
        expectedVariantPerModule.put(moduleId, androidModel.getSelectedVariant().getName());
      }
    }
    return expectedVariantPerModule;
  }
}