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

import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_ADD_AND_REMOVE_INCLUDE_WITHOUT_APPLY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_AND_EXCLUDE_ATTRIBUTE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_PATTERN;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_ONLY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_AND_EXCLUDE_ATTRIBUTE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_PATTERN;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_ONLY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_REMOVE_FILE_TREE_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_REMOVE_ONLY_POSSIBLE_IN_MAP_FORM;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_SET_DIR;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_SET_DIR_FROM_EMPTY;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_SET_DIR_WHEN_INCLUDE_SPECIFIED;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_SET_INCLUDES_IN_METHOD_CALL_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileName.FILE_TREE_DEPENDENCY_SET_REFERENCE_DIR_IN_METHOD_CALL_NOTATION;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link DependenciesModelImpl} and {@link FileTreeDependencyModelImpl}.
 */
public class FileTreeDependencyTest extends GradleFileModelTestCase {
  @Test
  public void testParseFileTreeWithDirAndIncludeAttributeList() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    verifyPropertyModel(dependency.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyListProperty(dependency.includes(), "dependencies.compile.include", ImmutableList.of("*.jar"));
    assertMissingProperty(dependency.excludes());
  }

  @Test
  public void testParseFileTreeWithDirAndIncludeAttributePattern() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_PATTERN);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel fileTree = dependencies.get(0);
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyPropertyModel(fileTree.includes(), STRING_TYPE, "*.jar", STRING, DERIVED, 0, "include");
    assertMissingProperty(fileTree.excludes());
  }

  @Test
  public void testParseFileTreeWithDirAndExcludeAttributeList() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_AND_EXCLUDE_ATTRIBUTE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel fileTree = dependencies.get(0);
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyListProperty(fileTree.includes(), "dependencies.compile.include", ImmutableList.of("*.jar"));
    verifyListProperty(fileTree.excludes(), "dependencies.compile.exclude", ImmutableList.of("*.aar"));
  }

  @Test
  public void testParseFileTreeWithDirOnly() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_PARSE_FILE_TREE_WITH_DIR_ONLY);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir().toString());
  }

  @Test
  public void testSetDirWhenIncludeSpecified() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_SET_DIR_WHEN_INCLUDE_SPECIFIED);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir().toString());

    dependency.dir().setValue("jars");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    dependency = dependencies.get(0);
    assertEquals("jars", dependency.dir().toString());
  }

  @Test
  public void testSetDir() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_SET_DIR);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<FileTreeDependencyModel> dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    FileTreeDependencyModel dependency = dependencies.get(0);
    assertEquals("libs", dependency.dir().toString());

    dependency.dir().setValue("jars");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().fileTrees();
    assertThat(dependencies).hasSize(1);

    dependency = dependencies.get(0);
    assertEquals("jars", dependency.dir().toString());
  }

  @Test
  public void testAddFileTreeWithDirOnly() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_ONLY);

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
    assertEquals("libs", fileTree.dir().toString());
  }

  @Test
  public void testAddFileTreeWithDirAndIncludeAttributePattern() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_PATTERN);

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
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyListProperty(fileTree.includes(), "dependencies.compile.include", ImmutableList.of("*.jar"));
    assertMissingProperty(fileTree.excludes());
  }

  @Test
  public void testAddFileTreeWithDirAndIncludeAttributeList() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_AND_INCLUDE_ATTRIBUTE_LIST);

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
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyListProperty(fileTree.includes(), ImmutableList.of("*.jar", "*.aar"), true);
    assertMissingProperty(fileTree.excludes());
  }

  @Test
  public void testAddFileTreeWithDirAndExcludeAttributeList() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_ADD_FILE_TREE_WITH_DIR_AND_EXCLUDE_ATTRIBUTE_LIST);

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
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyListProperty(fileTree.includes(), "dependencies.compile.include", ImmutableList.of("*.jar"));
    verifyListProperty(fileTree.excludes(), "dependencies.compile.exclude", ImmutableList.of("*.aar"));
  }

  @Test
  public void testRemoveFileTreeDependency() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_REMOVE_FILE_TREE_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);

    FileTreeDependencyModel fileTree = fileTrees.get(0);
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "libs", STRING, DERIVED, 0, "dir");
    verifyListProperty(fileTree.includes(), "dependencies.compile.include", ImmutableList.of("*.jar"));
    verifyListProperty(fileTree.excludes(), "dependencies.compile.exclude", ImmutableList.of("*.aar"));

    dependencies.remove(fileTree);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertThat(buildModel.dependencies().fileTrees()).isEmpty();
  }

  @Test
  public void testSetDirFromEmpty() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_SET_DIR_FROM_EMPTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(0);
  }

  @Test
  public void testSetReferenceDirInMethodCallNotation() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_SET_REFERENCE_DIR_IN_METHOD_CALL_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);

    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("lib", fileTree.dir().toString());

    // Set the value to a new one
    fileTree.dir().setValue(new ReferenceTo("libName"));
    applyChangesAndReparse(buildModel);

    dependenciesModel = buildModel.dependencies();
    fileTrees = dependenciesModel.fileTrees();
    assertEquals("newLib", fileTrees.get(0).dir().toString());
  }

  @Test
  public void testSetIncludesInMethodCallNotation() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_SET_INCLUDES_IN_METHOD_CALL_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);

    FileTreeDependencyModel fileTree = fileTrees.get(0);
    assertEquals("lib", fileTree.dir().toString());

    // Set the value to a new one
    fileTree.dir().setValue("hello");
    fileTree.includes().addListValue().setValue("*.aar");

    applyChangesAndReparse(buildModel);

    dependenciesModel = buildModel.dependencies();
    fileTrees = dependenciesModel.fileTrees();
    assertEquals("hello", fileTrees.get(0).dir().toString());
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "hello", STRING, DERIVED, 0, "dir");
    verifyListProperty(fileTree.includes(), "dependencies.compile.include", ImmutableList.of("*.aar"));
    assertMissingProperty(fileTree.excludes());
  }

  @Test
  public void testAddAndRemoveIncludeWithoutApply() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_ADD_AND_REMOVE_INCLUDE_WITHOUT_APPLY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);

    FileTreeDependencyModel fileTree = fileTrees.get(0);
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "lib", STRING, DERIVED, 0, "0");
    assertMissingProperty(fileTree.includes());
    assertMissingProperty(fileTree.excludes());
    fileTree.includes().setValue("*.jar");

    applyChangesAndReparse(buildModel);

    dependenciesModel = buildModel.dependencies();
    fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);
    fileTree = fileTrees.get(0);
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "lib", STRING, DERIVED, 0, "dir");
    verifyPropertyModel(fileTree.includes(), STRING_TYPE, "*.jar", STRING, DERIVED, 0, "include");
    assertMissingProperty(fileTree.excludes());
    fileTree.includes().delete();

    applyChangesAndReparse(buildModel);

    dependenciesModel = buildModel.dependencies();
    fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);
    fileTree = fileTrees.get(0);
    verifyPropertyModel(fileTree.dir(), STRING_TYPE, "lib", STRING, DERIVED, 0, "dir");
    assertMissingProperty(fileTree.includes());
    assertMissingProperty(fileTree.excludes());
  }

  @Test
  // Renaming these models is not really an operation that should be done, but since we allow it we need to check it works
  // with the transforms.
  public void testRemoveOnlyPossibleInMapForm() throws IOException {
    writeToBuildFile(FILE_TREE_DEPENDENCY_REMOVE_ONLY_POSSIBLE_IN_MAP_FORM);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();
    List<FileTreeDependencyModel> fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);

    FileTreeDependencyModel fileTree = fileTrees.get(0);
    try {
      fileTree.dir().rename("dirr");
      fail();
    }
    catch (UnsupportedOperationException e) {
      //expected
    }

    // Set includes to force it into list form.
    fileTree.includes().convertToEmptyList().addListValue().setValue("*.jar");

    applyChangesAndReparse(buildModel);

    dependenciesModel = buildModel.dependencies();
    fileTrees = dependenciesModel.fileTrees();
    assertThat(fileTrees).hasSize(1);
    fileTree = fileTrees.get(0);
    fileTree.dir().rename("dirr");

    // Now the property should be re-nameable.
    applyChangesAndReparse(buildModel);

    dependenciesModel = buildModel.dependencies();
    fileTrees = dependenciesModel.fileTrees();
    // The model is no longer picked up since the "dir" entry is not present.
    assertThat(fileTrees).hasSize(0);
  }
}
