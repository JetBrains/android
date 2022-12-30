/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import static com.android.projectmodel.VariantUtil.ARTIFACT_NAME_MAIN;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.makeAutoIndexingOnCopy;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static java.util.Collections.emptyList;

import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.io.File;

public final class MlProjectTestUtil {

  public static JavaCodeInsightTestFixture setupTestMlProject(JavaCodeInsightTestFixture fixture, String version, int minSdk) {
    Project project = fixture.getProject();
    setupTestMlProject(project, version, minSdk);
    return makeAutoIndexingOnCopy(fixture);
  }

  public static void setupTestMlProject(Project project, String version, int minSdk) {
    FileUtil.createDirectory(new File(project.getBasePath(), "ml"));
    setupTestProjectFromAndroidModel(
      project,
      new File(project.getBasePath()),
      new AndroidModuleModelBuilder(
        ":",
        null,
        version,
        "debug",
        new AndroidProjectBuilder()
          .withMinSdk(it -> minSdk)
          .withMlModelBindingEnabled(it -> true)
          .withMainSourceProvider(it -> new IdeSourceProviderImpl(
            ARTIFACT_NAME_MAIN,
            it.getModuleBasePath(),
            "AndroidManifest.xml",
            ImmutableList.of("src"),
            ImmutableList.of("srcKotlin"),
            emptyList(),
            emptyList(),
            emptyList(),
            ImmutableList.of("res"),
            emptyList(),
            emptyList(),
            emptyList(),
            ImmutableList.of("ml"),
            emptyList())
          )));
  }
}
