/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.libraries;

import static com.android.SdkConstants.FN_LINT_JAR;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.libraries.UnpackedAars.FileCacheAdapter;
import com.google.idea.blaze.android.sync.BlazeAndroidSyncPlugin;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.libraries.BlazeLibrarySorter;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.util.io.FileUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.idea.blaze.android.libraries.UnpackedAars}. */
@RunWith(JUnit4.class)
public class UnpackedAarsTest extends BlazeTestCase {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private WorkspaceRoot workspaceRoot;
  private WritingOutputSink writingOutputSink;
  private BlazeContext context;
  private ArtifactLocationDecoder localArtifactLocationDecoder;
  private ArtifactLocationDecoder remoteArtifactLocationDecoder;
  private static final String STRINGS_XML_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<resources>"
          + "    <string name=\"appString\">Hello from app</string>"
          + "</resources>";

  private static final String COLORS_XML_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<resources>"
          + "    <color name=\"quantum_black_100\">#000000</color>"
          + "</resources>";

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    writingOutputSink = new WritingOutputSink();
    context = BlazeContext.create();
    context.addOutputSink(PrintOutput.class, writingOutputSink);
    workspaceRoot = new WorkspaceRoot(folder.getRoot());
    localArtifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };

    remoteArtifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }

          @Override
          public BlazeArtifact resolveOutput(ArtifactLocation artifact) {
            if (!artifact.isSource()) {
              File file = new File(workspaceRoot.directory(), artifact.getRelativePath());
              // when the remote artifact cannot be resolved, it will guess it as local artifact.
              return file.exists()
                  ? new FakeRemoteOutputArtifact(file, Path.of(artifact.getRelativePath()))
                  : super.resolveOutput(artifact);
            }

            return super.resolveOutput(artifact);
          }
        };
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    try {
      File projectDataDirectory = folder.newFolder("projectdata");
      BlazeImportSettings dummyImportSettings =
          new BlazeImportSettings(
              "",
              "",
              projectDataDirectory.getAbsolutePath(),
              "",
              BuildSystemName.Bazel,
              ProjectType.ASPECT_SYNC);
      BlazeImportSettingsManager.getInstance(project).setImportSettings(dummyImportSettings);
    } catch (IOException e) {
      throw new AssertionError("Fail to create directory for test", e);
    }
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    applicationServices.register(RemoteArtifactPrefetcher.class, new DefaultPrefetcher());
    projectServices.register(UnpackedAars.class, new UnpackedAars(project));
    registerExtensionPoint(FileCache.EP_NAME, FileCache.class)
        .registerExtension(new FileCacheAdapter());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class)
        .registerExtension(new BlazeAndroidSyncPlugin());
    registerExtensionPoint(BlazeLibrarySorter.EP_NAME, BlazeLibrarySorter.class);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  private static class FakeRemoteOutputArtifact implements RemoteOutputArtifact {
    private final File file;
    private final Path artifactPath;

    FakeRemoteOutputArtifact(File file, Path artifactPath) {
      this.file = file;
      this.artifactPath = artifactPath;
    }

    @Override
    public long getLength() {
      return this.file.length();
    }

    @Override
    public BufferedInputStream getInputStream() throws IOException {
      return new BufferedInputStream(new FileInputStream(file));
    }

    @Override
    public String getConfigurationMnemonic() {
      return "";
    }

    @Override
    public Path getArtifactPath() {
      return artifactPath;
    }

    @Nullable
    @Override
    public ArtifactState toArtifactState() {
      return null;
    }

    @Override
    public void prefetch() {}

    @Override
    public String getHashId() {
      return String.valueOf(FileUtil.fileHashCode(file));
    }

    @Override
    public long getSyncTimeMillis() {
      return 0;
    }

    @Override
    public String getDigest() {
      // The digest algorithm depends on the build system and thus in-memory hash code is suitable
      // in tests.
      return String.valueOf(FileUtil.fileHashCode(file));
    }
  }

  private ArtifactLocation generateArtifactLocation(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(workspaceRoot.directory().getAbsolutePath())
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  @Test
  public void refresh_localArtifact_srcJarIsCopied() throws IOException {
    testRefreshSrcJarIsCopied(localArtifactLocationDecoder);
  }

  @Test
  public void refresh_remoteArtifact_srcJarIsCopied() throws IOException {
    testRefreshSrcJarIsCopied(remoteArtifactLocationDecoder);
  }

  private void testRefreshSrcJarIsCopied(ArtifactLocationDecoder decoder) throws IOException {
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();

    // new aar with jar files
    String importedAar = "import.aar";
    String importedAarJar = "importAar.jar";
    String importedAarSrcJar = "importAar-src.jar";
    String colorsXmlRelativePath = "res/values/colors.xml";
    LibraryFileBuilder.aar(workspaceRoot, importedAar)
        .addContent(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    File jar = workspaceRoot.fileForPath(new WorkspacePath(importedAarJar));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(jar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/gen/Gen.java"));
      zo.write("package gen; class Gen {}".getBytes(UTF_8));
      zo.closeEntry();
    }

    File srcJar = workspaceRoot.fileForPath(new WorkspacePath(importedAarSrcJar));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(srcJar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/gen/Gen.class"));
      zo.write("package gen; class Gen {}".getBytes(UTF_8));
      zo.closeEntry();
    }
    ArtifactLocation importedAarArtifactLocation = generateArtifactLocation(importedAar);
    ArtifactLocation jarArtifactLocation = generateArtifactLocation(importedAarJar);
    ArtifactLocation srcJarArtifactLocation = generateArtifactLocation(importedAarSrcJar);
    LibraryArtifact libraryArtifact =
        LibraryArtifact.builder()
            .setInterfaceJar(jarArtifactLocation)
            .addSourceJar(srcJarArtifactLocation)
            .build();
    AarLibrary importedAarLibrary =
        new AarLibrary(libraryArtifact, importedAarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(importedAarArtifactLocation),
                importedAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(aarCacheDir.list()).hasLength(1);

    ImmutableList<File> cachedSrcJars = unpackedAars.getCachedSrcJars(decoder, importedAarLibrary);
    assertThat(cachedSrcJars).hasSize(1);
    assertThat(Files.readAllBytes(cachedSrcJars.get(0).toPath()))
        .isEqualTo(Files.readAllBytes(srcJar.toPath()));
  }

  @Test
  public void refresh_localArtifact_fileNotExist_success() {
    testRefreshFileNotExist(localArtifactLocationDecoder);
  }

  @Test
  public void refresh_remoteArtifact_fileNotExist_success() {
    testRefreshFileNotExist(remoteArtifactLocationDecoder);
  }

  /**
   * When aar files are not accessible e.g. a remote artifacts get expired, UnpackedAars should
   * still complete unpack process with a summary about count of copied & removed file. The only
   * difference is that it will not copy the inaccessible file to local.
   */
  private void testRefreshFileNotExist(ArtifactLocationDecoder decoder) {
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();

    // non-existent aar. It's not expected but for some corner cases, aars may not be accessible
    // e.g. objfs has been expired which cannot be read anymore.
    String resourceAar = "resource.aar";
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary = new AarLibrary(resourceAarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(aarCacheDir.list()).hasLength(0);
    String messages = writingOutputSink.getMessages();
    assertThat(messages).doesNotContain("Copied 1 AARs");
  }

  @Test
  public void refresh_localArtifact_includeLintJar() throws IOException {
    testRefreshIncludeLintJar(localArtifactLocationDecoder);
  }

  @Test
  public void refresh_remoteArtifact_includeLintJar() throws IOException {
    testRefreshIncludeLintJar(remoteArtifactLocationDecoder);
  }

  private void testRefreshIncludeLintJar(ArtifactLocationDecoder decoder) throws IOException {
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();
    String aar = "wihtLint.aar";
    File lintJar = workspaceRoot.fileForPath(new WorkspacePath(FN_LINT_JAR));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(lintJar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/sampleDetector.java"));
      zo.write("package com.google.foo; class sampleDetector {}".getBytes(UTF_8));
      zo.closeEntry();
    }
    byte[] expectedJarContent = Files.readAllBytes(lintJar.toPath());
    LibraryFileBuilder.aar(workspaceRoot, aar).addContent(FN_LINT_JAR, expectedJarContent).build();
    ArtifactLocation aarArtifactLocation = generateArtifactLocation(aar);
    AarLibrary aarLibrary = new AarLibrary(aarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(aarArtifactLocation), aarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(aarCacheDir.list()).hasLength(1);
    File lintJarAarDir = unpackedAars.getAarDir(decoder, aarLibrary);

    assertThat(aarCacheDir.listFiles()).asList().containsExactly(lintJarAarDir);
    assertThat(lintJarAarDir.list()).asList().contains(FN_LINT_JAR);
    byte[] actualJarContent = Files.readAllBytes(new File(lintJarAarDir, FN_LINT_JAR).toPath());
    assertThat(actualJarContent).isEqualTo(expectedJarContent);
  }

  @Test
  public void refresh_localArtifact_success() throws IOException {
    testRefresh(localArtifactLocationDecoder);
  }

  @Test
  public void refresh_remoteArtifact_success() throws IOException {
    testRefresh(remoteArtifactLocationDecoder);
  }

  private void testRefresh(ArtifactLocationDecoder decoder) throws IOException {
    // aars cached last time but not included in current build output. It should be removed after
    // current sync get finished.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();
    String stringsXmlRelativePath = "res/values/strings.xml";
    new File(aarCacheDir, "outOFDate.aar").mkdirs();

    // new aar without jar files
    String resourceAar = "resource.aar";
    LibraryFileBuilder.aar(workspaceRoot, resourceAar)
        .addContent(stringsXmlRelativePath, ImmutableList.of(STRINGS_XML_CONTENT))
        .build();
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary = new AarLibrary(resourceAarArtifactLocation, null);

    // new aar with jar files
    String importedAar = "import.aar";
    String importedAarJar = "importAar.jar";
    String colorsXmlRelativePath = "res/values/colors.xml";
    LibraryFileBuilder.aar(workspaceRoot, importedAar)
        .addContent(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    File jar = workspaceRoot.fileForPath(new WorkspacePath(importedAarJar));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(jar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/gen/Gen.java"));
      zo.write("package gen; class Gen {}".getBytes(UTF_8));
      zo.closeEntry();
    }
    ArtifactLocation importedAarArtifactLocation = generateArtifactLocation(importedAar);
    ArtifactLocation jarArtifactLocation = generateArtifactLocation(importedAarJar);
    LibraryArtifact libraryArtifact =
        LibraryArtifact.builder().setInterfaceJar(jarArtifactLocation).build();
    AarLibrary importedAarLibrary =
        new AarLibrary(libraryArtifact, importedAarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary,
                LibraryKey.libraryNameFromArtifactLocation(importedAarArtifactLocation),
                importedAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(aarCacheDir.list()).hasLength(2);
    File resourceAarDir = unpackedAars.getAarDir(decoder, resourceAarLibrary);
    File importedAarDir = unpackedAars.getAarDir(decoder, importedAarLibrary);
    assertThat(aarCacheDir.listFiles()).asList().containsExactly(resourceAarDir, importedAarDir);
    assertThat(resourceAarDir.list()).asList().containsExactly("aar.timestamp", "res");
    assertThat(importedAarDir.list()).asList().containsExactly("aar.timestamp", "res", "jars");

    assertThat(new File(resourceAarDir, "res").list()).hasLength(1);
    assertThat(new File(importedAarDir, "res").list()).hasLength(1);

    assertThat(
            new String(
                Files.readAllBytes(new File(resourceAarDir, stringsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(STRINGS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(importedAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);
    byte[] actualJarContent =
        Files.readAllBytes(new File(importedAarDir, "jars/classes_and_libs_merged.jar").toPath());
    byte[] expectedJarContent = Files.readAllBytes(jar.toPath());
    assertThat(actualJarContent).isEqualTo(expectedJarContent);

    String messages = writingOutputSink.getMessages();
    assertThat(messages).contains("Copied 2 AARs");
    assertThat(messages).contains("Removed 1 AARs");
  }

  @Test
  public void getLintRuleJar_localArtifact_lintFileIsReturn() throws IOException {
    testGetLintRuleJarLintFileIsReturn(localArtifactLocationDecoder);
  }

  @Test
  public void getLintRuleJar_remoteArtifact_lintFileIsReturn() throws IOException {
    testGetLintRuleJarLintFileIsReturn(remoteArtifactLocationDecoder);
  }

  private void testGetLintRuleJarLintFileIsReturn(ArtifactLocationDecoder decoder)
      throws IOException {
    // arrange: Set up project data with an aar library containing a lint check in its lint.jar.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    String aar = "withLint.aar";
    File lintJar = workspaceRoot.fileForPath(new WorkspacePath(FN_LINT_JAR));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(lintJar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/sampleDetector.java"));
      zo.write("package com.google.foo; class sampleDetector {}".getBytes(UTF_8));
      zo.closeEntry();
    }
    byte[] expectedJarContent = Files.readAllBytes(lintJar.toPath());
    LibraryFileBuilder.aar(workspaceRoot, aar).addContent(FN_LINT_JAR, expectedJarContent).build();
    ArtifactLocation aarArtifactLocation = generateArtifactLocation(aar);
    AarLibrary aarLibrary = new AarLibrary(aarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(aarArtifactLocation), aarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();

    // act: refresh all the file caches, which in turn will unpack the aars and register the lint
    // jars.
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    // assert: verify that the lint jar was extracted from the aar, and that it is has the right
    // contents.
    File actualLintRuleJar = unpackedAars.getLintRuleJar(decoder, aarLibrary);
    assertThat(actualLintRuleJar.exists()).isTrue();
    assertThat(actualLintRuleJar.getName()).isEqualTo(FN_LINT_JAR);
    byte[] actualJarContent = Files.readAllBytes(actualLintRuleJar.toPath());
    assertThat(actualJarContent).isEqualTo(expectedJarContent);
  }

  @Test
  public void getLintRuleJar_localArtifact_noLint_fileIsReturnButNotExist() throws IOException {
    testGetLintRuleJarNoLintFileIsReturnButNotExist(localArtifactLocationDecoder);
  }

  @Test
  public void getLintRuleJar_remoteArtifact_noLint_fileIsReturnButNotExist() throws IOException {
    testGetLintRuleJarNoLintFileIsReturnButNotExist(remoteArtifactLocationDecoder);
  }

  private void testGetLintRuleJarNoLintFileIsReturnButNotExist(ArtifactLocationDecoder decoder) {
    // arrange: Set up project data with an aar library without a lint.jar.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);

    String aar = "noLint.aar";
    LibraryFileBuilder.aar(workspaceRoot, aar).build();
    ArtifactLocation aarArtifactLocation = generateArtifactLocation(aar);
    AarLibrary aarLibrary = new AarLibrary(aarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(aarArtifactLocation), aarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();

    // act: refresh all the file caches, which in turn will unpack the aars
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    // assert: a nonexistent file is return since this aar does not have lint with it
    File actualLintRuleJar = unpackedAars.getLintRuleJar(decoder, aarLibrary);
    assertThat(actualLintRuleJar.exists()).isFalse();
  }

  @Test
  public void getLintRuleJar_localArtifact_notExistAar_nullIsReturn() throws IOException {
    testGetLintRuleJarNotExistAarNullIsReturn(localArtifactLocationDecoder);
  }

  @Test
  public void getLintRuleJar_remoteArtifact_notExistAar_nullIsReturn() throws IOException {
    testGetLintRuleJarNotExistAarNullIsReturn(remoteArtifactLocationDecoder);
  }

  private void testGetLintRuleJarNotExistAarNullIsReturn(ArtifactLocationDecoder decoder) {
    // arrange: Set up project data without aar.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);

    String notImportedAar = "notImported.aar";
    LibraryFileBuilder.aar(workspaceRoot, notImportedAar).build();
    ArtifactLocation notImportedAarArtifactLocation = generateArtifactLocation(notImportedAar);
    AarLibrary notImportedAarLibrary = new AarLibrary(notImportedAarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(), ImmutableMap.of(), ImmutableList.of(), ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();

    // act: refresh all the file caches, which in turn will unpack nothing
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    // assert: null is return since aar cache fail to find the aar directory.
    File actualLintRuleJar = unpackedAars.getLintRuleJar(decoder, notImportedAarLibrary);
    assertThat(actualLintRuleJar).isNull();
  }

  private static class WritingOutputSink implements OutputSink<PrintOutput> {

    private final Writer writer = new StringWriter();

    @Override
    public Propagation onOutput(PrintOutput output) {
      try {
        writer.write(output.getText());
        return Propagation.Continue;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public String getMessages() {
      return writer.toString();
    }
  }
}
