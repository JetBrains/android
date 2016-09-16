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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  com.android.tools.idea.IdeaTestSuite.class,  // a suite mustn't contain itself
  com.android.tools.idea.rendering.RenderSecurityManagerTest.class,  // calls System.setSecurityManager
  com.android.tools.idea.templates.TemplateTest.class, // we typically set DISABLE_STUDIO_TEMPLATE_TESTS because it's so slow
  // The following classes had failures when run in Bazel.
  com.android.tools.idea.avdmanager.AvdDisplayListTest.class,
  com.android.tools.idea.configurations.ResourceResolverCacheTest.class,
  com.android.tools.idea.ddms.adb.AdbServiceTest.class,
  com.android.tools.idea.editors.AndroidGeneratedSourcesFilterTest.class,
  com.android.tools.idea.editors.manifest.ManifestConflictTest.class,
  com.android.tools.idea.editors.theme.ConfiguredThemeEditorStyleTest.class,
  com.android.tools.idea.editors.theme.ThemeEditorUtilsTest.class,
  com.android.tools.idea.exportSignedPackage.ExportSignedPackageTest.class,
  com.android.tools.idea.gradle.AndroidGradleModelTest.class,
  com.android.tools.idea.gradle.customizer.dependency.TransitiveDependencySetupTest.class,
  com.android.tools.idea.gradle.dependencies.GradleDependencyManagerTest.class,
  com.android.tools.idea.gradle.eclipse.GradleImportTest.class,
  com.android.tools.idea.gradle.InternalAndroidModelViewTest.class,
  com.android.tools.idea.gradle.invoker.GradleInvokerTest.class,
  com.android.tools.idea.gradle.plugin.AndroidPluginInfoTest.class,
  com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdaterIntegrationTest.class,
  com.android.tools.idea.gradle.project.GradleProjectSyncDataTest.class,
  com.android.tools.idea.gradle.project.NonAndroidGradleProjectImportingTestSuite.class,
  com.android.tools.idea.gradle.project.sync.cleanup.GradleDistributionCleanUpTaskTest.class,
  com.android.tools.idea.gradle.project.sync.CommandLineArgsTest.class,
  com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityCheckerTest.class,
  com.android.tools.idea.gradle.project.sync.compatibility.VersionIncompatibilityTest.class,
  com.android.tools.idea.gradle.project.sync.DependencySetupTest.class,
  com.android.tools.idea.gradle.project.sync.GradleSyncIntegrationTest.class,
  com.android.tools.idea.gradle.project.sync.GradleSyncStateIntegrationTest.class,
  com.android.tools.idea.gradle.project.sync.issues.ExternalNativeBuildIssuesReporterTest.class,
  com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporterTest.class,
  com.android.tools.idea.gradle.project.sync.issues.UnhandledIssueMessageReporterTest.class,
  com.android.tools.idea.gradle.project.sync.issues.UnresolvedDependenciesReporterTest.class,
  com.android.tools.idea.gradle.project.sync.issues.UnsupportedGradleReporterTest.class,
  com.android.tools.idea.gradle.project.sync.NewGradleSyncTest.class,
  com.android.tools.idea.gradle.project.sync.precheck.JdkPreSyncCheckTest.class,
  com.android.tools.idea.gradle.project.sync.precheck.PreSyncChecksTest.class,
  com.android.tools.idea.gradle.structure.model.android.PsAndroidModuleTest.class,
  com.android.tools.idea.gradle.testing.AndroidJunitPatcherWithTestArtifactTest.class,
  com.android.tools.idea.gradle.testing.TestArtifactCustomScopeTest.class,
  com.android.tools.idea.gradle.testing.TestArtifactSearchScopesTest.class,
  com.android.tools.idea.gradle.testing.TestArtifactsFindUsageTest.class,
  com.android.tools.idea.gradle.testing.TestArtifactsRenameTest.class,
  com.android.tools.idea.gradle.testing.TestArtifactsResolveTest.class,
  com.android.tools.idea.javadoc.AndroidJavaDocWithGradleTest.class,
  com.android.tools.idea.model.AndroidModuleInfoTest.class,
  com.android.tools.idea.navigator.AndroidProjectViewTest.class,
  com.android.tools.idea.npw.ConfigureAndroidModuleStepDynamicTest.class,
  com.android.tools.idea.npw.importing.ArchiveToGradleModuleModelTest.class,
  com.android.tools.idea.npw.importing.ArchiveToGradleModuleStepTest.class,
  com.android.tools.idea.npw.importing.SourceToGradleModuleModelTest.class,
  com.android.tools.idea.npw.importing.SourceToGradleModuleStepTest.class,
  com.android.tools.idea.npw.NewModuleWizardStateTest.class,
  com.android.tools.idea.npw.project.AndroidGradleModuleUtilsTest.class,
  com.android.tools.idea.npw.TemplateWizardModuleBuilderTest.class,
  com.android.tools.idea.rendering.LayoutPullParserFactoryTest.class,
  com.android.tools.idea.rendering.MenuPreviewRendererTest.class,
  com.android.tools.idea.rendering.RenderErrorModelFactoryTest.class,
  com.android.tools.idea.run.AndroidTestConfigurationProducerTest.class,
  com.android.tools.idea.run.GradleApkProviderTest.class,
  com.android.tools.idea.run.GradleApplicationIdProviderTest.class,
  com.android.tools.idea.run.LaunchUtilsTest.class,
  com.android.tools.idea.templates.CreateGradleWrapperTest.class,
  com.android.tools.idea.templates.GradleFilePsiMergerTest.class,
  com.android.tools.idea.templates.GradleFileSimpleMergerTest.class,
  com.android.tools.idea.templates.ParameterTest.class,
  com.android.tools.idea.templates.RepositoryUrlManagerTest.class,
  com.android.tools.idea.templates.UniqueParameterTest.class,
  com.android.tools.idea.testing.TestProjectPathsGeneratorTest.class,
  com.android.tools.idea.uibuilder.palette.IconPreviewFactoryTest.class,
  com.android.tools.idea.uibuilder.property.editors.StyleFilterTest.class,
  com.android.tools.idea.uibuilder.surface.InteractionManagerTest.class,
  com.android.tools.idea.wizard.template.TemplateWizardStateTest.class,
  com.android.tools.swing.layoutlib.GraphicsLayoutRendererTest.class,
  org.jetbrains.android.databinding.GeneratedCodeMatchTest.class,
  org.jetbrains.android.facet.IdeaSourceProviderTest.class,
  org.jetbrains.android.projectView.ResourceMergerTest.class,
  org.jetbrains.android.refactoring.UnusedResourcesGradleTest.class,
  org.jetbrains.android.sdk.AndroidSdkDataTest.class,
})
public class IdeaTestSuite {

