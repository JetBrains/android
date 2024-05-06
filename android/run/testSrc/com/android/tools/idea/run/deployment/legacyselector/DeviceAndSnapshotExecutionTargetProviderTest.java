/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;

import com.intellij.execution.ExecutionTargetProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotExecutionTargetProviderTest {
  @NotNull
  private final AsyncDevicesGetter myGetter = Mockito.mock(AsyncDevicesGetter.class);

  @NotNull
  private final DeviceAndSnapshotComboBoxAction myAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

  @NotNull
  private final Project myProject = Mockito.mock(Project.class);

  private final ExecutionTargetProvider myProvider = new DeviceAndSnapshotExecutionTargetProvider(() -> myAction, project -> myGetter);

  @NotNull
  private final RunConfiguration myConfiguration = Mockito.mock(RunConfiguration.class);

  @Test
  public void getTargetsTargetsArePresent() {
    // Arrange
    setupAndroidFacetMock(myProject);
    var targets = Set.<Target>of(new QuickBootTarget(Keys.PIXEL_4_API_30));
    Mockito.when(myAction.getSelectedTargets(myProject)).thenReturn(Optional.of(targets));

    // Act
    var executionTargets = myProvider.getTargets(myProject, myConfiguration);

    // Assert
    assertEquals(List.of(new DeviceAndSnapshotComboBoxExecutionTarget(targets, myGetter)), executionTargets);
  }

  @Test
  public void getTargets() {
    // Act
    setupAndroidFacetMock(myProject);
    var executionTargets = myProvider.getTargets(myProject, myConfiguration);

    // Assert
    assertEquals(List.of(new DeviceAndSnapshotComboBoxExecutionTarget(Set.of(), myGetter)), executionTargets);
  }

  private static void setupAndroidFacetMock(Project project){
    ProjectFacetManager mockProjectManager = Mockito.mock(ProjectFacetManager.class);
    Mockito.when(mockProjectManager.hasFacets(AndroidFacet.ID)).thenReturn(true);

    Mockito.when(ProjectFacetManager.getInstance(project)).thenReturn(mockProjectManager);
  }
}
