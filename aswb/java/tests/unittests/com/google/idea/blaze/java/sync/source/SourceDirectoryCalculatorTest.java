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
package com.google.idea.blaze.java.sync.source;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.JavaSourcePackage;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PackageManifest;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.MockInputStreamProvider;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.MockRemoteArtifactPrefetcher;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link SourceDirectoryCalculator}. */
@RunWith(JUnit4.class)
public class SourceDirectoryCalculatorTest extends BlazeTestCase {

  private static final ImmutableMap<TargetKey, ArtifactLocation> NO_MANIFESTS = ImmutableMap.of();
  private static final Label LABEL = Label.create("//fake:label");

  private MockInputStreamProvider mockInputStreamProvider;
  private SourceDirectoryCalculator sourceDirectoryCalculator;

  private final BlazeContext context = BlazeContext.create();
  private final ErrorCollector issues = new ErrorCollector();
  private MockExperimentService experimentService;

  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));
  private final ArtifactLocationDecoder decoder =
      new MockArtifactLocationDecoder() {
        @Override
        public File decode(ArtifactLocation artifactLocation) {
          return new File("/root", artifactLocation.getRelativePath());
        }
      };

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    mockInputStreamProvider = new MockInputStreamProvider();
    applicationServices.register(InputStreamProvider.class, mockInputStreamProvider);
    applicationServices.register(JavaSourcePackageReader.class, new JavaSourcePackageReader());
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(FileOperationProvider.class, new MockFileOperationProvider());

    context.addOutputSink(IssueOutput.class, issues);
    sourceDirectoryCalculator = new SourceDirectoryCalculator();

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);

    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);

    applicationServices.register(PrefetchService.class, new MockPrefetchService());

    applicationServices.register(
        RemoteArtifactPrefetcher.class, new MockRemoteArtifactPrefetcher());

    registerExtensionPoint(JavaLikeLanguage.EP_NAME, JavaLikeLanguage.class)
        .registerExtension(new JavaLikeLanguage.Java());
  }

  @Test
  public void testGuessPackagePathForCommonRootDirectory() {
    List<SourceArtifact> sourceArtifacts = ImmutableList.of();
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google/app")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google/app")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/app")
                        .setPackagePrefix("com.google.app")
                        .build())
                .build());
  }

  @Test
  public void testRootDirectoryWithoutNestedSourcesIsMarkedAsSourceRoot() {
    List<SourceArtifact> sourceArtifacts = ImmutableList.of();
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("some/innocuous/path")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/some/innocuous/path")
                .addSource(
                    BlazeSourceDirectory.builder("/root/some/innocuous/path")
                        .setPackagePrefix("some.innocuous.path")
                        .build())
                .build());
  }

  @Test
  public void testCalculatesPackageForSimpleCase() {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
    issues.assertNoIssues();
  }

  @Test
  public void testSourceRootUnderExcludedDirectoryIsIgnored() {
    mockInputStreamProvider.addFile(
        "/root/included/src/com/google/Bla.java", "package com.google;\n public class Bla {}");
    mockInputStreamProvider.addFile(
        "/root/excluded/src/com/google/Foo.java", "package com.google;\n public class Foo {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("included/src/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("excluded/src/com/google/Foo.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("")),
                ImmutableList.of(new WorkspacePath("excluded"))),
            sourceArtifacts,
            NO_MANIFESTS);
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root/included/src").build())
                .build());
    issues.assertNoIssues();
  }

  @Test
  public void testHandlesSourceAtProjectRoot() {
    mockInputStreamProvider.addFile("/root/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder().setRelativePath("Bla.java").setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(ImmutableList.of(new WorkspacePath("")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(
                    BlazeSourceDirectory.builder("/root").setPackagePrefix("com.google").build())
                .build());
    issues.assertNoIssues();
  }

  @Test
  public void testSourcesToSourceDirectories_testReturnsTest() {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_multipleMatchingPackagesAreMerged() {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.subpackage;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testMultipleDirectoriesAreMergedWithDirectoryRootAsWorkspaceRoot() {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/run/Run.java",
            "package com.google.idea.blaze.plugin.run;\n public class run {}")
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/sync/Sync.java",
            "package com.google.idea.blaze.plugin.sync;\n public class Sync {}")
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/Plugin.java",
            "package com.google.idea.blaze.plugin;\n public class Plugin {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/run/Run.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/sync/Sync.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/Plugin.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(ImmutableList.of(new WorkspacePath("")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root/java").setPackagePrefix("").build())
                .build());
  }

  @Test
  public void testIncorrectPackageInMiddleOfTreeCausesMergePointHigherUp() {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/run/Run.java",
            "package com.google.idea.blaze.plugin.run;\n public class run {}")
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/sync/Sync.java",
            "package com.google.idea.blaze.plugin.sync;\n public class Sync {}")
        .addFile(
            "/root/java/com/google/idea/blaze/Incorrect.java",
            "package com.google.idea.blaze.incorrect;\n public class Incorrect {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/run/Run.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/sync/Sync.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/Incorrect.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(ImmutableList.of(new WorkspacePath("")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root/java").setPackagePrefix("").build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/idea/blaze")
                        .setPackagePrefix("com.google.idea.blaze.incorrect")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/idea/blaze/plugin")
                        .setPackagePrefix("com.google.idea.blaze.plugin")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_multipleNonMatchingPackagesAreNotMerged() {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.different;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_childMatchesPathButParentDoesnt() {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.facebook;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.subpackage;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.facebook")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.subpackage")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_orderIsIrrelevant() {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.different;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_packagesMatchPath() {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_packagesDoNotMatchPath() {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.facebook")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_completePackagePathMismatch() {
    mockInputStreamProvider.addFile(
        "/root/java/com/org/foo/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/org/foo/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/org")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/org")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/org/foo")
                        .setPackagePrefix("com.facebook")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_generatedSourcesOutsideOfModuleGeneratesNoIssue() {
    mockInputStreamProvider.addFile(
        "/root/java/com/facebook/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/facebook/Bla.java")
                        .setIsSource(false))
                .build());
    sourceDirectoryCalculator.calculateContentEntries(
        project,
        context,
        workspaceRoot,
        decoder,
        buildImportRoots(
            ImmutableList.of(new WorkspacePath("java/com/google/my")), ImmutableList.of()),
        sourceArtifacts,
        NO_MANIFESTS);
    issues.assertNoIssues();
  }

  @Test
  public void testSourcesToSourceDirectories_missingPackageDeclaration() {
    mockInputStreamProvider.addFile("/root/java/com/google/Bla.java", "public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    sourceDirectoryCalculator.calculateContentEntries(
        project,
        context,
        workspaceRoot,
        decoder,
        buildImportRoots(
            ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
        sourceArtifacts,
        NO_MANIFESTS);

    issues.assertIssueContaining("No package name string found");
  }

  @Test
  public void testCompetingPackageDeclarationPicksMajority() {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Foo.java", "package com.google;\n public class Foo {}")
        .addFile(
            "/root/java/com/google/Bla.java", "package com.google.different;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/Bla2.java",
            "package com.google.different;\n public class Bla2 {}")
        .addFile(
            "/root/java/com/google/Bla3.java",
            "package com.google.different;\n public class Bla3 {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla2.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla3.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Foo.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  @Test
  public void testCompetingPackageDeclarationWithEqualCountsPicksDefault() {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/Bla.java", "package com.google.different;\n public class Bla {}")
        .addFile("/root/java/com/google/Foo.java", "package com.google;\n public class Foo {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Foo.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_packagesMatchPathButNotAtRoot() {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/Bla.java", "package com.google.different;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.subpackage;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/subsubpackage/Bla.java",
            "package com.google.subpackage.subsubpackage;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/subsubpackage/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google.different")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.subpackage")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_multipleSubdirectoriesAreNotMerged() {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/package0/Bla.java",
            "package com.google.packagewrong0;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/package1/Bla.java",
            "package com.google.packagewrong1;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/package0/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/package1/Bla.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/package0")
                        .setPackagePrefix("com.google.packagewrong0")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/package1")
                        .setPackagePrefix("com.google.packagewrong1")
                        .build())
                .build());
  }

  @Test
  public void testLowestDirectoryIsPrioritised() {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/android/chimera/internal/Preconditions.java",
            "package com.google.android.chimera.container.internal;\n "
                + "public class Preconditions {}")
        .addFile(
            "/root/java/com/google/android/chimera/container/FileApkUtils.java",
            "package com.google.android.chimera.container;\n public class FileApkUtils {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath(
                            "java/com/google/android/chimera/internal/Preconditions.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath(
                            "java/com/google/android/chimera/container/FileApkUtils.java")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google/android")), ImmutableList.of()),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google/android")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/android")
                        .setPackagePrefix("com.google.android")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/android/chimera/internal")
                        .setPackagePrefix("com.google.android.chimera.container.internal")
                        .build())
                .build());
  }

  @Test
  public void testNewFormatManifest() {
    setNewFormatPackageManifest(
        "/root/blaze-out/k8-opt/genfiles/java/com/test.manifest",
        ImmutableList.of(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("java/com/google/Bla.java")
                .setIsSource(true)
                .build()),
        ImmutableList.of("com.google"));
    ImmutableMap<TargetKey, ArtifactLocation> manifests =
        ImmutableMap.of(
            TargetKey.forPlainTarget(LABEL),
            ArtifactLocation.builder()
                .setRelativePath("java/com/test.manifest")
                .setRootExecutionPathFragment("blaze-out/k8-opt/genfiles")
                .setIsSource(false)
                .build());
    Map<TargetKey, Map<ArtifactLocation, String>> manifestMap =
        readPackageManifestFiles(manifests, getDecoder());

    assertThat(manifestMap.get(TargetKey.forPlainTarget(LABEL)))
        .containsEntry(
            ArtifactLocation.builder()
                .setRelativePath("java/com/google/Bla.java")
                .setIsSource(true)
                .build(),
            "com.google");
  }

  @Test
  public void testManifestSingleFile() {
    setPackageManifest(
        "/root/blaze-out/k8-opt/genfiles/java/com/test.manifest",
        ImmutableList.of("java/com/google/Bla.java"),
        ImmutableList.of("com.google"));
    ImmutableMap<TargetKey, ArtifactLocation> manifests =
        ImmutableMap.of(
            TargetKey.forPlainTarget(LABEL),
            ArtifactLocation.builder()
                .setRelativePath("java/com/test.manifest")
                .setRootExecutionPathFragment("blaze-out/k8-opt/genfiles")
                .setIsSource(false)
                .build());
    Map<TargetKey, Map<ArtifactLocation, String>> manifestMap =
        readPackageManifestFiles(manifests, getDecoder());

    assertThat(manifestMap.get(TargetKey.forPlainTarget(LABEL)))
        .containsEntry(
            ArtifactLocation.builder()
                .setRelativePath("java/com/google/Bla.java")
                .setIsSource(true)
                .build(),
            "com.google");
  }

  @Test
  public void testManifestRepeatedSources() {
    setPackageManifest(
        "/root/blaze-out/k8-opt/genfiles/java/com/test.manifest",
        ImmutableList.of("java/com/google/Bla.java", "java/com/google/Foo.java"),
        ImmutableList.of("com.google", "com.google.subpackage"));
    setPackageManifest(
        "/root/blaze-out/k8-opt/genfiles/java/com/test2.manifest",
        ImmutableList.of("java/com/google/Bla.java", "java/com/google/other/Temp.java"),
        ImmutableList.of("com.google", "com.google.other"));
    ImmutableMap<TargetKey, ArtifactLocation> manifests =
        ImmutableMap.<TargetKey, ArtifactLocation>builder()
            .put(
                TargetKey.forPlainTarget(Label.create("//a:a")),
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test.manifest")
                    .setRootExecutionPathFragment("blaze-out/k8-opt/genfiles")
                    .setIsSource(false)
                    .build())
            .put(
                TargetKey.forPlainTarget(Label.create("//b:b")),
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test2.manifest")
                    .setRootExecutionPathFragment("blaze-out/k8-opt/genfiles")
                    .setIsSource(false)
                    .build())
            .build();
    Map<TargetKey, Map<ArtifactLocation, String>> manifestMap =
        readPackageManifestFiles(manifests, getDecoder());

    assertThat(manifestMap).hasSize(2);

    assertThat(manifestMap.get(TargetKey.forPlainTarget(Label.create("//a:a"))))
        .containsEntry(
            ArtifactLocation.builder()
                .setRelativePath("java/com/google/Bla.java")
                .setIsSource(true)
                .build(),
            "com.google");
    assertThat(manifestMap.get(TargetKey.forPlainTarget(Label.create("//a:a"))))
        .containsEntry(
            ArtifactLocation.builder()
                .setRelativePath("java/com/google/Foo.java")
                .setIsSource(true)
                .build(),
            "com.google.subpackage");
    assertThat(manifestMap.get(TargetKey.forPlainTarget(Label.create("//b:b"))))
        .containsEntry(
            ArtifactLocation.builder()
                .setRelativePath("java/com/google/other/Temp.java")
                .setIsSource(true)
                .build(),
            "com.google.other");
  }

  @Test
  public void testManifestMissingSourcesFallback() {
    setPackageManifest(
        "/root/blaze-out/k8-opt/genfiles/java/com/test.manifest",
        ImmutableList.of("java/com/google/Bla.java", "java/com/google/Foo.java"),
        ImmutableList.of("com.google", "com.google"));

    mockInputStreamProvider.addFile(
        "/root/java/com/google/subpackage/Bla.java",
        "package com.google.different;\n public class Bla {}");

    ImmutableMap<TargetKey, ArtifactLocation> manifests =
        ImmutableMap.of(
            TargetKey.forPlainTarget(LABEL),
            ArtifactLocation.builder()
                .setRelativePath("java/com/test.manifest")
                .setRootExecutionPathFragment("blaze-out/k8-opt/genfiles")
                .setIsSource(false)
                .build());

    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Foo.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(TargetKey.forPlainTarget(LABEL))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setIsSource(true))
                .build());

    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            getDecoder(),
            buildImportRoots(
                ImmutableList.of(new WorkspacePath("java/com/google")), ImmutableList.of()),
            sourceArtifacts,
            manifests);

    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  private ImportRoots buildImportRoots(
      ImmutableList<WorkspacePath> roots, ImmutableList<WorkspacePath> excluded) {
    ImportRoots.Builder builder = ImportRoots.builder(workspaceRoot, BuildSystemName.Blaze);
    roots.forEach(path -> builder.add(DirectoryEntry.include(path)));
    excluded.forEach(path -> builder.add(DirectoryEntry.exclude(path)));
    return builder.build();
  }

  private void setPackageManifest(
      String manifestPath, List<String> sourceRelativePaths, List<String> packages) {
    PackageManifest.Builder manifest = PackageManifest.newBuilder();
    for (int i = 0; i < sourceRelativePaths.size(); i++) {
      String sourceRelativePath = sourceRelativePaths.get(i);
      Common.ArtifactLocation source =
          Common.ArtifactLocation.newBuilder()
              .setRelativePath(sourceRelativePath)
              .setIsSource(true)
              .build();
      manifest.addSources(
          JavaSourcePackage.newBuilder()
              .setArtifactLocation(source)
              .setPackageString(packages.get(i)));
    }
    mockInputStreamProvider.addFile(manifestPath, manifest.build().toByteArray());
  }

  private void setNewFormatPackageManifest(
      String manifestPath, List<Common.ArtifactLocation> sources, List<String> packages) {
    PackageManifest.Builder manifest = PackageManifest.newBuilder();
    for (int i = 0; i < sources.size(); i++) {
      manifest.addSources(
          JavaSourcePackage.newBuilder()
              .setArtifactLocation(sources.get(i))
              .setPackageString(packages.get(i)));
    }
    mockInputStreamProvider.addFile(manifestPath, manifest.build().toByteArray());
  }

  private static ArtifactLocationDecoder getDecoder() {
    File root = new File("/root");
    WorkspaceRoot workspaceRoot = new WorkspaceRoot(root);
    BlazeInfo roots =
        BlazeInfo.createMockBlazeInfo(
            "/",
            "/root",
            "/root/out/crosstool/bin",
            "/root/out/crosstool/gen",
            "/root/out/crosstool/testlogs");
    return new ArtifactLocationDecoderImpl(
        roots, new WorkspacePathResolverImpl(workspaceRoot), RemoteOutputArtifacts.EMPTY);
  }

  private Map<TargetKey, Map<ArtifactLocation, String>> readPackageManifestFiles(
      Map<TargetKey, ArtifactLocation> manifests, ArtifactLocationDecoder decoder) {
    return PackageManifestReader.getInstance()
        .readPackageManifestFiles(
            project, context, decoder, manifests, MoreExecutors.newDirectExecutorService());
  }

  static class MockFileOperationProvider extends FileOperationProvider {
    @Override
    public long getFileModifiedTime(File file) {
      return 1;
    }
  }
}
