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
package com.google.idea.blaze.dart;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeDartSyncPlugin} */
@RunWith(JUnit4.class)
public class BlazeDartSyncPluginTest extends BlazeTestCase {

  private final ErrorCollector errorCollector = new ErrorCollector();
  private BlazeContext context;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    ExtensionPointImpl<BlazeSyncPlugin> ep =
        registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
    ep.registerExtension(new BlazeDartSyncPlugin());
    // add java, because we need at least one other BlazeSyncPlugin to verify the additional
    // language support
    ep.registerExtension(
        new BlazeSyncPlugin() {
          @Override
          public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
            return ImmutableList.of(WorkspaceType.JAVA);
          }

          @Override
          public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
            return ImmutableSet.of(LanguageClass.JAVA);
          }

          @Override
          public WorkspaceType getDefaultWorkspaceType() {
            return WorkspaceType.JAVA;
          }
        });

    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  @Test
  public void testDartAdditionalLanguageSupported() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.JAVA))
                    .add(
                        ListSection.builder(AdditionalLanguagesSection.KEY).add(LanguageClass.DART))
                    .build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    errorCollector.assertNoIssues();
    assertThat(workspaceLanguageSettings)
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.DART, LanguageClass.GENERIC, LanguageClass.JAVA)));
  }

  @Test
  public void testDartWorkspaceType() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.DART))
                    .build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    errorCollector.assertNoIssues();
    assertThat(workspaceLanguageSettings)
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.DART, ImmutableSet.of(LanguageClass.DART, LanguageClass.GENERIC)));
  }
}
