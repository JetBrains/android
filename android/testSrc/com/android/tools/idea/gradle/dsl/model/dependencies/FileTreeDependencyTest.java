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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DependenciesModel} and {@link FileTreeDependencyModel}.
 */
public class FileTreeDependencyTest extends GradleFileModelTestCase {
  public void testParseFileTreeWithDirAndIncludeAttributeList() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree(dir: 'libs', include: ['*.jar'])\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir());
    assertThat(dependency.include()).containsExactly("*.jar");
  }

  public void testParseFileTreeWithDirAndIncludeAttributePattern() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree(dir: 'libs', include: '*.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir());
    assertThat(dependency.include()).containsExactly("*.jar");
  }

  public void testParseFileTreeWithDirAndExcludeAttributeList() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree(dir: 'libs', include: ['*.jar'], exclude: ['*.aar'])\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir());
    assertThat(dependency.include()).containsExactly("*.jar");
    assertThat(dependency.exclude()).containsExactly("*.aar");
  }

  public void testParseFileTreeWithDirOnly() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree('libs')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir());
  }

  public void testSetDirWhenIncludeSpecified() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree(dir: 'libs', include: '*.jar')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir());

    dependency.setDir("jars");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    dependency = dependencies.get(0);
    assertEquals("jars", dependency.dir());
  }

  public void testSetDir() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree('libs')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir());

    dependency.setDir("jars");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    dependency = dependencies.get(0);
    assertEquals("jars", dependency.dir());
  }
}
