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
package com.android.tools.idea.gradle.structure.configurables.dependencies;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyEditor;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidLibraryDependencyEditor;
import com.google.common.collect.Lists;
import com.intellij.util.ui.ColumnInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EditableDependenciesTableModel}.
 */
public class EditableDependenciesTableModelTest {
  private PsdAndroidLibraryDependencyEditor myLibraryDependencyEditor;
  private EditableDependenciesTableModel myTableModel;

  @Before
  public void setUp() {
    myLibraryDependencyEditor = mock(PsdAndroidLibraryDependencyEditor.class);

    List<PsdAndroidDependencyEditor> dependencyEditors = Lists.newArrayList();
    dependencyEditors.add(myLibraryDependencyEditor);
    myTableModel = new EditableDependenciesTableModel(dependencyEditors);
  }

  @Test
  public void testShowArtifactDependencySpec() {
    ArtifactDependencySpec spec = new ArtifactDependencySpec("appcompat-v7", "com.android.support", "23.1.0");
    when(myLibraryDependencyEditor.getSpec()).thenReturn(spec);
    when(myLibraryDependencyEditor.getValueAsText()).thenReturn("com.android.support:appcompat-v7:23.1.0");

    ColumnInfo[] columnInfos = myTableModel.getColumnInfos();

    myTableModel.setShowGroupIds(true);

    //noinspection unchecked
    ColumnInfo<PsdAndroidDependencyEditor, String> specColumnInfo = columnInfos[0];
    String columnText = specColumnInfo.valueOf(myLibraryDependencyEditor);
    assertEquals("com.android.support:appcompat-v7:23.1.0", columnText);

    myTableModel.setShowGroupIds(false);

    columnText = specColumnInfo.valueOf(myLibraryDependencyEditor);
    assertEquals("appcompat-v7:23.1.0", columnText);
  }

  @Test
  public void testShowConfigurationName() {
    when(myLibraryDependencyEditor.getConfigurationName()).thenReturn("compile");

    ColumnInfo[] columnInfos = myTableModel.getColumnInfos();
    //noinspection unchecked
    ColumnInfo<PsdAndroidDependencyEditor, String> scopeColumnInfo = columnInfos[1];
    String columnText = scopeColumnInfo.valueOf(myLibraryDependencyEditor);
    assertEquals("compile", columnText);
  }
}