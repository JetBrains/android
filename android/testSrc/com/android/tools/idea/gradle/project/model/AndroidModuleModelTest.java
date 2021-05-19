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
package com.android.tools.idea.gradle.project.model;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.stubs.android.AndroidProjectStub.toIdeAndroidProject;
import static com.android.tools.idea.gradle.stubs.android.AndroidProjectStub.toIdeVariants;
import static com.android.utils.FileUtils.writeToFile;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.stubs.FileStructure;
import com.android.tools.idea.gradle.stubs.android.AndroidArtifactStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.gradle.util.GradleBuildOutputUtilTest;
import com.android.tools.idea.gradle.util.LastBuildOrSyncService;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.io.IOException;

/**
 * Tests for {@link AndroidModuleModel}.
 */
public class AndroidModuleModelTest extends AndroidGradleTestCase {

  public void testSelectedVariantExistsButNotRequested() {
    AndroidProjectStub androidProject = new AndroidProjectStub("MyApp");
    androidProject.clearVariants();
    // Simulate the case that variant names are "release" and "debug", but only "release" variant is requested.
    androidProject.addVariant("release");
    androidProject.setVariantNames("debug", "release");

    // Create AndroidModuleModel with "debug" as selected variant.
    AndroidModuleModel androidModel =
      AndroidModuleModel.create(androidProject.getName(),
                                getBaseDirPath(getProject()),
                                toIdeAndroidProject(androidProject),
                                toIdeVariants(androidProject),
                                "debug");

    // Verify that "release" is set as selected variant.
    assertThat(androidModel.getSelectedVariant().getName()).isEqualTo("release");

    // Verify that findVariantToSelect selects specified variant if it has been requested, selects the first available one otherwise.
    assertThat(androidModel.findVariantToSelect("release")).isEqualTo("release");
    assertThat(androidModel.findVariantToSelect("debug")).isEqualTo("release");
  }

  public void testSelectedVariantWasRequested() {
    AndroidProjectStub androidProject = new AndroidProjectStub("MyApp");
    androidProject.clearVariants();
    // Simulate the case that variant names are "release" and "debug", but only "release" variant is requested.
    androidProject.addVariant("release");
    androidProject.setVariantNames("debug", "release");

    // Create AndroidModuleModel with "release" as selected variant.
    AndroidModuleModel androidModel =
      AndroidModuleModel.create(androidProject.getName(),
                                getBaseDirPath(getProject()),
                                toIdeAndroidProject(androidProject),
                                toIdeVariants(androidProject),
                                "release");
    // Verify that "release" is set as selected variant.
    assertThat(androidModel.getSelectedVariant().getName()).isEqualTo("release");
  }

  public void testApplicationIdFromCache() throws IOException {
    File rootFile = getProjectFolderPath();
    File outputFile = new File (rootFile, "build/output/apk/test/output.json");
    writeToFile(outputFile, new GradleBuildOutputUtilTest().getSingleAPKOutputFileText());

    FileStructure fileStructure = new FileStructure(rootFile);
    AndroidArtifactStub androidArtifact = new AndroidArtifactStub("test", "test", "test", fileStructure);
    VariantStub variant = new VariantStub("test", "test", fileStructure, androidArtifact);

    AndroidProjectStub androidProject = new AndroidProjectStub("MyApp", fileStructure);
    androidProject.setModelVersion("4.2.0");
    androidProject.clearVariants();
    androidProject.addVariant(variant);

    // Create AndroidModuleModel with "release" as selected variant.
    AndroidModuleModel androidModel = AndroidModuleModel.create(androidProject.getName(),
                                                                rootFile,
                                                                toIdeAndroidProject(androidProject),
                                                                toIdeVariants(androidProject),
                                                                "test");
    Module mockModule = mock(Module.class);
    when(mockModule.getProject()).thenReturn(getProject());
    androidModel.setModule(mockModule);

    assertThat(androidModel.getApplicationId()).isEqualTo("com.example.myapplication");
    // Change the value
    String newSingleAPKOutputFileTest =
      new GradleBuildOutputUtilTest().getSingleAPKOutputFileText().replace("com.example.myapplication", "com.cool.app");
    writeToFile(outputFile, newSingleAPKOutputFileTest);
    // Check cache still produces old value
    assertThat(androidModel.getApplicationId()).isEqualTo("com.example.myapplication");
    getProject().getService(LastBuildOrSyncService.class).setLastBuildOrSyncTimeStamp(System.currentTimeMillis());

    // Fake the cache clearing and check new value is re-parsed
    assertThat(androidModel.getApplicationId()).isEqualTo("com.cool.app");
  }
}
