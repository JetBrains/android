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
package com.google.idea.blaze.base.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.projectview.section.sections.BuildConfigSection;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ExcludeTargetSection;
import com.google.idea.blaze.base.projectview.section.sections.ExcludedSourceSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportTargetOutputSection;
import com.google.idea.blaze.base.projectview.section.sections.RunConfigurationsSection;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.projectview.section.sections.SyncFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetShardSizeSection;
import com.google.idea.blaze.base.projectview.section.sections.TestFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for project view sets */
@RunWith(JUnit4.class)
public class ProjectViewSetTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
  }

  @Test
  public void testProjectViewSetSerializable() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(DirectoryEntry.include(new WorkspacePath("test"))))
                    .add(ScalarSection.builder(UseQuerySyncSection.KEY).set(true))
                    .add(
                        ListSection.builder(TargetSection.KEY)
                            .add(TargetExpression.fromStringSafe("//test:all")))
                    .add(ScalarSection.builder(ImportSection.KEY).set(new WorkspacePath("test")))
                    .add(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
                    .add(ListSection.builder(ExcludedSourceSection.KEY).add(new Glob("*.java")))
                    .add(ListSection.builder(BuildFlagsSection.KEY).add("--android_sdk=abcd"))
                    .add(ListSection.builder(SyncFlagsSection.KEY).add("--config=arm"))
                    .add(ListSection.builder(TestFlagsSection.KEY).add("--cache_test_results=no"))
                    .add(
                        ListSection.builder(ImportTargetOutputSection.KEY)
                            .add(Label.create("//test:test")))
                    .add(
                        ListSection.builder(ExcludeTargetSection.KEY)
                            .add(Label.create("//test:test")))
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.JAVA))
                    .add(
                        ListSection.builder(AdditionalLanguagesSection.KEY).add(LanguageClass.JAVA))
                    .add(TextBlockSection.of(TextBlock.newLine()))
                    .add(
                        ListSection.builder(RunConfigurationsSection.KEY)
                            .add(new WorkspacePath("test")))
                    .add(ScalarSection.builder(AutomaticallyDeriveTargetsSection.KEY).set(false))
                    .add(ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(false))
                    .add(ScalarSection.builder(TargetShardSizeSection.KEY).set(500))
                    .add(
                        ScalarSection.builder(BazelBinarySection.KEY)
                            .set(new File("/bazel/path/override")))
                    .add(
                        ScalarSection.builder(BuildConfigSection.KEY)
                            .set(new WorkspacePath("test")))
                    .build())
            .build();

    // Assert we have all sections
    assertThat(projectViewSet.getTopLevelProjectViewFile()).isNotNull();
    ProjectView projectView = projectViewSet.getTopLevelProjectViewFile().projectView;
    for (SectionParser parser : Sections.getParsers()) {
      ImmutableList<Section<?>> sections = projectView.getSections();
      assertThat(
              sections.stream().anyMatch(section -> section.isSectionType(parser.getSectionKey())))
          .isTrue();
    }

    TestUtils.assertIsSerializable(projectViewSet);
  }
}
