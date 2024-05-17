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
import static com.android.tools.idea.projectsystem.ProjectSystemBuildUtil.PROJECT_SYSTEM_BUILD_TOPIC;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.createAndroidProjectBuilderForDefaultTestProjectStructure;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.updateTestProjectFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;
import static com.android.tools.idea.testing.JavacUtil.getJavac;

import com.android.ide.common.gradle.Component;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.tools.idea.editors.fast.FastPreviewConfiguration;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.projectsystem.gradle.GradleClassFinderUtil;
import com.android.tools.idea.rendering.StudioModuleRenderContext;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.StudioResourceIdManager;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.testing.AndroidLibraryDependency;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.android.tools.idea.testing.ModuleModelBuilder;
import com.android.tools.rendering.classloading.ModuleClassLoader;
import com.android.tools.rendering.classloading.ModuleClassLoaderManager;
import com.android.tools.rendering.classloading.NopModuleClassLoadedDiagnostics;
import com.android.tools.idea.res.TestResourceIdManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;

public class StudioModuleClassLoaderTest extends AndroidTestCase {

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
    FastPreviewConfiguration.Companion.getInstance().resetDefault();
  }

  /**
   * Generates an empty R class file with one static field ID = "FileID"
   */
  @SuppressWarnings("SameParameterValue")
  private static void generateRClass(@NotNull Project project, @NotNull String pkg, @NotNull File outputFile) throws IOException {
    File tmpDir = Files.createTempDirectory("source").toFile();
    File tmpClass = new File(tmpDir, "R.java");
    FileUtil.writeToFile(tmpClass,
                         "package " + pkg + ";" +
                         "public class R {" +
                         "      public static final String ID = \"FileID\";" +
                         "}");

    buildFile(project, tmpClass.getAbsolutePath());
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

    generateRClass(getProject(), "test", new File(outputDir, "R.class"));

    ApplicationManager.getApplication().runReadAction(() -> {
      try (ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderReference = StudioModuleClassLoaderManager.get()
        .getShared(null, StudioModuleRenderContext.forModule(module))) {
        try {
          Class<?> rClass = loaderReference.getClassLoader().loadClass("test.R");
          String value = (String)rClass.getDeclaredField("ID").get(null);
          assertEquals("FileID", value);
        }
        catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
          fail("Unexpected exception " + e.getLocalizedMessage());
        }
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
    testResourceIdManager.setFinalIdsUsed(false);
    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDirectory("testProject").toFile();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
    assertTrue(FileUtil.createDirectory(outputDir));
    Objects.requireNonNull(CompilerProjectExtension.getInstance(getProject())).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass(getProject(), "test", new File(outputDir, "R.class"));

    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(module);
    ResourceNamespace namespace = Objects.requireNonNull(repositoryManager).getNamespace();
    ResourceRepository moduleResources = repositoryManager.getModuleResources();
    ResourceClassRegistry rClassRegistry = ResourceClassRegistry.get(module.getProject());
    rClassRegistry.addLibrary(moduleResources, StudioResourceIdManager.get(module), "test", namespace);

    ApplicationManager.getApplication().runReadAction(() -> {
      try (ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderReference = StudioModuleClassLoaderManager.get()
        .getShared(null, StudioModuleRenderContext.forModule(module))) {
        try {
          Class<?> rClass = loaderReference.getClassLoader().loadClass("test.R");
          rClass.getDeclaredField("ID");
          fail("Field \"ID\" is not expected");
        }
        catch (NoSuchFieldException expected) {
        }
        catch (ClassNotFoundException e) {
          fail("Unexpected exception " + e.getLocalizedMessage());
        }
      }
    });
  }

  public void testIsSourceModifiedWithOverlay() throws IOException, ClassNotFoundException {
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

    buildFile(getProject(), aClassSrc.toString());

    ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderReference =
      StudioModuleClassLoaderManager.get().getShared(null, StudioModuleRenderContext.forModule(myModule));
    StudioModuleClassLoader loader = loaderReference.getClassLoader();

    // Add the compiled class to the overlay directory
    Files.copy(packageDir.resolve("AClass.class"), overlayDir1.resolve("com/google/example/AClass.class"));
    loader.loadClass("com.google.example.AClass");

    assertTrue(loader.isUserCodeUpToDate());
    // New overlay will make the code out-of-date
    ModuleClassLoaderOverlays.getInstance(myModule).pushOverlayPath(overlayDir2);
    assertFalse(loader.isUserCodeUpToDate());
    StudioModuleClassLoaderManager.get().release(loaderReference);
  }

  private void doTestLibRClass(boolean finalIdsUsed) throws Exception {
    testResourceIdManager.setFinalIdsUsed(finalIdsUsed);

    setupTestProjectFromAndroidModel(
      getProject(),
      new File(Objects.requireNonNull(getProject().getBasePath())),
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, "p1.p2")));

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

    ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderReference =
      StudioModuleClassLoaderManager.get().getShared(null, StudioModuleRenderContext.forModule(myModule));
    ModuleClassLoader loader = loaderReference.getClassLoader();
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
      GradleClassFinderUtil.getModuleCompileOutputs(myModule, EnumSet.of(ScopeType.MAIN)).collect(Collectors.toList()).get(0).toPath();
    Files.createDirectories(moduleCompileOutputPath);
    Path packageDir = Files.createDirectories(moduleCompileOutputPath.resolve("p1/p2"));
    Path rSrcFile = Files.createFile(packageDir.resolve("R.java"));
    FileUtil.writeToFile(rSrcFile.toFile(), "package com.google.example; public class R { }");
    buildFile(getProject(), rSrcFile.toString());

    // Now, the class should be found, regardless of final IDs being used or not.
    assertNotNull(loader.loadClass("p1.p2.R"));
    StudioModuleClassLoaderManager.get().release(loaderReference);
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

    try (ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderRef = StudioModuleClassLoaderManager.get()
      .getShared(null, StudioModuleRenderContext.forModule(myModule))) {
      ModuleClassLoader loader = loaderRef.getClassLoader();
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
    ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderRef = StudioModuleClassLoaderManager.get()
      .getPrivate(null, StudioModuleRenderContext.forModule(Objects.requireNonNull(appModule)));
    StudioModuleClassLoader loader = loaderRef.getClassLoader();
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

    StudioModuleClassLoaderManager.get().release(loaderRef);
  }

  public void testModuleClassLoaderCopy() {
    ModuleClassLoaderManager.Reference<StudioModuleClassLoader> loaderRef = StudioModuleClassLoaderManager.get()
      .getPrivate(null, StudioModuleRenderContext.forModule(Objects.requireNonNull(myFixture.getModule())));
    StudioModuleClassLoader loader = loaderRef.getClassLoader();

    StudioModuleClassLoader copy = loader.copy(NopModuleClassLoadedDiagnostics.INSTANCE);
    assertNotNull(copy);
    copy.dispose();

    StudioModuleClassLoaderManager.get().release(loaderRef);

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
        Component.parse(artifactAddress),
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
        "srcJar.jar",
        "docJar.jar",
        "samplesJar.jar",
        "externalAnnotations",
        "publicResources",
        gradleCacheRoot.toPath().resolve("artifactFile").toFile(),
        "symbolFile",
        it -> it
      ));
  }

  /**
   * Builds the given file using javac.
   */
  private static void buildFile(@NotNull Project project, @NotNull String javaFilePath) {
    JavaCompiler javac = getJavac();
    javac.run(null, System.out, System.err, javaFilePath);
    project.getMessageBus().syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).buildCompleted(new ProjectSystemBuildManager.BuildResult(
      ProjectSystemBuildManager.BuildMode.COMPILE, ProjectSystemBuildManager.BuildStatus.SUCCESS, System.currentTimeMillis()));
  }
}
