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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DependenciesModelImpl} and {@link FileDependencyModelImpl}.
 */
public class FileDependencyTest extends GradleFileModelTestCase {
  public void testParseSingleFileDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib.jar", fileDependencies.get(0).file());
  }

  public void testParseMultipleFileDependencies() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib1.jar', 'lib2.jar')\n" +
                  "    compile files('lib3.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(3);
    assertEquals("lib1.jar", fileDependencies.get(0).file());
    assertEquals("lib2.jar", fileDependencies.get(1).file());
    assertEquals("lib3.jar", fileDependencies.get(2).file());
  }

  public void testParseFileDependenciesWithClosure() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib1.jar', 'lib2.jar') {\n" +
                  "      builtBy 'compile'\n" +
                  "    }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file());
    assertEquals("lib2.jar", fileDependencies.get(1).file());
  }

  public void testSetFile() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib1.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);

    FileDependencyModel fileDependency = fileDependencies.get(0);
    assertEquals("lib1.jar", fileDependency.file());

    fileDependency.setFile("lib2.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib2.jar", fileDependencies.get(0).file());
  }

  public void testUpdateOneOfFileDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib1.jar', 'lib2.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file());

    FileDependencyModel fileDependency = fileDependencies.get(1);
    assertEquals("lib2.jar", fileDependency.file());

    fileDependency.setFile("lib3.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file());
    assertEquals("lib3.jar", fileDependencies.get(1).file());
  }

  public void testAddFileDependency() throws IOException {
    String text = "dependencies {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    DependenciesModel dependencies = buildModel.dependencies();
    assertThat(dependencies.files()).isEmpty();

    dependencies.addFile("compile", "lib1.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib1.jar", fileDependencies.get(0).file());
  }

  public void testRemoveFileDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib1.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(1);

    FileDependencyModel file = fileDependencies.get(0);
    assertEquals("lib1.jar", file.file());

    dependencies.remove(file);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertThat(buildModel.dependencies().files()).isEmpty();
  }

  public void testRemoveOneOfFileDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    compile files('lib1.jar', 'lib2.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file());

    FileDependencyModel file = fileDependencies.get(1);
    assertEquals("lib2.jar", file.file());

    dependencies.remove(file);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib1.jar", fileDependencies.get(0).file());
  }
}
