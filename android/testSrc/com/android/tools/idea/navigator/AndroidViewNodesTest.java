/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator;

import com.android.tools.idea.navigator.nodes.android.AndroidManifestFileNode;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AndroidViewNodes}.
 */
public class AndroidViewNodesTest extends AndroidGradleTestCase {
  private AndroidViewNodes myAndroidViewNodes;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidViewNodes = new AndroidViewNodes();
  }

  public void testFindAndRefreshNode() throws Exception {
    loadSimpleApplication();
    createAndroidProjectViewPane();

    AndroidManifestFileNode node = myAndroidViewNodes.findAndRefreshNodeOfType(AndroidManifestFileNode.class, getProject());
    assertNotNull(node);
  }

  public void testSelectNodeOfType() throws Exception {
    Project project = getProject();
    ToolWindow projectToolWindow = new MyToolWindow(project);

    loadSimpleApplication();
    AndroidProjectViewPane projectViewPane = createAndroidProjectViewPane();

    AndroidManifestFileNode node = myAndroidViewNodes.selectNodeOfType(AndroidManifestFileNode.class, project, projectToolWindow);
    assertNotNull(node);

    Set<Object> elements = projectViewPane.getTreeBuilder().getSelectedElements();
    assertThat(elements).containsExactly(node);
  }

  @NotNull
  private AndroidProjectViewPane createAndroidProjectViewPane() {
    Project project = getProject();
    AndroidProjectViewPane projectViewPane = new AndroidProjectViewPane(project);

    projectViewPane.createComponent();
    Disposer.register(project, projectViewPane);

    ProjectView projectView = mock(ProjectView.class);
    IdeComponents.replaceService(project, ProjectView.class, projectView);
    when(projectView.getProjectViewPaneById(AndroidProjectViewPane.ID)).thenReturn(projectViewPane);

    return projectViewPane;
  }

  private static class MyToolWindow extends ToolWindowHeadlessManagerImpl.MockToolWindow {
    public MyToolWindow(@NotNull Project project) {
      super(project);
    }

    @Override
    public void activate(@Nullable Runnable runnable) {
      if (runnable != null) {
        runnable.run();
      }
    }
  }
}