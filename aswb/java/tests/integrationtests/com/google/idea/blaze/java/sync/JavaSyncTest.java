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
package com.google.idea.blaze.java.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Java-specific sync integration tests. */
@RunWith(JUnit4.class)
public class JavaSyncTest extends BlazeSyncIntegrationTestCase {

  @Test
  public void testJavaClassesPresentInClassPath() throws Exception {
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib");

    workspace.createFile(
        new WorkspacePath("java/com/google/ClassWithUniqueName1.java"),
        "package com.google;",
        "public class ClassWithUniqueName1 {}");

    workspace.createFile(
        new WorkspacePath("java/com/google/ClassWithUniqueName2.java"),
        "package com.google;",
        "public class ClassWithUniqueName2 {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("java/com/google/ClassWithUniqueName1.java"))
                    .addSource(sourceRoot("java/com/google/ClassWithUniqueName2.java"))
                    .setJavaInfo(JavaIdeInfo.builder()))
            .build();

    setTargetMap(targetMap);

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap().map()).isEqualTo(targetMap.map());
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType())
        .isEqualTo(WorkspaceType.JAVA);

    BlazeJavaSyncData javaSyncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    List<BlazeContentEntry> contentEntries = javaSyncData.getImportResult().contentEntries;
    assertThat(contentEntries).hasSize(1);

    BlazeContentEntry contentEntry = contentEntries.get(0);
    assertThat(contentEntry.contentRoot.getPath())
        .isEqualTo(workspaceRoot.fileForPath(new WorkspacePath("java/com/google")).getPath());
    assertThat(contentEntry.sources).hasSize(1);

    BlazeSourceDirectory sourceDir = contentEntry.sources.get(0);
    assertThat(sourceDir.getPackagePrefix()).isEqualTo("com.google");
    assertThat(sourceDir.getDirectory().getPath())
        .isEqualTo(workspaceRoot.fileForPath(new WorkspacePath("java/com/google")).getPath());
  }

  @Test
  public void testSimpleSync() throws Exception {
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib");

    workspace.createFile(
        new WorkspacePath("java/com/google/Source.java"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(
        new WorkspacePath("java/com/google/Other.java"),
        "package com.google;",
        "public class Other {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("java/com/google/Source.java"))
                    .addSource(sourceRoot("java/com/google/Other.java")))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap().map()).isEqualTo(targetMap.map());
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType())
        .isEqualTo(WorkspaceType.JAVA);
  }

  @Test
  public void testSimpleSyncLogging() throws Exception {
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib");

    workspace.createFile(
        new WorkspacePath("java/com/google/Source.java"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(
        new WorkspacePath("java/com/google/Other.java"),
        "package com.google;",
        "public class Other {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("java/com/google/Source.java"))
                    .addSource(sourceRoot("java/com/google/Other.java")))
            .build();

    setTargetMap(targetMap);

    // Run a full sync before an incremental one, otherwise incremental may be treated as full.
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
    errorCollector.assertNoIssues();
    List<SyncStats> syncStatsList = getSyncStats();
    assertThat(syncStatsList).hasSize(1);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();
    syncStatsList = getSyncStats();
    assertThat(syncStatsList).hasSize(2);

    SyncStats syncStats = syncStatsList.get(0);
    assertThat(syncStats.workspaceType()).isEqualTo(WorkspaceType.JAVA);
    assertThat(syncStats.syncMode()).isEqualTo(SyncMode.FULL);
    assertThat(syncStats.syncResult()).isEqualTo(SyncResult.SUCCESS);
    assertThat(syncStats.syncBinaryType()).isEqualTo(BuildBinaryType.BAZEL);
    assertThat(syncStats.timedEvents()).isNotEmpty();
    assertThat(syncStats.buildPhaseStats()).hasSize(1);
    assertThat(syncStats.buildPhaseStats().get(0).targets())
        .containsExactly(TargetExpression.fromString("//java/com/google:lib"));

    SyncStats secondSyncStats = syncStatsList.get(1);
    assertThat(secondSyncStats.syncMode()).isEqualTo(SyncMode.INCREMENTAL);
  }

  @Test
  public void testSimpleTestSourcesIdentified() {
    setProjectView(
        "directories:",
        "  java/com/google",
        "  javatests/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib",
        "test_sources:",
        "  javatests/*");

    VirtualFile javaRoot = workspace.createDirectory(new WorkspacePath("java/com/google"));
    VirtualFile javatestsRoot =
        workspace.createDirectory(new WorkspacePath("javatests/com/google"));

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ImmutableList<ContentEntry> contentEntries = getWorkspaceContentEntries();
    assertThat(contentEntries).hasSize(2);

    assertThat(findContentEntry(javaRoot)).isNotNull();
    assertThat(findContentEntry(javaRoot).getSourceFolders()).hasLength(1);
    assertThat(findContentEntry(javaRoot).getSourceFolders()[0].isTestSource()).isFalse();

    assertThat(findContentEntry(javatestsRoot)).isNotNull();
    assertThat(findContentEntry(javatestsRoot).getSourceFolders()).hasLength(1);
    assertThat(findContentEntry(javatestsRoot).getSourceFolders()[0].isTestSource()).isTrue();
  }

  @Test
  public void testNestedTestSourcesAreAdded() {
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib",
        "test_sources:",
        "  java/com/google/tests/*",
        "  java/com/google/moretests");

    workspace.createDirectory(new WorkspacePath("java/com/google"));

    VirtualFile testFile =
        workspace.createFile(new WorkspacePath("java/com/google/tests/ExampleTest.java"));
    VirtualFile moreTestsFile =
        workspace.createFile(
            new WorkspacePath("java/com/google/moretests/AnotherExampleTest.java"));

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ImmutableList<ContentEntry> contentEntries = getWorkspaceContentEntries();
    assertThat(contentEntries).hasSize(1);

    SourceFolder testRoot = findSourceFolder(contentEntries.get(0), testFile.getParent());
    SourceFolder moreTestsRoot = findSourceFolder(contentEntries.get(0), moreTestsFile.getParent());

    assertThat(testRoot).isNotNull();
    assertThat(testRoot.isTestSource()).isTrue();
    assertThat(testRoot.getPackagePrefix()).isEqualTo("com.google.tests");

    assertThat(moreTestsRoot).isNotNull();
    assertThat(moreTestsRoot.isTestSource()).isTrue();
    assertThat(moreTestsRoot.getPackagePrefix()).isEqualTo("com.google.moretests");
  }

  @Test
  public void testTestSourcesUpdateCorrectlyOnSubsequentSync() {
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib",
        "test_sources:",
        "  java/com/google/tests/*");

    VirtualFile root = workspace.createDirectory(new WorkspacePath("java/com/google"));

    VirtualFile testsDir = workspace.createDirectory(new WorkspacePath("java/com/google/tests"));
    VirtualFile moreTestsDir =
        workspace.createDirectory(new WorkspacePath("java/com/google/moretests"));

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ContentEntry contentEntry = findContentEntry(root);
    assertThat(findSourceFolder(contentEntry, testsDir).isTestSource()).isTrue();
    assertThat(findSourceFolder(contentEntry, moreTestsDir)).isNull();

    // unmark one test source, mark another.
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib",
        "test_sources:",
        "  java/com/google/moretests/*");

    runBlazeSync(syncParams);

    contentEntry = findContentEntry(root);
    assertThat(findSourceFolder(contentEntry, testsDir)).isNull();
    assertThat(findSourceFolder(contentEntry, moreTestsDir).isTestSource()).isTrue();
  }

  @Test
  public void testExistingPackagePrefixRetainedForTestSources() {
    setProjectView(
        "directories:",
        "  java/com/google",
        "  javatests/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib",
        "test_sources:",
        "  javatests/*");

    workspace.createDirectory(new WorkspacePath("java/com/google"));
    VirtualFile javatestsRoot =
        workspace.createDirectory(new WorkspacePath("javatests/com/google"));

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ContentEntry testRoot = findContentEntry(javatestsRoot);
    SourceFolder testRootSource = findSourceFolder(testRoot, javatestsRoot);
    assertThat(testRootSource.isTestSource()).isTrue();
    assertThat(testRootSource.getPackagePrefix()).isEqualTo("com.google");
  }

  @Test
  public void testTestSourceRelativePackagePrefixCalculation() {
    setProjectView(
        "directories:",
        "  java/com/google",
        "workspace_type: java",
        "targets:",
        "  //java/com/google:lib",
        "test_sources:",
        "  java/com/google/tests/*");

    VirtualFile javatestsRoot =
        workspace.createDirectory(new WorkspacePath("java/com/google/tests"));

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    ContentEntry root = findContentEntry(javatestsRoot.getParent());
    SourceFolder rootSource = findSourceFolder(root, javatestsRoot.getParent());
    assertThat(rootSource.isTestSource()).isFalse();
    assertThat(rootSource.getPackagePrefix()).isEqualTo("com.google");

    SourceFolder childTestSource = findSourceFolder(root, javatestsRoot);
    assertThat(childTestSource.isTestSource()).isTrue();
    assertThat(childTestSource.getPackagePrefix()).isEqualTo("com.google.tests");
  }

  @Nullable
  private static SourceFolder findSourceFolder(ContentEntry entry, VirtualFile file) {
    for (SourceFolder sourceFolder : entry.getSourceFolders()) {
      if (file.equals(sourceFolder.getFile())) {
        return sourceFolder;
      }
    }
    return null;
  }
}
