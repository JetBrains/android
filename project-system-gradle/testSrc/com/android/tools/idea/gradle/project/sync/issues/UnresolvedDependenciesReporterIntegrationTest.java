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
package com.android.tools.idea.gradle.project.sync.issues;

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.openPreparedTestProject;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.testFramework.UsefulTestCase.assertNotEmpty;
import static com.intellij.testFramework.UsefulTestCase.assertSize;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertSame;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.util.containers.ContainerUtil;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
@RunsInEdt
public class UnresolvedDependenciesReporterIntegrationTest {
  private UnresolvedDependenciesReporter myReporter = new UnresolvedDependenciesReporter();

  @Rule
  public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

  @Test
  public void testGetSupportedIssueType() {
    assertEquals(TYPE_UNRESOLVED_DEPENDENCY, myReporter.getSupportedIssueType());
  }

  @Test
  public void testReportWithRegularJavaLibrary() {

    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.google.guava:guava:19.0",
        "",
        null
      );

      Module appModule = TestModuleUtil.findAppModule(project);
      VirtualFile buildFile = getGradleBuildFile(appModule);
      final var messages = myReporter.report(syncIssue, appModule, buildFile);

      assertSize(1, messages);

      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertThat(message.getMessage())
        .containsMatch(Pattern.compile("Failed to resolve: com.google.guava:guava:19.0.*" +
                                       "Affected Modules:", Pattern.DOTALL));

      assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
      OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
      assertEquals(buildFile, navigatable.getFile());

      VirtualFile file = ((OpenFileDescriptor)message.getNavigatable()).getFile();
      assertSame(buildFile, file);

