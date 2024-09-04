/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/** Test cases for {@link BlazeSyncManager}. */
@RunWith(JUnit4.class)
public class BlazeSyncManagerTest extends BlazeTestCase {
  @Spy BlazeSyncManager manager;
  @Captor ArgumentCaptor<BlazeSyncParams> paramsCaptor;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    manager = new BlazeSyncManager(project);
    MockitoAnnotations.initMocks(this);
    applicationServices.register(BlazeUserSettings.class, new BlazeUserSettings());
    applicationServices.register(ExperimentService.class, new MockExperimentService());

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    projectServices.register(ProjectViewManager.class, new MockProjectViewManager());

    doNothing().when(manager).requestProjectSync(any());
    projectServices.register(BlazeSyncManager.class, manager);
    assertThat(BlazeSyncManager.getInstance(project)).isSameAs(manager);
  }

  @Override
  protected BuildSystemProvider createBuildSystemProvider() {
    return new BazelBuildSystemProvider();
  }

  @Test
  public void testFullProjectSync() {
    manager.fullProjectSync(/* reason= */ "test");
    verify(manager).requestProjectSync(paramsCaptor.capture());
    BlazeSyncParams params = paramsCaptor.getValue();
    assertThat(params).isNotNull();
    assertThat(params.title()).isEqualTo("Full Sync");
    assertThat(params.syncMode()).isEqualTo(SyncMode.FULL);
    assertThat(params.backgroundSync()).isFalse();
    assertThat(params.addProjectViewTargets()).isTrue();
    assertThat(params.addWorkingSet())
        .isEqualTo(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet());
    assertThat(params.targetExpressions()).isEmpty();
  }

  @Test
  public void testIncrementalProjectSync() {
    manager.incrementalProjectSync(/* reason= */ "test");
    verify(manager).requestProjectSync(paramsCaptor.capture());
    BlazeSyncParams params = paramsCaptor.getValue();
    assertThat(params).isNotNull();
    assertThat(params.title()).isEqualTo("Sync");
    assertThat(params.syncMode()).isEqualTo(SyncMode.INCREMENTAL);
    assertThat(params.backgroundSync()).isFalse();
    assertThat(params.addProjectViewTargets()).isTrue();
    assertThat(params.addWorkingSet())
        .isEqualTo(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet());
    assertThat(params.targetExpressions()).isEmpty();
  }

  @Test
  public void testPartialSync() {
    List<TargetExpression> targets =
        ImmutableList.of(
            TargetExpression.fromStringSafe("//foo:bar"),
            TargetExpression.fromStringSafe("//foo:baz"));
    manager.partialSync(targets, /* reason= */ "test");
    verify(manager).requestProjectSync(paramsCaptor.capture());
    BlazeSyncParams params = paramsCaptor.getValue();
    assertThat(params).isNotNull();
    assertThat(params.title()).isEqualTo("Partial Sync");
    assertThat(params.syncMode()).isEqualTo(SyncMode.PARTIAL);
    assertThat(params.backgroundSync()).isFalse();
    assertThat(params.addProjectViewTargets()).isFalse();
    assertThat(params.addWorkingSet()).isFalse();
    assertThat(params.targetExpressions()).containsExactlyElementsIn(targets);
  }

  @Test
  public void testWorkingSetSync() {
    manager.workingSetSync(/* reason= */ "test");
    verify(manager).requestProjectSync(paramsCaptor.capture());
    BlazeSyncParams params = paramsCaptor.getValue();
    assertThat(params).isNotNull();
    assertThat(params.title()).isEqualTo("Sync Working Set");
    assertThat(params.syncMode()).isEqualTo(SyncMode.PARTIAL);
    assertThat(params.backgroundSync()).isFalse();
    assertThat(params.addProjectViewTargets()).isFalse();
    assertThat(params.addWorkingSet()).isTrue();
    assertThat(params.targetExpressions()).isEmpty();
  }

  @Test
  public void testShouldForceFullSync_noNewLanguageAdded() {
    BlazeContext context = BlazeContext.create();
    BlazeProjectData projectData = getMockProjectData();
    SyncProjectState projectState =
        getMockProjectStateWithLanguages(ImmutableSet.of(LanguageClass.JAVA));
    assertThat(manager.shouldForceFullSync(projectData, projectState, SyncMode.NO_BUILD, context))
        .isFalse();
    assertThat(
            manager.shouldForceFullSync(projectData, projectState, SyncMode.INCREMENTAL, context))
        .isFalse();
    context.close();
  }

  @Test
  public void testShouldForceFullSync_newLanguageAdded() {
    BlazeContext context = BlazeContext.create();
    BlazeProjectData projectData = getMockProjectData();
    SyncProjectState projectState =
        getMockProjectStateWithLanguages(ImmutableSet.of(LanguageClass.JAVA, LanguageClass.KOTLIN));
    assertThat(manager.shouldForceFullSync(projectData, projectState, SyncMode.NO_BUILD, context))
        .isFalse();
    assertThat(
            manager.shouldForceFullSync(projectData, projectState, SyncMode.INCREMENTAL, context))
        .isTrue();
    context.close();
  }

  @Test
  public void testShouldForceFullSync_additionalLanguageRemoved() {
    BlazeContext context = BlazeContext.create();
    BlazeProjectData projectData = getMockProjectData();
    SyncProjectState projectState = getMockProjectStateWithLanguages(ImmutableSet.of());
    assertThat(manager.shouldForceFullSync(projectData, projectState, SyncMode.NO_BUILD, context))
        .isFalse();
    assertThat(
            manager.shouldForceFullSync(projectData, projectState, SyncMode.INCREMENTAL, context))
        .isFalse();
    context.close();
  }

  private SyncProjectState getMockProjectStateWithLanguages(
      ImmutableSet<LanguageClass> activeLanguages) {
    return SyncProjectState.builder()
        .setLanguageSettings(new WorkspaceLanguageSettings(WorkspaceType.ANDROID, activeLanguages))
        .setProjectViewSet(MockProjectViewManager.getInstance(project).getProjectViewSet())
        .setBlazeVersionData(BlazeVersionData.builder().build())
        .setWorkspacePathResolver(
            new WorkspacePathResolverImpl(WorkspaceRoot.fromProjectSafe(project)))
        .build();
  }

  private BlazeProjectData getMockProjectData() {
    return MockBlazeProjectDataBuilder.builder()
        .setWorkspaceLanguageSettings(
            new WorkspaceLanguageSettings(
                WorkspaceType.ANDROID, Sets.immutableEnumSet(LanguageClass.JAVA)))
        .build();
  }

  private static class MockProjectViewManager extends ProjectViewManager {
    @Override
    public ProjectViewSet getProjectViewSet() {
      return ProjectViewSet.builder().build();
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(BlazeContext context) {
      return ProjectViewSet.builder().build();
    }

    @Override
    public ProjectViewSet reloadProjectView(
        BlazeContext context, WorkspacePathResolver workspacePathResolver) {
      return ProjectViewSet.EMPTY;
    }
  }
}
