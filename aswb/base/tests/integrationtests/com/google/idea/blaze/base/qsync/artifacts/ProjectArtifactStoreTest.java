package com.google.idea.blaze.base.qsync.artifacts;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.qsync.FileRefresher;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.artifacts.MockArtifactCache;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import com.google.idea.blaze.qsync.project.ProjectProto.BuildArtifact;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ProjectArtifactStoreTest {

  @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock public FileRefresher fileRefresher;
  @Mock GeneratedSourcesStripper generatedSourcesStripper;

  private Path projectPath;
  private Path workspacePath;
  private Path cacheDir;

  private ProjectArtifactStore projectArtifactStore;

  @Before
  public void createPaths() throws IOException {
    projectPath = tmpDir.getRoot().toPath().resolve("project");
    workspacePath = tmpDir.getRoot().toPath().resolve("workspace");
    cacheDir = tmpDir.getRoot().toPath().resolve("cache");
    Files.createDirectories(projectPath);
    Files.createDirectories(workspacePath);
    Files.createDirectories(cacheDir);

    projectArtifactStore =
        new ProjectArtifactStore(
            projectPath,
            workspacePath,
            new MockArtifactCache(cacheDir),
            fileRefresher,
            generatedSourcesStripper);
  }

  @Test
  public void new_dirs_created() throws IOException, BuildException {

    projectArtifactStore.update(
        new NoopContext(),
        QuerySyncProjectSnapshot.EMPTY.toBuilder()
            .project(
                Project.newBuilder()
                    .setArtifactDirectories(
                        ArtifactDirectories.newBuilder()
                            .putDirectories(
                                "artifactdir",
                                ArtifactDirectoryContents.newBuilder()
                                    .putContents(
                                        "file1.txt",
                                        ProjectArtifact.newBuilder()
                                            .setTransform(ArtifactTransform.COPY)
                                            .setBuildArtifact(
                                                BuildArtifact.newBuilder().setDigest("abcd"))
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build());

    assertThat(Files.exists(projectPath.resolve("artifactdir"))).isTrue();
    assertThat(Files.isDirectory(projectPath.resolve("artifactdir"))).isTrue();
    assertThat(Files.exists(projectPath.resolve("artifactdir.contents"))).isTrue();
  }

  @Test
  public void old_dir_deleted() throws IOException, BuildException {
    new_dirs_created();

    projectArtifactStore.update(new NoopContext(), QuerySyncProjectSnapshot.EMPTY);

    assertThat(Files.exists(projectPath.resolve("artifactdir"))).isFalse();
    assertThat(Files.exists(projectPath.resolve("artifactdir.contents"))).isFalse();
  }
}
