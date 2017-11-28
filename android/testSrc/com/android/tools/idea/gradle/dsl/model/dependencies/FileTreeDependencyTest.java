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
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DependenciesModelImpl} and {@link FileTreeDependencyModelImpl}.
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
    assertEquals(ImmutableList.of("*.jar"), dependency.includes());
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
    assertEquals(ImmutableList.of("*.jar"), dependency.includes());
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
    assertEquals(ImmutableList.of("*.jar"), dependency.includes());
    assertEquals(ImmutableList.of("*.aar"), dependency.excludes());
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

  public void testAddFileTreeWithDirOnly() throws IOException {
    String text = "dependencies {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).isEmpty();

    dependencies.addFileTree("compile", "libs");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();
    fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);
    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("libs", fileTree.dir());
  }

  public void testAddFileTreeWithDirAndIncludeAttributePattern() throws IOException {
    String text = "dependencies {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).isEmpty();

    dependencies.addFileTree("compile", "libs", ImmutableList.of("*.jar"), null);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies();
    fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);
    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("libs", fileTree.dir());
    assertEquals(ImmutableList.of("*.jar"), fileTree.includes());
  }

  public void testAddFileTreeWithDirAndIncludeAttributeList() throws IOException {
    String text = "dependencies {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).isEmpty();

    dependencies.addFileTree("compile", "libs", ImmutableList.of("*.jar", "*.aar"), null);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies();
    fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);
    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("libs", fileTree.dir());
    assertEquals(ImmutableList.of("*.jar", "*.aar"), fileTree.includes());
  }

  public void testAddFileTreeWithDirAndExcludeAttributeList() throws IOException {
    String text = "dependencies {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).isEmpty();

    dependencies.addFileTree("compile", "libs", ImmutableList.of("*.jar"), ImmutableList.of("*.aar"));
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    dependencies = buildModel.dependencies();
    fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);
    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("libs", fileTree.dir());
    assertEquals(ImmutableList.of("*.jar"), fileTree.includes());
    assertEquals(ImmutableList.of("*.aar"), fileTree.excludes());
  }

  public void testRemoveFileTreeDependency() throws IOException {
    String text = "dependencies {\n" +
                  "    compile fileTree(dir: 'libs', include: ['*.jar'], exclude: ['*.aar'])\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);

    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("libs", fileTree.dir());
    assertEquals(ImmutableList.of("*.jar"), fileTree.includes());
    assertEquals(ImmutableList.of("*.aar"), fileTree.excludes());

    dependencies.remove(fileTree);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertThat(buildModel.dependencies().fileTrees()).isEmpty();
  }
}
