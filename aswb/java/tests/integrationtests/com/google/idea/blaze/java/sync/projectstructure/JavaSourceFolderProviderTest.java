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
package com.google.idea.blaze.java.sync.projectstructure;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.section.Glob.GlobSet;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyJarTracker;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JavaSourceFolderProvider} */
@RunWith(JUnit4.class)
public class JavaSourceFolderProviderTest extends BlazeIntegrationTestCase {

  private Disposable thisClassDisposable; // disposed prior to calling parent class's @After methods

  @Before
  public void doSetup() {
    thisClassDisposable = Disposer.newDisposable();
  }

  @After
  public void doTearDown() {
    Disposer.dispose(thisClassDisposable);
  }

  @Test
  public void testInitializeSourceFolders() {
    ImmutableList<BlazeContentEntry> contentEntries =
        ImmutableList.of(
            BlazeContentEntry.builder("/src/workspace/java/apps")
                .addSource(
                    BlazeSourceDirectory.builder("/src/workspace/java/apps")
                        .setPackagePrefix("apps")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/src/workspace/java/apps/gen")
                        .setPackagePrefix("apps.gen")
                        .setGenerated(true)
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/src/workspace/java/apps/resources")
                        .setPackagePrefix("apps.resources")
                        .setResource(true)
                        .build())
                .build(),
            BlazeContentEntry.builder("/src/workspace/javatests/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/src/workspace/javatests/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());

    JavaSourceFolderProvider provider =
        new JavaSourceFolderProvider(
            new BlazeJavaSyncData(
                BlazeJavaImportResult.builder()
                    .setContentEntries(contentEntries)
                    .setLibraries(ImmutableMap.of())
                    .setBuildOutputJars(ImmutableList.of())
                    .setJavaSourceFiles(ImmutableSet.of())
                    .setSourceVersion(null)
                    .setEmptyJarTracker(EmptyJarTracker.builder().build())
                    .setPluginProcessorJars(ImmutableSet.of())
                    .build(),
                new GlobSet(ImmutableList.of())));

    VirtualFile root = workspace.createDirectory(new WorkspacePath("java/apps"));
    VirtualFile gen = workspace.createDirectory(new WorkspacePath("java/apps/gen"));
    VirtualFile res = workspace.createDirectory(new WorkspacePath("java/apps/resources"));

    ImmutableMap<File, SourceFolder> sourceFolders =
        provider.initializeSourceFolders(getContentEntry(root));
    assertThat(sourceFolders).hasSize(3);

    SourceFolder rootSource = sourceFolders.get(new File(root.getPath()));
    assertThat(rootSource.getPackagePrefix()).isEqualTo("apps");
    assertThat(JavaSourceFolderProvider.isGenerated(rootSource)).isFalse();
    assertThat(JavaSourceFolderProvider.isResource(rootSource)).isFalse();

    SourceFolder genSource = sourceFolders.get(new File(gen.getPath()));
    assertThat(genSource.getPackagePrefix()).isEqualTo("apps.gen");
    assertThat(JavaSourceFolderProvider.isGenerated(genSource)).isTrue();
    assertThat(JavaSourceFolderProvider.isResource(genSource)).isFalse();

    SourceFolder resSource = sourceFolders.get(new File(res.getPath()));
    assertThat(JavaSourceFolderProvider.isGenerated(resSource)).isFalse();
    assertThat(JavaSourceFolderProvider.isResource(resSource)).isTrue();

    VirtualFile testRoot = workspace.createDirectory(new WorkspacePath("javatests/apps/example"));
    sourceFolders = provider.initializeSourceFolders(getContentEntry(testRoot));

    assertThat(sourceFolders).hasSize(1);
    assertThat(sourceFolders.get(new File(testRoot.getPath())).getPackagePrefix())
        .isEqualTo("apps.example");
  }

  @Test
  public void testRelativePackagePrefix() {
    ImmutableList<BlazeContentEntry> contentEntries =
        ImmutableList.of(
            BlazeContentEntry.builder("/src/workspace/java/apps")
                .addSource(
                    BlazeSourceDirectory.builder("/src/workspace/java/apps")
                        .setPackagePrefix("apps")
                        .build())
                .build());

    JavaSourceFolderProvider provider =
        new JavaSourceFolderProvider(
            new BlazeJavaSyncData(
                BlazeJavaImportResult.builder()
                    .setContentEntries(contentEntries)
                    .setLibraries(ImmutableMap.of())
                    .setBuildOutputJars(ImmutableList.of())
                    .setJavaSourceFiles(ImmutableSet.of())
                    .setSourceVersion(null)
                    .setEmptyJarTracker(EmptyJarTracker.builder().build())
                    .setPluginProcessorJars(ImmutableSet.of())
                    .build(),
                new GlobSet(ImmutableList.of())));

    VirtualFile root = workspace.createDirectory(new WorkspacePath("java/apps"));
    ContentEntry contentEntry = getContentEntry(root);

    ImmutableMap<File, SourceFolder> sourceFolders = provider.initializeSourceFolders(contentEntry);
    assertThat(sourceFolders).hasSize(1);

    VirtualFile testRoot = workspace.createDirectory(new WorkspacePath("java/apps/tests/model"));

    SourceFolder testSourceChild =
        provider.setSourceFolderForLocation(
            contentEntry,
            sourceFolders.get(new File(root.getPath())),
            new File(testRoot.getPath()),
            true);
    assertThat(testSourceChild.isTestSource()).isTrue();
    assertThat(testSourceChild.getPackagePrefix()).isEqualTo("apps.tests.model");
  }

  @Test
  public void testRelativePackagePrefixWithoutParentPrefix() {
    ImmutableList<BlazeContentEntry> contentEntries =
        ImmutableList.of(
            BlazeContentEntry.builder("/src/workspace/java")
                .addSource(
                    BlazeSourceDirectory.builder("/src/workspace/java")
                        .setPackagePrefix("")
                        .build())
                .build());

    JavaSourceFolderProvider provider =
        new JavaSourceFolderProvider(
            new BlazeJavaSyncData(
                BlazeJavaImportResult.builder()
                    .setContentEntries(contentEntries)
                    .setLibraries(ImmutableMap.of())
                    .setBuildOutputJars(ImmutableList.of())
                    .setJavaSourceFiles(ImmutableSet.of())
                    .setSourceVersion(null)
                    .setEmptyJarTracker(EmptyJarTracker.builder().build())
                    .setPluginProcessorJars(ImmutableSet.of())
                    .build(),
                new GlobSet(ImmutableList.of())));

    VirtualFile root = workspace.createDirectory(new WorkspacePath("java"));
    ContentEntry contentEntry = getContentEntry(root);

    ImmutableMap<File, SourceFolder> sourceFolders = provider.initializeSourceFolders(contentEntry);
    assertThat(sourceFolders).hasSize(1);

    VirtualFile testRoot = workspace.createDirectory(new WorkspacePath("java/apps/tests"));

    SourceFolder testSourceChild =
        provider.setSourceFolderForLocation(
            contentEntry,
            sourceFolders.get(new File(root.getPath())),
            new File(testRoot.getPath()),
            true);
    assertThat(testSourceChild.isTestSource()).isTrue();
    assertThat(testSourceChild.getPackagePrefix()).isEqualTo("apps.tests");
  }

  private ContentEntry getContentEntry(VirtualFile root) {
    ContentEntry entry =
        ModuleRootManager.getInstance(testFixture.getModule())
            .getModifiableModel()
            .addContentEntry(root);
    if (entry instanceof Disposable) {
      // need to dispose the content entry and child disposables before the TestFixture is disposed
      Disposer.register(thisClassDisposable, (Disposable) entry);
    }
    return entry;
  }
}
