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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.testing.ServiceUtil;
import com.intellij.execution.ExecutionTargetProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.ProjectRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotExecutionTargetProviderTest {

  @Rule
  public final DisposableRule myDisposableRule = new DisposableRule();

  @Rule
  public final ProjectRule myProjectRule = new ProjectRule();

  @NotNull
  private final AsyncDevicesGetter myGetter = Mockito.mock(AsyncDevicesGetter.class);

  @NotNull
  private final DeviceAndSnapshotComboBoxAction myAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

  private final ExecutionTargetProvider myProvider = new DeviceAndSnapshotExecutionTargetProvider(() -> myAction, project -> myGetter);

  @NotNull
  private final RunConfiguration myConfiguration = Mockito.mock(RunConfiguration.class);

  private static final ProjectFacetManager myProjectFacetManager = newProjectFacetManager(
    Arrays.asList(AndroidFacet.ID, ApkFacet.ID, GradleFacet.getFacetTypeId()));

  @Test
  public void getTargetsTargetsArePresent() {
    Project myProject = myProjectRule.getProject();
    ServiceUtil.registerServiceInstance(myProject, ProjectFacetManager.class, myProjectFacetManager, myDisposableRule.getDisposable());
    // Arrange
    var targets = Set.<Target>of(new QuickBootTarget(Keys.PIXEL_4_API_30));
    Mockito.when(myAction.getSelectedTargets(myProject)).thenReturn(Optional.of(targets));

    // Act
    var executionTargets = myProvider.getTargets(myProject, myConfiguration);

    // Assert
    assertEquals(List.of(new DeviceAndSnapshotComboBoxExecutionTarget(targets, myGetter)), executionTargets);
  }

  @Test
  public void getTargets() {
    Project myProject = myProjectRule.getProject();
    ServiceUtil.registerServiceInstance(myProject, ProjectFacetManager.class, myProjectFacetManager, myDisposableRule.getDisposable());

    // Act
    var executionTargets = myProvider.getTargets(myProject, myConfiguration);

    // Assert
    assertEquals(List.of(new DeviceAndSnapshotComboBoxExecutionTarget(Set.of(), myGetter)), executionTargets);
  }

  @SuppressWarnings("rawtypes")
  private static ProjectFacetManager newProjectFacetManager(List<FacetTypeId> facets) {
    return new ProjectFacetManager() {
      @Override
      public boolean hasFacets(@NotNull FacetTypeId<?> typeId) {
        return ContainerUtil.exists(facets, facetId -> facetId == typeId);
      }

      @Override
      public <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId, Module[] modules) {
        return null;
      }

      @Override
      public @NotNull <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId) {
        return null;
      }

      @Override
      public @NotNull List<Module> getModulesWithFacet(@NotNull FacetTypeId<?> typeId) {
        return null;
      }

      @Override
      public <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull FacetType<?, C> facetType) {
        return null;
      }

      @Override
      public <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull FacetType<?, C> facetType, @NotNull C configuration) {

      }
    };
  }
}
