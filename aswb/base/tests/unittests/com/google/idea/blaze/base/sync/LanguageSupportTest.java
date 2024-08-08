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
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link LanguageSupport} */
@RunWith(JUnit4.class)
public class LanguageSupportTest extends BlazeTestCase {
  private ErrorCollector errorCollector = new ErrorCollector();
  private BlazeContext context;
  private ExtensionPointImpl<BlazeSyncPlugin> syncPlugins;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    syncPlugins = registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);

    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  @Test
  public void testSimpleCase() {
    syncPlugins.registerExtension(
        new BlazeSyncPlugin() {
          @Override
          public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
            return ImmutableSet.of(LanguageClass.C);
          }

          @Override
          public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
            return ImmutableList.of(WorkspaceType.C);
          }

          @Override
          public WorkspaceType getDefaultWorkspaceType() {
            return WorkspaceType.C;
          }
        });

    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.C))
                    .build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    errorCollector.assertNoIssues();
    assertThat(workspaceLanguageSettings)
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.C, ImmutableSet.of(LanguageClass.C, LanguageClass.GENERIC)));
  }

  @Test
  public void testFailWithUnsupportedWorkspaceType() {
    syncPlugins.registerExtension(
        new BlazeSyncPlugin() {
          @Override
          public WorkspaceType getDefaultWorkspaceType() {
            return WorkspaceType.JAVA;
          }
        });
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.C))
                    .build())
            .build();
    WorkspaceLanguageSettings settings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    LanguageSupport.validateLanguageSettings(context, settings);
    errorCollector.assertIssues("Workspace type 'c' is not supported by this plugin");
  }

  @Test
  public void testFailWithUnsupportedLanguageType() {
    syncPlugins.registerExtension(
        new BlazeSyncPlugin() {
          @Override
          public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
            return ImmutableSet.of(LanguageClass.C);
          }

          @Override
          public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
            return ImmutableList.of(WorkspaceType.C);
          }

          @Override
          public WorkspaceType getDefaultWorkspaceType() {
            return WorkspaceType.C;
          }
        });

    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.C))
                    .add(
                        ListSection.builder(AdditionalLanguagesSection.KEY)
                            .add(LanguageClass.PYTHON))
                    .build())
            .build();
    WorkspaceLanguageSettings settings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    LanguageSupport.validateLanguageSettings(context, settings);
    errorCollector.assertIssues("Language 'python' is not supported by this plugin");
  }

  /** Tests that we ask for java and android when the workspace type is android. */
  @Test
  public void testWorkspaceTypeImpliesLanguages() {
    syncPlugins.registerExtension(
        new BlazeSyncPlugin() {
          @Override
          public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
            return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA, LanguageClass.C);
          }

          @Override
          public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
            return ImmutableList.of(WorkspaceType.ANDROID);
          }

          @Override
          public WorkspaceType getDefaultWorkspaceType() {
            return WorkspaceType.ANDROID;
          }
        });

    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.ANDROID))
                    .build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    assertThat(workspaceLanguageSettings)
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.ANDROID,
                ImmutableSet.of(LanguageClass.JAVA, LanguageClass.ANDROID, LanguageClass.GENERIC)));
  }
}
