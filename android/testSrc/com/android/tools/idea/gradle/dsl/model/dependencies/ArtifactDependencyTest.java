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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames;
import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class ArtifactDependencyTest extends GradleFileModelTestCase {
  public void testMapNotation() throws Exception {
    String text = "dependencies {\n" +
                  "  compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";

    checkReadAndModify(text);
  }

  private void checkReadAndModify(@NotNull String text) throws IOException {
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    ArtifactDependencyModel dependencyModel = getDependency(buildModel.dependenciesV2());

    // Test Read
    assertEquals(new ExternalDependencySpec("guice", "com.google.code.guice", "1.0"), dependencyModel.getDependencySpec());

    // Test reset
    dependencyModel.setVersion("1.1");
    assertEquals("1.1", dependencyModel.version());
    buildModel.resetState();
    assertEquals("1.0", dependencyModel.version());

    // Test Read after write
    dependencyModel.setVersion("1.2");

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assert(dependenciesModel != null);
    dependencyModel = getDependency(dependenciesModel);

    assertEquals(new ExternalDependencySpec("guice", "com.google.code.guice", "1.2"),
                 dependencyModel.getDependencySpec());

    // Test Remove
    dependenciesModel.remove(dependencyModel);
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    dependenciesModel = buildModel.dependenciesV2();
    assert(dependenciesModel != null);
    assertEmpty(dependenciesModel.artifactDependencies("compile"));
  }

  private static ArtifactDependencyModel getDependency(@Nullable DependenciesModel dependenciesModel) {
    assertNotNull(dependenciesModel);
    List<ArtifactDependencyModel> artifactDependencies = dependenciesModel.artifactDependencies(CommonConfigurationNames.COMPILE);
    assertSize(1, artifactDependencies);
    return artifactDependencies.get(0);
  }
}
