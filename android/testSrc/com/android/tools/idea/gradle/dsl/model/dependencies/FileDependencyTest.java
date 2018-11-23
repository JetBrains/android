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

import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_ADD_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_PARSE_FILE_DEPENDENCIES_WITH_CLOSURE;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_PARSE_MULTIPLE_FILE_DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_PARSE_SINGLE_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_REMOVE_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_REMOVE_ONE_OF_FILE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_SET_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_DEPENDENCY_UPDATE_ONE_OF_FILE_DEPENDENCY;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link DependenciesModelImpl} and {@link FileDependencyModelImpl}.
 */
public class FileDependencyTest extends GradleFileModelTestCase {
  @Test
  public void testParseSingleFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_PARSE_SINGLE_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testParseMultipleFileDependencies() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_PARSE_MULTIPLE_FILE_DEPENDENCIES);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(3);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib2.jar", fileDependencies.get(1).file().toString());
    assertEquals("lib3.jar", fileDependencies.get(2).file().toString());
  }

  @Test
  public void testParseFileDependenciesWithClosure() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_PARSE_FILE_DEPENDENCIES_WITH_CLOSURE);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib2.jar", fileDependencies.get(1).file().toString());
  }

  @Test
  public void testSetFile() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_SET_FILE);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);

    FileDependencyModel fileDependency = fileDependencies.get(0);
    assertEquals("lib1.jar", fileDependency.file().toString());

    fileDependency.file().setValue("lib2.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib2.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testUpdateOneOfFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_UPDATE_ONE_OF_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());

    FileDependencyModel fileDependency = fileDependencies.get(1);
    assertEquals("lib2.jar", fileDependency.file().toString());

    fileDependency.file().setValue("lib3.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
    assertEquals("lib3.jar", fileDependencies.get(1).file().toString());
  }

  @Test
  public void testAddFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_ADD_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();

    DependenciesModel dependencies = buildModel.dependencies();
    assertThat(dependencies.files()).isEmpty();

    dependencies.addFile("compile", "lib1.jar");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<FileDependencyModel> fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
  }

  @Test
  public void testRemoveFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_REMOVE_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(1);

    FileDependencyModel file = fileDependencies.get(0);
    assertEquals("lib1.jar", file.file().toString());

    dependencies.remove(file);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertThat(buildModel.dependencies().files()).isEmpty();
  }

  @Test
  public void testRemoveOneOfFileDependency() throws IOException {
    writeToBuildFile(FILE_DEPENDENCY_REMOVE_ONE_OF_FILE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileDependencyModel> fileDependencies = dependencies.files();
    assertThat(fileDependencies).hasSize(2);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());

    FileDependencyModel file = fileDependencies.get(1);
    assertEquals("lib2.jar", file.file().toString());

    dependencies.remove(file);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    fileDependencies = buildModel.dependencies().files();
    assertThat(fileDependencies).hasSize(1);
    assertEquals("lib1.jar", fileDependencies.get(0).file().toString());
  }
}
