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
package org.jetbrains.android.uipreview;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.createAndroidProjectBuilderForDefaultTestProjectStructure;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.updateTestProjectFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.tools.idea.editors.fast.FastPreviewConfiguration;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.projectsystem.gradle.GradleClassFinderUtil;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.TestResourceIdManager;
import com.android.tools.idea.testing.AndroidLibraryDependency;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.android.tools.idea.testing.ModuleModelBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.TimeoutUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;

public class ModuleClassLoaderTest extends AndroidTestCase {

  private TestResourceIdManager testResourceIdManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testResourceIdManager = TestResourceIdManager.Companion.getManager(myModule);
  }

  @Override
  protected void tearDown() throws Exception {
    testResourceIdManager.resetFinalIdsUsed();
    super.tearDown();
    StudioFlags.COMPOSE_FAST_PREVIEW.clearOverride();
    FastPreviewConfiguration.Companion.getInstance().resetDefault();
  }

  /**
   * Generates an empty R class file with one static field ID = "FileID"
   */
  @SuppressWarnings("SameParameterValue")
  private static void generateRClass(@NotNull String pkg, @NotNull File outputFile) throws IOException {
    File tmpDir = Files.createTempDirectory("source").toFile();
    File tmpClass = new File(tmpDir, "R.java");
    FileUtil.writeToFile(tmpClass,
                         "package " + pkg + ";" +
                         "public class R {" +
                         "      public static final String ID = \"FileID\";" +
                         "}");

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    javac.run(null, System.out, System.err, tmpClass.getAbsolutePath());

    FileUtil.copy(new File(tmpDir, "R.class"), outputFile);
  }

  // Disabled. Failing in post-submit
  @SuppressWarnings("unused")
  public void disabledTestModuleClassLoading() throws IOException {
    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDirectory("testProject").toFile();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
    assertTrue(FileUtil.createDirectory(outputDir));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass("test", new File(outputDir, "R.class"));

    ApplicationManager.getApplication().runReadAction(() -> {
      ModuleClassLoader loader = ModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(module), this);
      try {
        Class<?> rClass = loader.loadClass("test.R");
        String value = (String)rClass.getDeclaredField("ID").get(null);
        assertEquals("FileID", value);
      }
      catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
        fail("Unexpected exception " + e.getLocalizedMessage());
      }
      finally {
        ModuleClassLoaderManager.get().release(loader, this);
      }
    });
  }

  /**
   * Verifies that the AAR generated R classes are given priority vs the build generated files. This is important in cases like support
   * library upgrades/downgrades. In those cases, the build generated file, will be outdated so it shouldn't be used by the ModuleClassLoader.
   * By preferring the AAR geneated versions, we make sure we are always up-to-date.
   * See <a href="http://b.android.com/229382">229382</a>
   */
  public void testAARPriority() throws Exception {
    doTestAARPriority();
  }

  public void testAARPriorityNamespaced() throws Exception {
    enableNamespacing("test");
    doTestAARPriority();
  }

  private void doTestAARPriority() throws IOException {
    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDirectory("testProject").toFile();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
    assertTrue(FileUtil.createDirectory(outputDir));
    Objects.requireNonNull(CompilerProjectExtension.getInstance(getProject())).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass("test", new File(outputDir, "R.class"));

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(module);
    ResourceNamespace namespace = Objects.requireNonNull(repositoryManager).getNamespace();
    List<ResourceRepository> repositories = repositoryManager.getAppResourcesForNamespace(namespace);
    // In the namespaced case two repositories are returned. The first one is a module repository,
    // the second one is an empty repository of user-defined sample data. In the non-namespaced case
    // the app resource repository is returned.
    assertFalse(repositories.isEmpty());
    ResourceClassRegistry rClassRegistry = ResourceClassRegistry.get(module.getProject());
    rClassRegistry.addLibrary(repositories.get(0), ResourceIdManager.get(module), "test", namespace);

    ApplicationManager.getApplication().runReadAction(() -> {
      ModuleClassLoader loader = ModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(module), this);
      try {
        Class<?> rClass = loader.loadClass("test.R");
        rClass.getDeclaredField("ID");
        fail("Field \"ID\" is not expected");
      }
      catch (NoSuchFieldException expected) {
      }
      catch (ClassNotFoundException e) {
        fail("Unexpected exception " + e.getLocalizedMessage());
      }
      finally {
        ModuleClassLoaderManager.get().release(loader, this);
      }
    });
  }

  public void testIsSourceModified() throws IOException {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(Objects.requireNonNull(getProject().getBasePath())),
      new AndroidModuleModelBuilder(":", "debug", createAndroidProjectBuilderForDefaultTestProjectStructure()));

    Path srcDir = Files.createDirectories(Files.createTempDirectory("testProject").resolve("src"));
    Path packageDir = Files.createDirectories(srcDir.resolve("com/google/example"));
    Path rSrc = Files.createFile(packageDir.resolve("R.java"));
    FileUtil.writeToFile(rSrc.toFile(), "package com.google.example; public class R { public class string {} }");
    Path modifiedSrc = Files.createFile(packageDir.resolve("Modified.java"));
    FileUtil.writeToFile(modifiedSrc.toFile(), "package com.google.example; public class Modified {}");
    Path notModifiedSrc = Files.createFile(packageDir.resolve("NotModified.java"));
    FileUtil.writeToFile(notModifiedSrc.toFile(), "package com.google.example; public class NotModified {}");

    ApplicationManager.getApplication().runWriteAction(
      (Computable<SourceFolder>)() -> PsiTestUtil.addSourceRoot(myModule, VfsUtil.findFileByIoFile(srcDir.toFile(), true)));

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    for (Path src : ImmutableList.of(rSrc, modifiedSrc, notModifiedSrc)) {
      javac.run(null, null, null, src.toAbsolutePath().toString());
    }

    VirtualFile rClass = VfsUtil.findFileByIoFile(new File(rSrc.getParent().toFile(), "R.class"), true);
    assertThat(rClass).isNotNull();
    VirtualFile rStringClass = VfsUtil.findFileByIoFile(new File(rSrc.getParent().toFile(), "R$string.class"), true);
    assertThat(rStringClass).isNotNull();
    VirtualFile modifiedClass = VfsUtil.findFileByIoFile(new File(modifiedSrc.getParent().toFile(), "Modified.class"), true);
    assertThat(modifiedClass).isNotNull();
    VirtualFile notModifiedClass = VfsUtil.findFileByIoFile(new File(notModifiedSrc.getParent().toFile(), "NotModified.class"), true);
    assertThat(notModifiedClass).isNotNull();

    ModuleClassLoader loader = ModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(myModule), this);
    loader.injectProjectClassFile("com.google.example.R", rClass);
    loader.injectProjectClassFile("com.google.example.R$string", rStringClass);
    loader.injectProjectClassFile("com.google.example.Modified", modifiedClass);
    loader.injectProjectClassFile("com.google.example.NotModified", notModifiedClass);

    // Wait a bit to make sure timestamp is different.
    // At least one whole second because Apple's HFS only has whole second resolution.
    TimeoutUtil.sleep(1200);

    // Always false for R classes.
    assertThat(loader.isSourceModified("com.google.example.R", null)).isFalse();
    assertThat(loader.isSourceModified("com.google.example.R$string", null)).isFalse();

    // Even if we modify them.
    FileUtil.appendToFile(rSrc.toFile(), "// some comments.");
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(rSrc.toFile()));
    assertThat(loader.isSourceModified("com.google.example.R", null)).isFalse();
    assertThat(loader.isSourceModified("com.google.example.R$string", null)).isFalse();

    // No build yet.
    FileUtil.appendToFile(modifiedSrc.toFile(), "// some comments.");
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(modifiedSrc.toFile()));
    assertThat(loader.isSourceModified("com.google.example.Modified", null)).isTrue();
    assertThat(loader.isSourceModified("com.google.example.NotModified", null)).isFalse();

    // Trigger build.
    getProjectSystem(getProject()).getBuildManager().compileFilesAndDependencies(
      ImmutableList.of(Objects.requireNonNull(VfsUtil.findFile(modifiedSrc, false))));
    assertThat(loader.isSourceModified("com.google.example.Modified", null)).isFalse();
    assertThat(loader.isSourceModified("com.google.example.NotModified", null)).isFalse();

    // Recompile and check ClassLoader is out of date. We are not really modifying the PSI so we can not use isUserCodeUpToDate
    // since it relies on the PSI modification to cache the information.
    loader.injectProjectClassFile("com.google.example.Modified", modifiedClass);
    assertTrue(loader.isUserCodeUpToDateNonCached());
    javac.run(null, null, null, modifiedSrc.toAbsolutePath().toString());
    assertFalse(loader.isUserCodeUpToDateNonCached());

    ModuleClassLoaderManager.get().release(loader, this);
  }

  public void testIsSourceModifiedWithOverlay() throws IOException, ClassNotFoundException {
    StudioFlags.COMPOSE_FAST_PREVIEW.override(true);
    FastPreviewConfiguration.Companion.getInstance().setEnabled(true);
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(Objects.requireNonNull(getProject().getBasePath())),
      new AndroidModuleModelBuilder(":", "debug", createAndroidProjectBuilderForDefaultTestProjectStructure()));

    // Create regular project path
    Path srcDir = Files.createDirectories(Files.createTempDirectory("testProject").resolve("src"));
    Path packageDir = Files.createDirectories(srcDir.resolve("com/google/example"));
    Path aClassSrc = Files.createFile(packageDir.resolve("AClass.java"));
    FileUtil.writeToFile(aClassSrc.toFile(), "package com.google.example; public class AClass {}");

    Path overlayDir1 = Files.createDirectories(Files.createTempDirectory("overlay"));
    Path overlayDir2 = Files.createDirectories(Files.createTempDirectory("overlay"));
    Files.createDirectories(overlayDir1.resolve("com/google/example"));
    ModuleClassLoaderOverlays.getInstance(myModule).pushOverlayPath(overlayDir1);

    ApplicationManager.getApplication().runWriteAction(
      (Computable<SourceFolder>)() -> PsiTestUtil.addSourceRoot(myModule,
                                                                Objects.requireNonNull(VfsUtil.findFileByIoFile(srcDir.toFile(), true))));

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    javac.run(null, null, null, aClassSrc.toString());

    ModuleClassLoader loader = ModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(myModule), this);
    // Add the compiled class to the overlay directory
    Files.copy(packageDir.resolve("AClass.class"), overlayDir1.resolve("com/google/example/AClass.class"));
    loader.loadClass("com.google.example.AClass");

    assertTrue(loader.isUserCodeUpToDateNonCached());
    // New overlay will make the code out-of-date
    ModuleClassLoaderOverlays.getInstance(myModule).pushOverlayPath(overlayDir2);
    assertFalse(loader.isUserCodeUpToDateNonCached());
    ModuleClassLoaderManager.get().release(loader, this);
  }

  private void doTestLibRClass(boolean finalIdsUsed) throws Exception {
    testResourceIdManager.setFinalIdsUsed(finalIdsUsed);

    setupTestProjectFromAndroidModel(
      getProject(),
      new File(Objects.requireNonNull(getProject().getBasePath())),
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)));

    SourceProviders sourceProviderManager = SourceProviderManager.getInstance(myFacet);
    VirtualFile defaultManifest = sourceProviderManager.getMainManifestFile();

    WriteAction.run(() -> {
      VirtualFile manifestFile = sourceProviderManager.getMainManifestFile();
      if (manifestFile == null) {
        String manifestUrl = Iterables.getOnlyElement(sourceProviderManager.getMainIdeaSourceProvider().getManifestFileUrls());
        VirtualFile manifestDirectory =
          Iterables.getOnlyElement(sourceProviderManager.getMainIdeaSourceProvider().getManifestDirectories());
        manifestFile = manifestDirectory.createChildData(this, Objects.requireNonNull(VfsUtil.extractFileName(manifestUrl)));
      }
      assertThat(manifestFile).named("Manifest virtual file").isNotNull();
      byte[] defaultManifestContent = Objects.requireNonNull(defaultManifest).contentsToByteArray();
      assertNotNull(defaultManifestContent);
      manifestFile.setBinaryContent(defaultManifestContent);
    });
    assertThat(Manifest.getMainManifest(myFacet)).isNotNull();

    ModuleClassLoader loader = ModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(myModule), this);
    try {
      assertNotNull(loader.loadClass("p1.p2.R"));
      if (finalIdsUsed) {
        fail("When final IDs are used, resource classes should not be loaded from light classes in LibraryResourceClassLoader.");
      }
    }
    catch (ClassNotFoundException e) {
      if (!finalIdsUsed) {
        fail("When final IDs are not used, resource classes should be found even if there is no backing compiled classes.");
      }
    }

    // Make sure there is a compiled R class in the output directory, to be used when final IDs are used.
    Path moduleCompileOutputPath =
      GradleClassFinderUtil.getModuleCompileOutputs(myModule,false).collect(Collectors.toList()).get(0).toPath();
    Files.createDirectories(moduleCompileOutputPath);
    Path packageDir = Files.createDirectories(moduleCompileOutputPath.resolve("p1/p2"));
    Path rSrcFile = Files.createFile(packageDir.resolve("R.java"));
    FileUtil.writeToFile(rSrcFile.toFile(), "package com.google.example; public class R { }");
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    javac.run(null, System.out, System.err, rSrcFile.toString());

    // Now, the class should be found, regardless of final IDs being used or not.
    assertNotNull(loader.loadClass("p1.p2.R"));
    ModuleClassLoaderManager.get().release(loader, this);
  }

  // Regression test for b/233862429
  public void testLibRClassFinalIds() throws Exception {
    doTestLibRClass(true);
  }

  public void testLibRClassNoFinalIds() throws Exception {
    doTestLibRClass(false);
  }

  // Regression test for b/162056408
  public void testDisallowLoadingAndroidDispatcherFactory() throws Exception {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(Objects.requireNonNull(getProject().getBasePath())),
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)));

    ModuleClassLoader loader = ModuleClassLoaderManager.get().getShared(null, ModuleRenderContext.forModule(myModule), this);
    try {
      try {
        loader.loadClass("kotlinx.coroutines.android.AndroidDispatcherFactory");
        fail("AndroidDispatcherFactory should not be allowed to load by ModuleClassLoader since it would trigger the use of Android " +
             "coroutines instead of the host ones");
      }
      catch (IllegalArgumentException ignored) {
      }

      // Check we can load resources from the layoutlib-extensions.jar
      assertNotNull(loader.getResource("META-INF/services/_layoutlib_._internal_.kotlinx.coroutines.internal.MainDispatcherFactory"));

      // Verify that not existing resources return null
      assertNull(loader.getResource("META-INF/services/does.not.exist"));
    } finally {
      ModuleClassLoaderManager.get().release(loader, this);
    }
  }

  public void testNotUpToDate_whenDependenciesChange() throws IOException {
    File basePath = new File(Objects.requireNonNull(getProject().getBasePath()));
    File gradleFolder = basePath.toPath().resolve(".gradle").toFile();
    String libFolder = "libraryFolder";
    String libJar = "file.jar";
    File libFolderFile = gradleFolder.toPath().resolve(libFolder).toFile();
    assertTrue(libFolderFile.exists() || libFolderFile.mkdirs());
    // We have to actually create the file so that the ".exists()" check in ModuleClassLoader succeeds
    Files.createFile(libFolderFile.toPath().resolve(libJar));

    ModuleModelBuilder appModuleBuilder =
      new AndroidModuleModelBuilder(
        ":app",
        "debug",
        new AndroidProjectBuilder()
      );

    setupTestProjectFromAndroidModel(
      getProject(),
      basePath,
      JavaModuleModelBuilder.getRootModuleBuilder(),
      appModuleBuilder
    );

    Module appModule = gradleModule(getProject(), ":app");
    ModuleClassLoader loader = ModuleClassLoaderManager.get().getPrivate(null, ModuleRenderContext.forModule(
      Objects.requireNonNull(appModule)), this);
    // In addition to the initial check this also triggers creation of myJarClassLoader in ModuleClassLoader
    assertTrue(loader.areDependenciesUpToDate());

    ModuleModelBuilder updatedAppModuleBuilder =
      new AndroidModuleModelBuilder(
        ":app",
        "debug",
        new AndroidProjectBuilder()
          .withAndroidLibraryDependencyList(
            (it, variant) -> ImmutableList.of(ideAndroidLibrary(gradleFolder, "com.example:library:1.0", libFolder, libJar))));

    updateTestProjectFromAndroidModel(
      getProject(),
      basePath,
      JavaModuleModelBuilder.getRootModuleBuilder(),
      updatedAppModuleBuilder
    );
    // Module has not changed
    assertEquals(gradleModule(getProject(), ":app"), appModule);

    assertFalse(loader.areDependenciesUpToDate());

    ModuleClassLoaderManager.get().release(loader, this);
  }

  public void testModuleClassLoaderCopy() {
    ModuleClassLoader loader = ModuleClassLoaderManager.get().getPrivate(null, ModuleRenderContext.forModule(
      Objects.requireNonNull(myFixture.getModule())), this);

    ModuleClassLoader copy = loader.copy(NopModuleClassLoadedDiagnostics.INSTANCE);
    assertNotNull(copy);
    Disposer.dispose(copy);

    ModuleClassLoaderManager.get().release(loader, this);

    copy = loader.copy(NopModuleClassLoadedDiagnostics.INSTANCE);
    assertNull("Disposed ModuleClassLoaders can not be copied", copy);
  }

  private static AndroidLibraryDependency ideAndroidLibrary(File gradleCacheRoot,
                                                            @SuppressWarnings("SameParameterValue") String artifactAddress,
                                                            String folder,
                                                            String libJar) {
    return new AndroidLibraryDependency(
      IdeAndroidLibraryImpl.Companion.create(
        artifactAddress,
        "",
        gradleCacheRoot.toPath().resolve(folder).toFile(),
        "manifest.xml",
        ImmutableList.of("api.jar"),
        ImmutableList.of(libJar),
        "res",
        new File(folder + File.separator + "res.apk"),
        "assets",
        "jni",
        "aidl",
        "renderscriptFolder",
        "proguardRules",
        "lint.jar",
        "externalAnnotations",
        "publicResources",
        gradleCacheRoot.toPath().resolve("artifactFile").toFile(),
        "symbolFile",
        it -> it
      ));
  }
}
