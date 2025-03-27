/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import static com.google.common.truth.PathSubject.paths;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;
import com.google.idea.blaze.base.TestData;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncProject;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.testing.EdtRule;
import com.google.idea.testing.java.LightJavaCodeInsightFixtureTestCase4Concrete;
import com.google.protobuf.TextFormat;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.rules.TempDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ClassFileJavaSourceFinderTest extends LightJavaCodeInsightFixtureTestCase4Concrete {

  @Rule public final TempDirectory tempDir = new TempDirectory();
  @Rule public final EdtRule edtRule = new EdtRule();
  @Rule public final Expect expect = Expect.create();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TestData testData = new TestData();
  @Mock public QuerySyncManager querySyncManager;
  @Mock public ArtifactTracker<?> artifactTracker;
  private final SnapshotHolder snapshotHolder = new SnapshotHolder();
  public Path libraryArtifactAbsolutePath;
  public Path javaDepsAbsolutePath;
  public Path libraryArtifactPath;
  public Path projectAbsolutePath;

  @Before
  public void initArtifactTracker() {
    // We can't use when(...).thenReturn(...) here due to generic arg in getArtifactTracker:
    doReturn(artifactTracker).when(querySyncManager).getArtifactTracker();
    when(querySyncManager.isProjectLoaded()).thenReturn(true);
  }

  @Before
  public void initJar() throws IOException {
    String localRelativeArtifactPath = "com/test/libtest.jar";
    final var testDataJar = testData.get(localRelativeArtifactPath);
    projectAbsolutePath = tempDir.newDirectory("project").toPath();
    javaDepsAbsolutePath = projectAbsolutePath.resolve(ArtifactDirectories.JAVADEPS.relativePath());
    libraryArtifactAbsolutePath = javaDepsAbsolutePath.resolve("blaze-out/k8/bin/" + localRelativeArtifactPath);
    libraryArtifactPath = javaDepsAbsolutePath.relativize(libraryArtifactAbsolutePath);
    Files.createDirectories(libraryArtifactAbsolutePath.getParent());
    Files.copy(Path.of(testDataJar), libraryArtifactAbsolutePath);
  }

  @Before
  public void initBlazeProject() {
    QuerySyncProject mockProject = mock(QuerySyncProject.class);
    when(mockProject.getSnapshotHolder()).thenReturn(snapshotHolder);
    when(querySyncManager.getLoadedProject()).thenReturn(Optional.of(mockProject));
  }

  @Test
  public void project_not_loaded() {
    VirtualFile classFile =
        JarFileSystem.getInstance()
            .findFileByPath(libraryArtifactAbsolutePath.toString() + "!/com/test/Test.class");
    ClsFileImpl psiClassFile = (ClsFileImpl) getFixture().getPsiManager().findFile(classFile);

    when(querySyncManager.isProjectLoaded()).thenReturn(false);
    ClassFileJavaSourceFinder djsf =
        new ClassFileJavaSourceFinder(
            getFixture().getProject(), querySyncManager, Path.of("/"), Path.of("/"), psiClassFile);
    expect.that(djsf.findSourceFile()).isNull();
    verify(querySyncManager, never()).getArtifactTracker();
  }

  @Test
  public void unknown_jarfile() {
    VirtualFile classFile =
        JarFileSystem.getInstance()
            .findFileByPath(libraryArtifactAbsolutePath.toString() + "!/com/test/Test.class");
    ClsFileImpl psiClassFile = (ClsFileImpl) getFixture().getPsiManager().findFile(classFile);
    ClassFileJavaSourceFinder djsf =
        new ClassFileJavaSourceFinder(
          getFixture().getProject(), querySyncManager, testData.root, Path.of("/"), psiClassFile);
    expect.that(djsf.findSourceFile()).isNull();
  }

  @Test
  public void source_match() throws Exception {
    VirtualFile classFile =
        JarFileSystem.getInstance()
            .findFileByPath(libraryArtifactAbsolutePath.toString() + "!/com/test/Test.class");
    ClsFileImpl psiClassFile = (ClsFileImpl) getFixture().getPsiManager().findFile(classFile);
    ClassFileJavaSourceFinder djsf =
        new ClassFileJavaSourceFinder(
          getFixture().getProject(),
          querySyncManager,
          testData.root,
          projectAbsolutePath,
          psiClassFile);
    ArtifactTracker.State artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        ImmutableList.of(
          JavaArtifactInfo.empty(Label.of("//com/test:test")).toBuilder()
            .setSources(
              ImmutableSet.of(
                Path.of("com/test/Test.java"), Path.of("com/test/AnotherClass.java")))
            .setJars(ImmutableList.of(
              BuildArtifact.create(
                "digest-libtest.jar",
                Path.of("blaze-out/k8/bin/com/test/libtest.jar"), Label.of("//com/test:test"))))
            .build()));
    snapshotHolder.setCurrent(
        mock(Context.class),
        QuerySyncProjectSnapshot.EMPTY.toBuilder()
            .artifactState(artifactState)
            .build());

    PsiElement navElement = djsf.findSourceFile();
    assertThat(navElement).isNotNull();
    expect
        .about(paths())
        .that(navElement.getContainingFile().getVirtualFile().toNioPath())
        .isEqualTo(testData.getPath("com/test/Test.java"));
  }

  @Test
  public void source_name_duplicate_match() throws Exception {
    VirtualFile classFile =
      JarFileSystem.getInstance()
        .findFileByPath(libraryArtifactAbsolutePath.toString() + "!/com/test/Test.class");
    ClsFileImpl psiClassFile = (ClsFileImpl)getFixture().getPsiManager().findFile(classFile);
    ClassFileJavaSourceFinder djsf =
      new ClassFileJavaSourceFinder(
        getFixture().getProject(),
        querySyncManager,
        testData.root,
        projectAbsolutePath,
        psiClassFile);
    ArtifactTracker.State artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        ImmutableList.of(
          JavaArtifactInfo.empty(Label.of("//com/test:test")).toBuilder()
            .setSources(
              ImmutableSet.of(
                Path.of("com/test/Test.java"),
                Path.of("com/test/AnotherClass.java"),
                Path.of("com/test2/Test.java")))
            .setJars(ImmutableList.of(
              BuildArtifact.create(
                "digest-libtest.jar",
                Path.of("blaze-out/k8/bin/com/test/libtest.jar"), Label.of("//com/test:test"))))
            .build()));
    snapshotHolder.setCurrent(
      mock(Context.class),
        QuerySyncProjectSnapshot.EMPTY.toBuilder()
            .artifactState(artifactState)
            .build());

    PsiElement navElement = djsf.findSourceFile();
    assertThat(navElement).isNotNull();
    expect
        .about(paths())
        .that(navElement.getContainingFile().getVirtualFile().toNioPath())
        .isEqualTo(testData.getPath("com/test/Test.java"));
  }

  @Test
  public void source_name_duplicate_no_match() {
    VirtualFile classFile =
        JarFileSystem.getInstance()
            .findFileByPath(libraryArtifactAbsolutePath.toString() + "!/com/test/Test.class");
    ClsFileImpl psiClassFile = (ClsFileImpl) getFixture().getPsiManager().findFile(classFile);
    ClassFileJavaSourceFinder djsf =
        new ClassFileJavaSourceFinder(
          getFixture().getProject(), querySyncManager, testData.root, Path.of("/"), psiClassFile);
    PsiElement navElement = djsf.findSourceFile();
    assertThat(navElement).isNull();
  }
}
