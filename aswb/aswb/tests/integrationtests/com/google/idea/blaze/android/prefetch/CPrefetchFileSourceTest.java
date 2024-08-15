/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.prefetch;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.cpp.CPrefetchFileSource;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CPrefetchFileSource}. */
@RunWith(JUnit4.class)
public class CPrefetchFileSourceTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  @Test
  public void testSourceFilesInProjectIgnored() {
    ProjectViewSet projectViewSet =
        parseProjectView(
            "directories:",
            "  java/com/google",
            "targets:",
            "  //java/com/google:lib",
            "additional_languages:",
            "  c",
            "android_sdk_platform: android-25");

    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                TargetMapBuilder.builder()
                    .addTarget(
                        TargetIdeInfo.builder()
                            .setBuildFile(sourceRoot("java/com/google/BUILD"))
                            .setLabel("//java/com/google:lib")
                            .setKind("cc_library")
                            .addSource(sourceRoot("java/com/google/native.cc"))
                            .addSource(sourceRoot("java/com/google/native.h")))
                    .build())
            .setWorkspaceLanguageSettings(
                LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))
            .build();

    Set<File> filesToPrefetch = new HashSet<>();
    new CPrefetchFileSource()
        .addFilesToPrefetch(
            getProject(),
            projectViewSet,
            getImportRoots(projectViewSet),
            projectData,
            filesToPrefetch);

    assertThat(filesToPrefetch).isEmpty();
  }

  @Test
  public void testCppHeaderFilesOutsideProjectIncluded() {
    ProjectViewSet projectViewSet =
        parseProjectView(
            "directories:",
            "  java/com/google",
            "targets:",
            "  //java/com/google:lib",
            "additional_languages:",
            "  c",
            "android_sdk_platform: android-25");

    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                TargetMapBuilder.builder()
                    .addTarget(
                        TargetIdeInfo.builder()
                            .setBuildFile(sourceRoot("third_party/library/BUILD"))
                            .setLabel("//third_party/library:dep")
                            .setKind("cc_library")
                            .setCInfo(
                                CIdeInfo.builder()
                                    .addSource(sourceRoot("third_party/library/main.cc"))
                                    .addHeader(sourceRoot("third_party/library/dep.h"))
                                    .addHeader(sourceRoot("third_party/library/other.h"))
                                    .addTextualHeader(sourceRoot("third_party/library/textual.h"))))
                    .build())
            .setWorkspaceLanguageSettings(
                LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))
            .build();

    Set<File> filesToPrefetch = new HashSet<>();
    new CPrefetchFileSource()
        .addFilesToPrefetch(
            getProject(),
            projectViewSet,
            getImportRoots(projectViewSet),
            projectData,
            filesToPrefetch);

    assertThat(filesToPrefetch)
        .containsExactly(
            workspaceFile("third_party/library/dep.h"),
            workspaceFile("third_party/library/other.h"),
            workspaceFile("third_party/library/textual.h"));
  }

  @Test
  public void testJavaSourceFilesIgnored() {
    ProjectViewSet projectViewSet =
        parseProjectView(
            "directories:",
            "  java/com/google",
            "targets:",
            "  //java/com/google:lib",
            "additional_languages:",
            "  c",
            "android_sdk_platform: android-25");

    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                TargetMapBuilder.builder()
                    .addTarget(
                        TargetIdeInfo.builder()
                            .setBuildFile(sourceRoot("third_party/library/BUILD"))
                            .setLabel("//third_party/library:lib")
                            .setKind("java_library")
                            .addSource(sourceRoot("third_party/library/Library.java")))
                    .build())
            .setWorkspaceLanguageSettings(
                LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))
            .build();

    Set<File> filesToPrefetch = new HashSet<>();
    new CPrefetchFileSource()
        .addFilesToPrefetch(
            getProject(),
            projectViewSet,
            getImportRoots(projectViewSet),
            projectData,
            filesToPrefetch);

    assertThat(filesToPrefetch).isEmpty();
  }

  private ProjectViewSet parseProjectView(String... contents) {
    ProjectViewParser projectViewParser =
        new ProjectViewParser(BlazeContext.create(), new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));
    return projectViewParser.getResult();
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private File workspaceFile(String workspacePath) {
    return workspaceRoot.fileForPath(new WorkspacePath(workspacePath));
  }

  private ImportRoots getImportRoots(ProjectViewSet projectViewSet) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(getProject()).getImportSettings();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    return ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
        .add(projectViewSet)
        .build();
  }
}
