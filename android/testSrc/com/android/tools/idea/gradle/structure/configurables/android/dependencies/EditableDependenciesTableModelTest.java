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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.EditableDependenciesTableModel.DependencyCellRenderer;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.google.common.collect.Lists;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.ui.ColumnInfo;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EditableDependenciesTableModel}.
 */
public class EditableDependenciesTableModelTest extends IdeaTestCase {
  private boolean myOriginalShowGroupId;
  private PsLibraryDependency myLibraryDependencyModel;

  private EditableDependenciesTableModel myTableModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myOriginalShowGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    myLibraryDependencyModel = mock(PsLibraryDependency.class);

    List<PsAndroidDependency> dependencies = Lists.newArrayList();
    dependencies.add(myLibraryDependencyModel);
    myTableModel = new EditableDependenciesTableModel(dependencies);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = myOriginalShowGroupId;
    }
    finally {
      super.tearDown();
    }
  }

  public void testShowArtifactDependencySpec() {
    PsArtifactDependencySpec spec = new PsArtifactDependencySpec("appcompat-v7", "com.android.support", "23.1.0");
    when(myLibraryDependencyModel.getResolvedSpec()).thenReturn(spec);
    when(myLibraryDependencyModel.getDeclaredSpec()).thenReturn(spec);
    when(myLibraryDependencyModel.getValueAsText()).thenReturn("com.android.support:appcompat-v7:23.1.0");

    ColumnInfo[] columnInfos = myTableModel.getColumnInfos();

    PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;

    //noinspection unchecked
    ColumnInfo<PsAndroidDependency, String> specColumnInfo = columnInfos[0];
    DependencyCellRenderer renderer = (DependencyCellRenderer)specColumnInfo.getRenderer(myLibraryDependencyModel);
    assertNotNull(renderer);

    String text = renderer.getText();
    assertEquals("com.android.support:appcompat-v7:23.1.0", text);

    PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;

    text = renderer.getText();
    assertEquals("appcompat-v7:23.1.0", text);
  }

  public void testShowConfigurationName() {
    when(myLibraryDependencyModel.getConfigurationName()).thenReturn("compile");

    ColumnInfo[] columnInfos = myTableModel.getColumnInfos();
    //noinspection unchecked
    ColumnInfo<PsAndroidDependency, String> scopeColumnInfo = columnInfos[1];
    String columnText = scopeColumnInfo.valueOf(myLibraryDependencyModel);
    assertEquals("compile", columnText);
  }
}