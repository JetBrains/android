/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.model.IdeArtifactLibrary;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public abstract class IdeAndroidProjectIntegrationTestCase extends AndroidGradleTestCase {
  protected void verifyIdeLevel2DependenciesPopulated() {
    @Nullable AndroidModuleModel androidModel = getAndroidModelInApp();
    assertNotNull(androidModel);

    // Verify IdeLevel2Dependencies are populated for each variant.
    androidModel.getVariants().forEach(variant -> {
      IdeDependencies level2Dependencies = variant.getMainArtifact().getLevel2Dependencies();
      assertThat(level2Dependencies).isNotNull();
      assertThat(level2Dependencies.getAndroidLibraries()).isNotEmpty();
      assertThat(level2Dependencies.getJavaLibraries()).isNotEmpty();
    });
  }

  @Nullable
  protected AndroidModuleModel getAndroidModelInApp() {
    Module appModule = TestModuleUtil.findAppModule(getProject());
    return AndroidModuleModel.get(appModule);
  }

  protected void verifyAarModuleShowsAsAndroidLibrary(String expectedLibraryName) {
    @Nullable AndroidModuleModel androidModel = getAndroidModelInApp();
    assertNotNull(androidModel);

    // Aar module should show up as android library dependency, not module dependency for app module.
    androidModel.getVariants().forEach(variant -> {
      IdeDependencies level2Dependencies = variant.getMainArtifact().getLevel2Dependencies();
      assertThat(level2Dependencies).isNotNull();
      assertThat(level2Dependencies.getModuleDependencies()).isEmpty();
      List<String> androidLibraries = ContainerUtil.map(level2Dependencies.getAndroidLibraries(), IdeArtifactLibrary::getArtifactAddress);
      assertThat(level2Dependencies.getAndroidLibraries()).isNotEmpty();
      assertThat(androidLibraries).contains(expectedLibraryName);
    });
  }
}
