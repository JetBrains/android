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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData;
import com.android.tools.idea.gradle.project.sync.InternedModels;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDeclaredDependenciesTableModel.DependencyCellRenderer;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectModels;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ui.ColumnInfo;
import java.io.File;

/**
 * Tests for {@link AbstractDeclaredDependenciesTableModel}.
 */
public class AbstractDeclaredDependenciesTableModelTest extends PlatformTestCase {
  private PsLibraryAndroidDependency myLibraryDependency;
  private PsUISettings myUISettings;

  private AbstractDeclaredDependenciesTableModel<PsAndroidDependency> myTableModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myLibraryDependency = mock(PsLibraryAndroidDependency.class);
    myUISettings = new PsUISettings();

    PsProject project = mock(PsProject.class);
    when(project.getName()).thenReturn("Project");
    Module resolvedModel = mock(Module.class);
    when(resolvedModel.getName()).thenReturn("resolvedModelName");
    GradleBuildModel parsedModel = mock(GradleBuildModel.class);
    when(parsedModel.ext()).thenReturn(mock(ExtModel.class));

    AndroidProjectModels projectModels =
      new AndroidProjectBuilder().build().invoke("name", ":name", new File("/"), new File("/name"), "7.0.0", new InternedModels(null));
    GradleAndroidModelData gradleAndroidModel = mock(GradleAndroidModelData.class);
    when(gradleAndroidModel.getAndroidProject()).thenReturn(projectModels.getAndroidProject());
    PsAndroidModule module = new PsAndroidModule(project, ":name");
    module.init("name", null, new GradleAndroidModel(gradleAndroidModel, getProject(), mock(IdeLibraryModelResolver.class)), null, null,
                parsedModel);
    PsContext context = mock(PsContext.class);
    when(context.getUiSettings()).thenReturn(myUISettings);
    myTableModel = new AbstractDeclaredDependenciesTableModel<PsAndroidDependency>(module, context) {};
  }

  public void testShowArtifactDependencySpec() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.Companion.create("com.android.support", "appcompat-v7", "23.1.0");
    when(myLibraryDependency.getSpec()).thenReturn(spec);
    when(myLibraryDependency.toText()).thenReturn("com.android.support:appcompat-v7:23.1.0");

    ColumnInfo[] columnInfos = myTableModel.getColumnInfos();

    myUISettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;

    //noinspection unchecked
    ColumnInfo<PsAndroidDependency, String> specColumnInfo = columnInfos[0];
    DependencyCellRenderer renderer = (DependencyCellRenderer)specColumnInfo.getRenderer(myLibraryDependency);
    assertNotNull(renderer);

    String text = renderer.getText();
    assertEquals("com.android.support:appcompat-v7:23.1.0", text);

    myUISettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;

    text = renderer.getText();
    assertEquals("appcompat-v7:23.1.0", text);
  }

  public void testShowConfigurationName() {
    when(myLibraryDependency.getJoinedConfigurationNames()).thenReturn("compile");

    ColumnInfo[] columnInfos = myTableModel.getColumnInfos();
    //noinspection unchecked
    ColumnInfo<PsAndroidDependency, String> scopeColumnInfo = columnInfos[1];
    String columnText = scopeColumnInfo.valueOf(myLibraryDependency);
    assertEquals("compile", columnText);
  }
}