      assertEquals(
        ImmutableList.of(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  @Test
  public void testReportWithConstraintLayout() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");

      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.android.support.constraint:constraint-layout:+",
        "",
        null
      );

      final var messages = myReporter.report(syncIssue, appModule, getGradleBuildFile(appModule));

      assertSize(1, messages);

      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertThat(message.getMessage())
        .containsMatch(Pattern.compile("Failed to resolve: com.android.support.constraint:constraint-layout:\\+.*" +
                                       "Affected Modules:", Pattern.DOTALL));

      final var quickFixes = messages.get(0).getQuickFixes();
      assertNotEmpty(quickFixes);

      assertEquals(
        ImmutableList.of(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  @Test
  public void testReportWithAppCompat() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");

      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.android.support:appcompat-v7:24.1.1",
        "",
        null
      );

      final var messages = myReporter.report(syncIssue, appModule, getGradleBuildFile(appModule));

      assertSize(1, messages);

      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertThat(message.getMessage())
        .containsMatch(Pattern.compile("Failed to resolve: com.android.support:appcompat-v7:24.1.1.*" +
                                       "Affected Modules:", Pattern.DOTALL));

      final var quickFixes = message.getQuickFixes();
      int expectedSize = (IdeInfo.getInstance().isAndroidStudio() ? 2 : 1) + 1 /* affected modules */;
      assertThat(quickFixes).hasSize(expectedSize);

      var quickFix = quickFixes.get(0);
      assertThat(quickFix).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
      AddGoogleMavenRepositoryHyperlink addQuickFix = (AddGoogleMavenRepositoryHyperlink)quickFix;
      // Confirm that the repository will be added to project build file (b/68657672)
      assertSize(1, addQuickFix.getBuildFiles());

      if (IdeInfo.getInstance().isAndroidStudio()) {
        quickFix = quickFixes.get(1);
        assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
      }

      assertEquals(
        ImmutableList.of(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  @Test
  public void testReportWithAppCompatAndGoogle() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");

      // Add Google repository
      GradleBuildModel buildModel = ProjectBuildModel.get(project).getModuleBuildModel(appModule);
      buildModel.repositories().addGoogleMavenRepository();
      runWriteCommandAction(project, buildModel::applyChanges);

      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.android.support:appcompat-v7:24.1.1",
        "",
        null
      );

      final var messages = myReporter.report(syncIssue, appModule, null);

      assertSize(1, messages);


      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertEquals("Failed to resolve: com.android.support:appcompat-v7:24.1.1\n" +
                   "<a href=\"open.dependency.in.project.structure\">Show in Project Structure dialog</a>\n" +
                   "Affected Modules: app",
                   message.getMessage());

      final var quickFixes = message.getQuickFixes();
      int expectedSize = (IdeInfo.getInstance().isAndroidStudio() ? 1 : 0) + 1 /* affected modules */;
      assertThat(quickFixes).hasSize(expectedSize);

      if (IdeInfo.getInstance().isAndroidStudio()) {
        assertThat(quickFixes.get(0)).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
      }

      assertEquals(
        ImmutableList.of(
          maybeAddShowDependencyInProjectStructureHyperLink(
            GradleSyncIssue
              .newBuilder()
              .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY))
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  /**
   * Verify that an {@link AddGoogleMavenRepositoryHyperlink} is generated when the project is not initialized
   *
   */
  @Test
  public void testReportNotInitialized() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");
      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.android.support:appcompat-v7:24.1.1",
        "",
        null
      );

      myReporter.assumeProjectNotInitialized(true);
      final var messages = myReporter.report(syncIssue, appModule, null);
      myReporter.assumeProjectNotInitialized(false);

      assertSize(1, messages);

      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertEquals("Failed to resolve: com.android.support:appcompat-v7:24.1.1\n" +
                   "<a href=\"add.google.maven.repository\">Add Google Maven repository and sync project</a>\n" +
                   "<a href=\"open.dependency.in.project.structure\">Show in Project Structure dialog</a>\n" +
                   "Affected Modules: app",
                   message.getMessage());

      final var quickFixes = message.getQuickFixes();
      int expectedSize = (IdeInfo.getInstance().isAndroidStudio() ? 2 : 1) + 1 /* affected modules */;
      assertThat(quickFixes).hasSize(expectedSize);

      var quickFix = quickFixes.get(0);
      assertThat(quickFix).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
      AddGoogleMavenRepositoryHyperlink addQuickFix = (AddGoogleMavenRepositoryHyperlink)quickFix;
      // Confirm that the build file was found
      assertSize(1, addQuickFix.getBuildFiles());

      if (IdeInfo.getInstance().isAndroidStudio()) {
        quickFix = quickFixes.get(1);
        assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
      }

      assertEquals(
        ImmutableList.of(
          maybeAddShowDependencyInProjectStructureHyperLink(
            GradleSyncIssue
              .newBuilder()
              .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
              .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK))
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  @Test
  public void testReportWithPlayServices() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");

      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.google.android.gms:play-services:9.4.0",
        "",
        null
      );

      final var messages = myReporter.report(syncIssue, appModule, getGradleBuildFile(appModule));

      assertSize(1, messages);

      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertThat(message.getMessage())
        .containsMatch(Pattern.compile("Failed to resolve: com.google.android.gms:play-services:9.4.0.*" +
                                       "Affected Modules:", Pattern.DOTALL));

      final var quickFixes = message.getQuickFixes();
      int expectedSize = (IdeInfo.getInstance().isAndroidStudio() ? 2 : 1) + 1 /* affected modules */;
      assertThat(quickFixes).hasSize(expectedSize);

      var quickFix = quickFixes.get(0);
      assertThat(quickFix).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);

      if (IdeInfo.getInstance().isAndroidStudio()) {
        quickFix = quickFixes.get(1);
        assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
      }

      assertEquals(
        ImmutableList.of(
          maybeAddShowDependencyInProjectStructureHyperLink(
            GradleSyncIssue
              .newBuilder()
              .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
              .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
          )
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  @Test
  public void testDeduplicateAcrossModules() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.DEPENDENT_MODULES);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");
      Module libModule = gradleModule(project, ":lib");

      List<IdeSyncIssue> issues = ContainerUtil.map(ImmutableList.of(1, 2), (i) -> new IdeSyncIssue() {
        @Override
        public int hashCode() {
          return 7;
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
          if (obj.hashCode() == hashCode()) {
            return true;
          }
          return false;
        }

        @Override
        public int getSeverity() {
          return SEVERITY_ERROR;
        }

        @Override
        public int getType() {
          return TYPE_UNRESOLVED_DEPENDENCY;
        }

        @Nullable
        @Override
        public String getData() {
          return "com.google.android.gms.play-services:9.4.0";
        }

        @NonNull
        @Override
        public String getMessage() {
          return "";
        }

        @Nullable
        @Override
        public List<String> getMultiLineMessage() {
          return null;
        }
      });

      IdentityHashMap<IdeSyncIssue, Module> moduleMap = new IdentityHashMap<>();
      moduleMap.put(issues.get(0), appModule);
      moduleMap.put(issues.get(1), libModule);
      final var messages = myReporter.reportAll(
        issues,
        moduleMap,
        ImmutableMap.of(appModule, getGradleBuildFile(appModule), libModule, getGradleBuildFile(libModule))
      );

      assertSize(1, messages);

      final var message = messages.get(0);
      assertEquals("Unresolved dependencies", message.getGroup());
      assertThat(message.getMessage())
        .containsMatch(
          Pattern.compile("Failed to resolve: com.google.android.gms.play-services:.*Affected Modules:",
                          Pattern.DOTALL));

      assertEquals(
        ImmutableList.of(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  @Test
  public void testGetModuleLink() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");
      VirtualFile appFile = getGradleBuildFile(appModule);

      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "com.google.guava:guava:19.0",
        "",
        null
      );

      List<IdeSyncIssue> syncIssues = ImmutableList.of(syncIssue);
      final var link = myReporter.createModuleLink(project, appModule, syncIssues, appFile);
      assertThat(link.getLineNumber()).isEqualTo(-1);
      assertThat(link.getFilePath()).isEqualTo(appFile.getPath());
    });
  }

  @Test
  public void testAndroidXGoogleHyperlink() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module appModule = gradleModule(project, ":app");
      VirtualFile appFile = getGradleBuildFile(appModule);

      final var syncIssue = new IdeSyncIssueImpl(
        IdeSyncIssue.SEVERITY_WARNING,
        TYPE_UNRESOLVED_DEPENDENCY,
        "androidx.room:room-compiler:2.0.0-alpha1",
        "",
        null
      );

      final var messages = myReporter.report(syncIssue, appModule, appFile);

      assertSize(1, messages);

      final var links = messages.get(0).getQuickFixes();
      boolean studio = IdeInfo.getInstance().isAndroidStudio();
      assertSize((studio ? 2 : 1) + 1 /* affected modules */, links);
      assertThat(links.get(0)).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
      if (studio) {
        assertThat(links.get(1)).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
      }

      assertEquals(
        ImmutableList.of(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
            .build()),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
    });
  }

  private static GradleSyncIssue.Builder maybeAddShowDependencyInProjectStructureHyperLink(GradleSyncIssue.Builder builder) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      builder.addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK);
    }
    return builder;
  }
}