  private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String HOST_DIR = OsType.getHostOs().getFolderName();

  // Initialize Idea specific environment
  static {
    setIdeaHome();
    // Adds embedded Maven repo directory for tests, see EmbeddedDistributionPaths for details.
    createTmpDir("prebuilts/tools/common/offline-m2");
    // Bazel tests are sandboxed so we disable VfsRoot checks.
    VfsRootAccess.allowRootAccess("/");

    symbolicLinkInTmpDir("tools/adt/idea/android/annotations");
    symbolicLinkInTmpDir("tools/idea/java/jdkAnnotations");
    symbolicLinkInTmpDir("tools/base/templates");
    symbolicLinkInTmpDir("tools/adt/idea/android/device-art-resources");
    symbolicLinkInTmpDir("tools/adt/idea/android/testData");
    symbolicLinkInTmpDir("tools/adt/idea/designer/testData");
    symbolicLinkInTmpDir("prebuilts/studio/jdk");
    symbolicLinkInTmpDir("prebuilts/studio/sdk/" + HOST_DIR + "/platforms/" + TestUtils.getLatestAndroidPlatform());
  }

  private static void symbolicLinkInTmpDir(String target) {
    Path targetPath = TestUtils.getWorkspaceFile(target).toPath();
    Path linkName = Paths.get(TMP_DIR, target);
    try {
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setIdeaHome() {
    System.setProperty("idea.home", createTmpDir("tools/idea").toString());
  }

  private static Path createTmpDir(String p) {
    Path path = Paths.get(TMP_DIR, p);
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }
}
