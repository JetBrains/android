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
package com.google.idea.blaze.base.projectview.parser;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the project view parser */
@RunWith(JUnit4.class)
public class ProjectViewParserTest extends BlazeTestCase {
  private ProjectViewParser projectViewParser;
  private BlazeContext context;
  private ErrorCollector errorCollector;
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));
  private MockProjectViewStorageManager projectViewStorageManager;

  static class MockProjectViewStorageManager extends ProjectViewStorageManager {
    Map<String, String> projectViewFiles = Maps.newHashMap();

    @Nullable
    @Override
    public String loadProjectView(@NotNull File projectViewFile) throws IOException {
      return projectViewFiles.get(projectViewFile.getPath());
    }

    @Override
    public void writeProjectView(@NotNull String projectViewText, @NotNull File projectViewFile)
        throws IOException {
      // no-op
    }

    void add(String name, String... text) {
      projectViewFiles.put(name, Joiner.on('\n').join(text));
    }
  }

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    context = BlazeContext.create();
    errorCollector = new ErrorCollector();
    context.addOutputSink(IssueOutput.class, errorCollector);
    projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewStorageManager = new MockProjectViewStorageManager();
    applicationServices.register(ProjectViewStorageManager.class, projectViewStorageManager);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
  }

  @Test
  public void testDirectoriesAndTargets() throws Exception {
    projectViewStorageManager.add(
        ".blazeproject",
        "directories:",
        "  java/com/google",
        "  java/com/google/android",
        "  -java/com/google/android/notme",
        "",
        "targets:",
        "  //java/com/google:all",
        "  //java/com/google/...:all",
        "  -//java/com/google:thistarget");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    assertThat(projectViewFile).isNotNull();
    assertThat(projectViewFile.projectViewFile).isEqualTo(new File(".blazeproject"));
    assertThat(projectViewSet.getProjectViewFiles()).containsExactly(projectViewFile);

    ProjectView projectView = projectViewFile.projectView;
    assertThat(projectView.getSectionsOfType(DirectorySection.KEY).get(0).items())
        .containsExactly(
            DirectoryEntry.include(new WorkspacePath("java/com/google")),
            DirectoryEntry.include(new WorkspacePath("java/com/google/android")),
            DirectoryEntry.exclude(new WorkspacePath("java/com/google/android/notme")));
    assertThat(projectView.getSectionsOfType(TargetSection.KEY).get(0).items())
        .containsExactly(
            TargetExpression.fromStringSafe("//java/com/google:all"),
            TargetExpression.fromStringSafe("//java/com/google/...:all"),
            TargetExpression.fromStringSafe("-//java/com/google:thistarget"));
  }

  @Test
  public void testRootDirectory() throws Exception {
    projectViewStorageManager.add(
        ".blazeproject",
        "directories:",
        "  .",
        "  -java/com/google/android/notme",
        "",
        "targets:",
        "  //java/com/google:all");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    assertThat(projectViewFile).isNotNull();
    assertThat(projectViewFile.projectViewFile).isEqualTo(new File(".blazeproject"));
    assertThat(projectViewSet.getProjectViewFiles()).containsExactly(projectViewFile);

    ProjectView projectView = projectViewFile.projectView;
    assertThat(projectView.getSectionsOfType(DirectorySection.KEY).get(0).items())
        .containsExactly(
            DirectoryEntry.include(new WorkspacePath("")),
            DirectoryEntry.exclude(new WorkspacePath("java/com/google/android/notme")));
    assertThat(projectView.getSectionsOfType(TargetSection.KEY).get(0).items())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google:all"));

    String text = ProjectViewParser.projectViewToString(projectView);
    assertThat(text)
        .isEqualTo(
            Joiner.on('\n')
                .join(
                    "directories:",
                    "  .",
                    "  -java/com/google/android/notme",
                    "",
                    "targets:",
                    "  //java/com/google:all"));
  }

  @Test
  public void testPrint() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/com/google/one")))
                    .add(DirectoryEntry.exclude(new WorkspacePath("java/com/google/two"))))
            .add(
                ListSection.builder(TargetSection.KEY)
                    .add(TargetExpression.fromStringSafe("//java/com/google:one"))
                    .add(TargetExpression.fromStringSafe("//java/com/google:two")))
            .add(
                ScalarSection.builder(ImportSection.KEY)
                    .set(new WorkspacePath("some/file.blazeproject")))
            .build();
    String text = ProjectViewParser.projectViewToString(projectView);
    assertThat(text)
        .isEqualTo(
            Joiner.on('\n')
                .join(
                    "directories:",
                    "  java/com/google/one",
                    "  -java/com/google/two",
                    "targets:",
                    "  //java/com/google:one",
                    "  //java/com/google:two",
                    "import some/file.blazeproject"));
  }

  @Test
  public void testImport() {
    projectViewStorageManager.add("/parent.blazeproject", "directories:", "  parent", "");
    projectViewStorageManager.add(
        ".blazeproject", "import parent.blazeproject", "directories:", "  child", "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    assertThat(projectViewSet.getProjectViewFiles()).hasSize(2);
    Collection<DirectoryEntry> entries = projectViewSet.listItems(DirectorySection.KEY);
    assertThat(entries)
        .containsExactly(
            DirectoryEntry.include(new WorkspacePath("parent")),
            DirectoryEntry.include(new WorkspacePath("child")));
  }

  @Test
  public void testMultipleImports() {
    projectViewStorageManager.add("/grandparent.blazeproject", "directories:", "  grandparent");
    projectViewStorageManager.add(
        "/mother.blazeproject", "import grandparent.blazeproject", "directories:", "  mother");
    projectViewStorageManager.add(
        "/father.blazeproject", "import grandparent.blazeproject", "directories:", "  father");
    projectViewStorageManager.add(
        "/child.blazeproject",
        "import mother.blazeproject",
        "directories:",
        "  child1",
        "import father.blazeproject",
        "directories:",
        "  child2");
    projectViewParser.parseProjectView(new File("/child.blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();

    // Ensures we don't parse grandfather twice
    assertThat(projectViewSet.getProjectViewFiles()).hasSize(4);
    Collection<DirectoryEntry> entries = projectViewSet.listItems(DirectorySection.KEY);

    // All imports' contributions appear before the children, no matter where they appear
    assertThat(entries)
        .containsExactly(
            DirectoryEntry.include(new WorkspacePath("grandparent")),
            DirectoryEntry.include(new WorkspacePath("mother")),
            DirectoryEntry.include(new WorkspacePath("father")),
            DirectoryEntry.include(new WorkspacePath("child1")),
            DirectoryEntry.include(new WorkspacePath("child2")))
        .inOrder();
  }

  @Test
  public void testTestSources() throws Exception {
    projectViewStorageManager.add(
        ".blazeproject",
        "test_sources:",
        "  javatests/com/google",
        "  javatests/com/google/android/*");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    Collection<Glob> entries = projectViewSet.listItems(TestSourceSection.KEY);
    assertThat(entries)
        .containsExactly(
            new Glob("javatests/com/google"), new Glob("javatests/com/google/android/*"))
        .inOrder();
  }

  @Test
  public void testMinimumIndentRequired() {
    projectViewStorageManager.add(
        ".blazeproject", "directories:", "  java/com/google", "java/com/google2", "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not parse: 'java/com/google2'");
  }

  @Test
  public void testIncorrectIndentationResultsInIssue() {
    projectViewStorageManager.add(
        ".blazeproject", "directories:", "  java/com/google", " java/com/google2", "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Invalid indentation on line: 'java/com/google2'");
  }

  @Test
  public void testCanParseWithMissingCarriageReturnAtEndOfSection() {
    projectViewStorageManager.add(".blazeproject", "directories:", "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    ProjectView projectView =
        projectViewParser.getResult().getTopLevelProjectViewFile().projectView;
    assertThat(projectView.getSectionsOfType(DirectorySection.KEY).get(0).items())
        .containsExactly(DirectoryEntry.include(new WorkspacePath("java/com/google")));
  }

  @Test
  public void testImportMissingFileResultsInIssue() {
    projectViewStorageManager.add(".blazeproject", "import parent.blazeproject");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not load project view file: '/parent.blazeproject'");
  }

  @Test
  public void testMissingSectionResultsInIssue() {
    projectViewStorageManager.add(".blazeproject", "nosuchsection:", "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not parse: 'nosuchsection:'");
  }

  @Test
  public void testMissingColonResultInIssue() {
    projectViewStorageManager.add(".blazeproject", "directories", "  java/com/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Could not parse: 'directories'");
  }

  @Test
  public void testEmptySectionYieldsError() {
    projectViewStorageManager.add(".blazeproject", "directories:", "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("Empty section: 'directories'");
  }

  @Test
  public void testTargetExpressionInGlobSectionResultsInIssue() {
    projectViewStorageManager.add(".blazeproject", "test_sources:", "  //javatests/com/google:one");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssues("test_sources is a list of file path globs, not target patterns.");
  }

  @Test
  public void testUnsupportedWildcardInGlobSectionResultsInIssue() {
    projectViewStorageManager.add(
        ".blazeproject", "test_sources:", "  javatests/com/google...", "  javatests/**/google");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertIssueContaining("wildcard is not supported in test_sources");
  }

  @Test
  public void testCommentsAreParsed() throws Exception {
    projectViewStorageManager.add(
        ".blazeproject",
        "# comment",
        "directories:",
        "  # another comment",
        "  java/com/google",
        "  # comment",
        "  java/com/google/android",
        "");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    ProjectView projectView = projectViewFile.projectView;
    assertThat(projectView.getSectionsOfType(TextBlockSection.KEY).get(0).getTextBlock())
        .isEqualTo(new TextBlock(ImmutableList.of("# comment")));
    assertThat(projectView.getSectionsOfType(DirectorySection.KEY).get(0).items())
        .containsExactly(
            DirectoryEntry.include(new WorkspacePath("java/com/google")),
            DirectoryEntry.include(new WorkspacePath("java/com/google/android")));
  }

  @Test
  public void testMultipleSections() throws Exception {
    projectViewStorageManager.add(
        ".blazeproject",
        "directories:",
        "  java/com/google",
        "directories:",
        "  java/com/google2",
        "",
        "workspace_type: java",
        "workspace_type: android");
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    assertThat(projectViewSet.listItems(DirectorySection.KEY))
        .containsExactly(
            DirectoryEntry.include(new WorkspacePath("java/com/google")),
            DirectoryEntry.include(new WorkspacePath("java/com/google2")));
    assertThat(projectViewSet.listScalarItems(WorkspaceTypeSection.KEY))
        .containsExactly(WorkspaceType.JAVA, WorkspaceType.ANDROID);
  }

  @Test
  public void testListParserAcceptsWhitespace() throws Exception {
    String text =
        Joiner.on('\n')
            .join(
                "directories:",
                "  dir0",
                "  ",
                "",
                "  dir1",
                "  ",
                "  ",
                "# comment",
                "  dir2",
                "",
                "  # commented out dir",
                "  ",
                "# comment",
                "# comment");
    projectViewStorageManager.add(".blazeproject", text);
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    assert projectViewFile != null;
    ProjectView projectView = projectViewFile.projectView;

    assertThat(projectView)
        .isEqualTo(
            ProjectView.builder()
                .add(
                    ListSection.builder(DirectorySection.KEY)
                        .add(DirectoryEntry.include(new WorkspacePath("dir0")))
                        .add(TextBlock.of("  "))
                        .add(TextBlock.of(""))
                        .add(DirectoryEntry.include(new WorkspacePath("dir1")))
                        .add(TextBlock.of("  ", "  "))
                        .add(TextBlock.of("# comment"))
                        .add(DirectoryEntry.include(new WorkspacePath("dir2")))
                        .add(TextBlock.of(""))
                        .add(TextBlock.of("  # commented out dir"))
                        .add(TextBlock.of("  ")))
                .add(TextBlockSection.of(TextBlock.of("# comment", "# comment")))
                .build());

    String outputString = ProjectViewParser.projectViewToString(projectView);
    assertThat(outputString).isEqualTo(text);
  }

  @Test
  public void testCommentsAndWhitespacePreserved() throws Exception {
    String text =
        Joiner.on('\n')
            .join(
                "",
                "# comment",
                "  ",
                "  ",
                "directories:",
                "  # another comment",
                "  java/com/google",
                "  # comment",
                "#unindented comment",
                "  java/com/google/android",
                "",
                "  # needlessly indented comment",
                "",
                "directories:",
                "  java/com/google",
                "  # trailing comment",
                "directories:",
                "  java/com/google");
    projectViewStorageManager.add(".blazeproject", text);
    projectViewParser.parseProjectView(new File(".blazeproject"));
    errorCollector.assertNoIssues();

    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewSet.ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    ProjectView projectView = projectViewFile.projectView;
    String outputString = ProjectViewParser.projectViewToString(projectView);
    assertThat(outputString).isEqualTo(text);
  }
}
