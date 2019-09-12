/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.android.tools.idea.gradle.util.ContentEntries.findChildContentEntries;
import static com.android.tools.idea.io.FilePaths.getJarFromJarUrl;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.BASIC;
import static com.android.tools.idea.testing.TestProjectPaths.CENTRAL_BUILD_DIRECTORY;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_KAPT;
import static com.android.tools.idea.testing.TestProjectPaths.NESTED_MODULE;
import static com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.android.tools.idea.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.util.PropertiesFiles.savePropertiesToFile;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static com.intellij.pom.java.LanguageLevel.JDK_1_7;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.ProjectLibraries;
import com.android.tools.idea.gradle.actions.SyncProjectAction;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.BuildEnvironment;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.Lists;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LeakHunter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.mockito.ArgumentCaptor;

/**
 * Integration tests for 'Gradle Sync'.
 */
public class GradleSyncIntegrationTest extends GradleSyncIntegrationTestCase {
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    myIdeComponents = new IdeComponents(project);

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(singletonList(projectSettings));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // TODO(b/117274283): Remove when upgrading to Kotlin 1.3.30, or whichever version fixes KT-30076
      if ("syncWithKotlinDsl".equals(getTestName(true))) {
        return;
      }

      // Regression test: check the model doesn't hold on to dynamic proxies for Gradle Tooling API classes.
      Object model = DataNodeCaches.getInstance(getProject()).getCachedProjectData();
      if (model != null) {
        LeakHunter.checkLeak(model, Proxy.class, o -> Arrays.stream(
          o.getClass().getInterfaces()).anyMatch(clazz -> clazz.getName().contains("gradle.tooling")));
      }
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean useSingleVariantSyncInfrastructure() {
    return false;
  }

  @Override
  protected boolean useCompoundSyncInfrastructure() {
    return false;
  }

  // https://code.google.com/p/android/issues/detail?id=233038
  public void testLoadPlainJavaProject() throws Exception {
    prepareProjectForImport(PURE_JAVA_PROJECT);
    importProject();

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      ContentEntry[] entries = ModuleRootManager.getInstance(module).getContentEntries();
      assertThat(entries).named(module.getName() + " should have content entries").isNotEmpty();
    }
  }

  // See https://code.google.com/p/android/issues/detail?id=226802
  public void testNestedModule() throws Exception {
    // Sync must be successful.
    loadProject(NESTED_MODULE);

    Module rootModule = myModules.getModule(getName());
    GradleFacet gradleFacet = GradleFacet.getInstance(rootModule);
    // The root module should be considered a Java module.
    assertNotNull(gradleFacet);
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    assertNotNull(gradleModel);
    assertEquals(":", gradleModel.getGradlePath());
  }

  // See https://code.google.com/p/android/issues/detail?id=224985
  public void testNdkProjectSync() throws Exception {
    loadProject(HELLO_JNI);

    Module appModule = myModules.getAppModule();
    NdkFacet ndkFacet = NdkFacet.getInstance(appModule);
    assertNotNull(ndkFacet);

    ModuleRootManager rootManager = ModuleRootManager.getInstance(appModule);
    VirtualFile[] roots = rootManager.getSourceRoots(false /* do not include tests */);

    boolean cppSourceFolderFound = false;
    for (VirtualFile root : roots) {
      if (root.getName().equals("cpp")) {
        cppSourceFolderFound = true;
        break;
      }
    }

    assertTrue(cppSourceFolderFound);
  }

  public void testWithUserDefinedLibrarySources() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    String libraryNameRegex = "Gradle: com.google.guava:.*";
    Library library = libraries.findMatchingLibrary(libraryNameRegex);
    assertNotNull(library);

    String url = "jar://$USER_HOME$/fake-dir/fake-sources.jar!/";

    // add an extra source path.
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(url, SOURCES);
    ApplicationManager.getApplication().runWriteAction(libraryModel::commit);

    requestSyncAndWait();

    library = libraries.findMatchingLibrary(libraryNameRegex);
    assertNotNull(library);

    String[] urls = library.getUrls(SOURCES);
    assertThat(urls).asList().contains(url);
  }

  public void testSyncShouldNotChangeDependenciesInBuildFiles() throws Exception {
    loadSimpleApplication();

    File appBuildFilePath = getBuildFilePath("app");
    long lastModified = appBuildFilePath.lastModified();

    requestSyncAndWait();

    // See https://code.google.com/p/android/issues/detail?id=78628
    assertEquals(lastModified, appBuildFilePath.lastModified());
  }

  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testWithEmptyGradleSettingsFileInSingleModuleProject() throws Exception {
    loadProject(BASIC);
    createEmptyGradleSettingsFile();
    // Sync should be successful for single-module projects with an empty settings.gradle file.
    requestSyncAndWait();
  }

  private void createEmptyGradleSettingsFile() throws IOException {
    File settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE);
    assertTrue(delete(settingsFilePath));
    writeToFile(settingsFilePath, " ");
    assertAbout(file()).that(settingsFilePath).isFile();
    refreshProjectFiles();
  }

  public void testModuleJavaLanguageLevel() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module library1Module = myModules.getModule("library1");
    LanguageLevel javaLanguageLevel = getJavaLanguageLevel(library1Module);
    assertEquals(JDK_1_7, javaLanguageLevel);
  }

  @Nullable
  private static LanguageLevel getJavaLanguageLevel(@NotNull Module module) {
    return LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
  }

  public void testSetupEventInvoked() throws Exception {
    // Verify GradleSyncState
    GradleSyncListener listener = mock(GradleSyncListener.class);
    Project project = getProject();
    GradleSyncState.subscribe(project, listener);
    loadSimpleApplication();

    verify(listener, times(1)).setupStarted(project);
    reset(listener);

    // Verify ProjectSetUpTask
    listener = mock(GradleSyncListener.class);
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.testRequest();
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener);

    verify(listener, times(1)).setupStarted(project);
    reset(listener);
  }

  // https://code.google.com/p/android/issues/detail?id=227931
  public void testJarsFolderInExplodedAarIsExcluded() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);
    Collection<SyncIssue> issues = androidModel.getSyncIssues();
    assertThat(issues).isEmpty();

    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(getProject());
    assertNotNull(pluginInfo);
    GradleVersion pluginVersion = pluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);

    if (pluginVersion.compareIgnoringQualifiers("2.3.0") >= 0) {
      // Gradle plugin 2.3 stores exploded AARs in the user's cache. Excluding "jar" folder in the explode AAR is no longer needed, since
      // it is not inside the project.
      return;
    }

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library appCompat = libraries.findMatchingLibrary("Gradle: appcompat-v7.*");
    assertNotNull(appCompat);

    File jarsFolderPath = null;
    for (String url : appCompat.getUrls(CLASSES)) {
      if (url.startsWith(JAR_PROTOCOL_PREFIX)) {
        File jarPath = getJarFromJarUrl(url);
        assertNotNull(jarPath);
        jarsFolderPath = jarPath.getParentFile();
        break;
      }
    }
    assertNotNull(jarsFolderPath);

    ContentEntry[] contentEntries = ModuleRootManager.getInstance(appModule).getContentEntries();
    assertThat(contentEntries).hasLength(1);

    ContentEntry contentEntry = contentEntries[0];
    List<String> excludeFolderUrls = contentEntry.getExcludeFolderUrls();
    assertThat(excludeFolderUrls).contains(pathToIdeaUrl(jarsFolderPath));
  }

  public void ignore_testSourceAttachmentsForJavaLibraries() throws Exception {
    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library guava = libraries.findMatchingLibrary("Gradle: guava.*");
    assertNotNull(guava);

    String[] sources = guava.getUrls(SOURCES);
    assertThat(sources).isNotEmpty();
  }

  public void testLegacySourceGenerationIsDisabled() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    AndroidFacet facet = AndroidFacet.getInstance(appModule);
    assertNotNull(facet);

    try {
      ModuleSourceAutogenerating.getInstance(facet);
      fail("Shouldn't be able to construct a source generator for Gradle projects");
    }
    catch (IllegalArgumentException e) {
      assertEquals("app is built by an external build system and should not require the IDE to generate sources", e.getMessage());
    }
  }

  // Verifies that sync does not fail and user is warned when a project contains an Android module without variants.
  // See https://code.google.com/p/android/issues/detail?id=170722
  public void testWithAndroidProjectWithoutVariants() throws Exception {
    Project project = getProject();

    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    loadSimpleApplication();
    File appBuildFile = getBuildFilePath("app");

    // Remove all variants.
    appendToFile(appBuildFile, "android.variantFilter { variant -> variant.ignore = true }");

    requestSyncAndWait();

    // Verify user was warned.
    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).hasSize(1);

    SyncMessage message = messages.get(0);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(MessageType.ERROR)
                                            .hasMessageLine("The module 'app' is an Android project without build variants, and cannot be built.", 0);
    // @formatter:on

    // Verify AndroidFacet was removed.
    assertNull(AndroidFacet.getInstance(myModules.getAppModule()));
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  public void testWithCentralBuildDirectoryInRootModule() throws Exception {
    // In issue 74259, project sync fails because the "app" build directory is set to "CentralBuildDirectory/central/build", which is
    // outside the content root of the "app" module.
    File projectRootPath = prepareProjectForImport(CENTRAL_BUILD_DIRECTORY);

    // The bug appears only when the central build folder does not exist.
    File centralBuildDirPath = new File(projectRootPath, join("central", "build"));
    File centralBuildParentDirPath = centralBuildDirPath.getParentFile();
    delete(centralBuildParentDirPath);

    importProject();
    Module app = myModules.getAppModule();

    // Now we have to make sure that if project import was successful, the build folder (with custom path) is excluded in the IDE (to
    // prevent unnecessary file indexing, which decreases performance.)
    File[] sourceFolderPaths = ApplicationManager.getApplication().runReadAction(
      (Computable<File[]>)() -> {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(app);
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          Collection<ContentEntry> contentEntries =
            findChildContentEntries(centralBuildDirPath, Arrays.stream(rootModel.getContentEntries()));

          List<File> paths = Lists.newArrayList();

          for (SourceFolder source : contentEntries.stream().flatMap(contentEntry -> Arrays.stream(contentEntry.getSourceFolders()))
            .collect(Collectors.toSet())) {
            String path = urlToPath(source.getUrl());
            if (isNotEmpty(path)) {
              paths.add(toSystemDependentPath(path));
            }
          }
          return paths.toArray(new File[paths.size()]);
        }
        finally {
          rootModel.dispose();
        }
      });

    assertThat(sourceFolderPaths).isNotEmpty();
  }

  public void testGradleSyncActionAfterFailedSync() {
    IdeInfo ideInfo = myIdeComponents.mockApplicationService(IdeInfo.class);
    when(ideInfo.isAndroidStudio()).thenReturn(true);

    SyncProjectAction action = new SyncProjectAction();

    Presentation presentation = new Presentation();
    presentation.setEnabledAndVisible(false);
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getPresentation()).thenReturn(presentation);
    when(event.getProject()).thenReturn(getProject());

    assertFalse(GradleProjectInfo.getInstance(getProject()).isBuildWithGradle());
    action.update(event);
    assertFalse(presentation.isEnabledAndVisible());

    Module app = createModule("app");
    createAndAddGradleFacet(app);

    assertTrue(GradleProjectInfo.getInstance(getProject()).isBuildWithGradle());
    action.update(event);
    assertTrue(presentation.isEnabledAndVisible());
  }

  // Verify that sync issues were reported properly when there're unresolved dependencies
  // due to conflicts in variant attributes.
  // See b/64213214.
  public void testSyncIssueWithNonMatchingVariantAttributes() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    // DEPENDENT_MODULES project has two modules, app and lib, app module has dependency on lib module.
    loadProject(DEPENDENT_MODULES);

    // Define new buildType qa in app module.
    // This causes sync issues, because app depends on lib module, but lib module doesn't have buildType qa.
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid.buildTypes { qa { } }\n");

    try {
      requestSyncAndWait();
    }
    catch (AssertionError expected) {
      // Sync issues are expected.
    }

    // Verify sync issues are reported properly.
    List<NotificationData> messages = syncMessages.getNotifications();
    List<NotificationData> relevantMessages = messages.stream()
      .filter(m -> m.getNotificationCategory().equals(ERROR) &&
                   m.getTitle().equals("Unresolved dependencies") &&
                   m.getMessage().contains(
                     "Unable to resolve dependency for ':app@paidQa/compileClasspath': Could not resolve project :lib.\nAffected Modules:"))
      .collect(toList());
    assertThat(relevantMessages).isNotEmpty();
  }

  public void testSyncWithAARDependencyAddsSources() throws Exception {
    Project project = getProject();

    loadProject(SIMPLE_APPLICATION);

    Module appModule = getModule("app");

    ApplicationManager.getApplication().invokeAndWait(() -> runWriteCommandAction(
      project, () -> {
        GradleBuildModel buildModel = GradleBuildModel.get(appModule);

        buildModel.repositories().addFlatDirRepository(getTestDataPath() + "/res/aar-lib-sources/");

        String newDependency = "com.foo.bar:bar:0.1@aar";
        buildModel.dependencies().addArtifact(COMPILE, newDependency);
        buildModel.applyChanges();
      }));

    requestSyncAndWait();

    // Verify that the library has sources.
    ProjectLibraries libraries = new ProjectLibraries(getProject());
    String libraryNameRegex = "Gradle: com.foo.bar:bar:0.1@aar";
    Library library = libraries.findMatchingLibrary(libraryNameRegex);

    assertNotNull("Library com.foo.bar:bar:0.1 is missing", library);
    VirtualFile[] files = library.getFiles(SOURCES);
    assertThat(files).asList().hasSize(1);
  }

  // Verify that custom properties on local.properties are preserved after sync (b/70670394)
  public void testCustomLocalPropertiesPreservedAfterSync() throws Exception {
    Project project = getProject();

    loadProject(SIMPLE_APPLICATION);

    LocalProperties originalLocalProperties = new LocalProperties(project);
    Properties modified = getProperties(originalLocalProperties.getPropertiesFilePath());
    modified.setProperty("custom.property", "custom.value");
    savePropertiesToFile(modified, originalLocalProperties.getPropertiesFilePath(), null);
    LocalProperties modifiedLocalProperties = new LocalProperties(project);
    assertThat(modifiedLocalProperties.getProperty("custom.property")).isEqualTo("custom.value");

    requestSyncAndWait();

    LocalProperties afterSyncLocalProperties = new LocalProperties(project);
    assertThat(afterSyncLocalProperties.getProperty("custom.property")).isEqualTo("custom.value");
  }

  // Verify that previously reported sync issues are cleaned up as part of the next sync
  public void testSyncIssuesCleanup() throws Exception {
    loadSimpleApplication();

    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);
    SyncMessage oldSyncMessage = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR,
                                                 "A quick blown fix bumps over the lazy bug");
    syncMessages.report(oldSyncMessage);

    // Expect a successful sync, and that the old message should get cleaned up.
    requestSyncAndWait();
    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).isEmpty();
  }

  public void testSyncWithKotlinDsl() throws Exception {
    loadProject(KOTLIN_GRADLE_DSL);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSize(3, modules);
    for (Module module : modules) {
      ContentEntry[] entries = ModuleRootManager.getInstance(module).getContentEntries();
      assertThat(entries).named(module.getName() + " should have content entries").isNotEmpty();
    }
  }

  public void testSyncGetsGradlePluginModel() throws Exception {
    loadProject(SIMPLE_APPLICATION);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSize(2, modules);
    for (Module module : modules) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (module.getName().equals("app")) {
        assertThat(gradleFacet).isNotNull();
        Collection<String> plugins = gradleFacet.getGradleModuleModel().getGradlePlugins();
        // The main project module will not contain a list of plugins
        List<String> expectedPlugins = new ArrayList<>(Arrays.asList(
          "com.android.ide.gradle.model.builder.AndroidStudioToolingPlugin",
          "org.gradle.buildinit.plugins.BuildInitPlugin",
          "org.gradle.buildinit.plugins.WrapperPlugin",
          "org.gradle.api.plugins.HelpTasksPlugin",
          "com.android.build.gradle.api.AndroidBasePlugin",
          "org.gradle.language.base.plugins.LifecycleBasePlugin",
          "org.gradle.api.plugins.BasePlugin",
          "org.gradle.api.plugins.ReportingBasePlugin",
          "org.gradle.api.plugins.JavaBasePlugin",
          "com.android.build.gradle.AppPlugin",
          "org.gradle.plugins.ide.idea.IdeaPlugin",
          "com.android.build.gradle.internal.plugins.AppPlugin",
          "com.android.build.gradle.internal.plugins.VersionCheckPlugin"
        ));
        assertThat(plugins).containsExactlyElementsIn(expectedPlugins);
      }
      else {
        assertThat(gradleFacet).isNull();
      }
    }
  }

  public void testNoGradleFacetInTopLevelModule() throws Exception {
    loadSimpleApplication();
    Module topLevelModule = getModule(getProject().getName());
    assertNotNull(topLevelModule);
    // Verify that GradleFacet is not applied to top-level project.
    assertNull(GradleFacet.getInstance(topLevelModule));
  }

  public void testAgpVersionPopulated() throws Exception {
    loadSimpleApplication();
    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      // agpVersion is not available for Java modules.
      if (gradleFacet != null && androidFacet != null) {
        assertThat(gradleFacet.getConfiguration().LAST_SUCCESSFUL_SYNC_AGP_VERSION)
          .isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
        assertThat(gradleFacet.getConfiguration().LAST_KNOWN_AGP_VERSION)
          .isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
      }
    }
  }

  public void testOnlyLastKnownAgpVersionPopulatedForUnsuccessfulSync() throws Exception {
    // DEPENDENT_MODULES project has two modules, app and lib, app module has dependency on lib module.
    loadProject(DEPENDENT_MODULES);
    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        // Clean any LAST_SUCCESSFUL_SYNC_AGP_VERSION set by initial import.
        gradleFacet.getConfiguration().LAST_SUCCESSFUL_SYNC_AGP_VERSION = null;
        gradleFacet.getConfiguration().LAST_KNOWN_AGP_VERSION = null;
      }
    }

    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\ndependencies { implementation 'bad:bad:bad' }\n");

    try {
      requestSyncAndWait();
    }
    catch (AssertionError expected) {
      // Sync issues are expected.
    }

    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      // agpVersion is not available for Java modules.
      if (gradleFacet != null && androidFacet != null) {
        assertThat(gradleFacet.getConfiguration().LAST_SUCCESSFUL_SYNC_AGP_VERSION).isNull();
        assertThat(gradleFacet.getConfiguration().LAST_KNOWN_AGP_VERSION)
          .isEqualTo(BuildEnvironment.getInstance().getGradlePluginVersion());
      }
    }
  }

  // Verify buildscript classpath has been setup.
  public void testBuildScriptClasspathSetup() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    String projectPath = project.getBasePath();
    assertNotNull(projectPath);

    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assertNotNull(manager);
    AbstractExternalSystemLocalSettings<?> localSettings = manager.getLocalSettingsProvider().fun(project);
    ExternalProjectBuildClasspathPojo projectBuildClasspathPojo = localSettings.getProjectBuildClasspath().get(projectPath);

    // Verify that ExternalProjectBuildClasspathPojo is not null.
    assertNotNull(projectBuildClasspathPojo);

    List<String> projectClasspath = projectBuildClasspathPojo.getProjectBuildClasspath();
    Map<String, ExternalModuleBuildClasspathPojo> moduleClasspath = projectBuildClasspathPojo.getModulesBuildClasspath();

    // Verify that project classpath is not empty.
    assertThat(projectClasspath).isNotEmpty();

    // Verify that each sub-module has non-empty classpath entry.
    String rootModuleDir = toCanonicalPath(projectPath);
    String appModuleDir = toCanonicalPath(new File(projectPath, "app").getPath());
    assertThat(moduleClasspath.keySet()).containsExactly(rootModuleDir, appModuleDir);
    assertThat(moduleClasspath.get(rootModuleDir).getEntries()).isNotEmpty();
    assertThat(moduleClasspath.get(appModuleDir).getEntries()).isNotEmpty();
  }

  // Verify that execute task.
  public void testExecuteGradleTask() throws Exception {
    loadSimpleApplication();

    Project project = getProject();
    ExternalSystemTaskId taskId = mock(ExternalSystemTaskId.class);
    when(taskId.findProject()).thenReturn(project);

/* b/137231583
    // Verify that the task "help" can be found and executed.
    assertTrue(new AndroidGradleTaskManager().executeTasks(taskId, singletonList("help"), project.getBasePath(), null, null,
                                                           new ExternalSystemTaskNotificationListenerAdapter() {
                                                           }));
b/137231583 */
  }

  public void testNDKModelRefreshedWithModifiedCMakeLists() throws Exception {
    loadProject(HELLO_JNI);
    // Verify artifacts is not empty.
    assertThat(getNativeArtifacts()).isNotEmpty();

    // Write empty CMakeLists file so that no artifacts can be built.
    File cmakeFile = new File(getProjectFolderPath(), join("app", "src", "main", "cpp", "CMakeLists.txt"));
    writeToFile(cmakeFile, "");
    requestSyncAndWait();

    // Verify Ndk model doesn't contain any artifact.
    assertThat(getNativeArtifacts()).isEmpty();
  }

  public void testWithPreSyncCheckFailure() throws Exception {
    Project project = getProject();

    // Force a pre sync error
    String errorMessage = "This is a pre sync check error message";
    PreSyncCheckResult result = PreSyncCheckResult.failure(errorMessage);
    GradleSyncInvoker spyInvoker = spy(GradleSyncInvoker.getInstance());
    when(spyInvoker.runPreSyncChecks(project)).thenReturn(result);
    myIdeComponents.replaceApplicationService(GradleSyncInvoker.class, spyInvoker);

    // Spy on SyncView manager to confirm it is displaying the error message
    SyncViewManager spyViewManager = spy(ServiceManager.getService(project, SyncViewManager.class));
    myIdeComponents.replaceProjectService(SyncViewManager.class, spyViewManager);

    String syncError = loadProjectAndExpectSyncError(SIMPLE_APPLICATION);
    assertEquals(errorMessage, syncError);

    // Make sure the error is processed in sync view
    ArgumentCaptor<BuildEvent> buildEventCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    verify(spyViewManager, times(2)).onEvent(any(Object.class), buildEventCaptor.capture());
    List<BuildEvent> buildEvents = buildEventCaptor.getAllValues();
    assertSize(2, buildEvents);
    assertThat(buildEvents.get(0)).isInstanceOf(StartBuildEvent.class);
    assertThat(buildEvents.get(1)).isInstanceOf(FinishBuildEvent.class);
    FinishBuildEvent finishEvent = (FinishBuildEvent)buildEvents.get(1);
    assertThat(finishEvent.getResult()).isInstanceOf(FailureResult.class);
    assertEquals(errorMessage, finishEvent.getMessage());
  }

  public void testFinishBuildEventOnlyCreatedOnce() throws Exception {
    Project project = getProject();
    // Spy on SyncView manager to capture the build events.
    SyncViewManager spyViewManager = spy(ServiceManager.getService(project, SyncViewManager.class));
    myIdeComponents.replaceProjectService(SyncViewManager.class, spyViewManager);

    // Invoke Gradle sync.
    loadSimpleApplication();

    ArgumentCaptor<BuildEvent> buildEventCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    verify(spyViewManager, atLeastOnce()).onEvent(any(Object.class), buildEventCaptor.capture());

    // Verify that FinishBuildEvent was created only once.
    List<BuildEvent> buildEvents = buildEventCaptor.getAllValues().stream().filter(FinishBuildEvent.class::isInstance).collect(toList());
    assertThat(buildEvents).hasSize(1);
    assertThat(buildEvents.get(0).getMessage()).isEqualTo("successful");
  }

  public void testContentRootDataNodeWithBuildSrcModule() throws Exception {
    loadSimpleApplication();

    // Create buildSrc folder under root project.
    File buildSrcDir = new File(getProject().getBasePath(), "buildSrc");
    File buildFile = new File(buildSrcDir, "build.gradle");
    writeToFile(buildFile, "repositories {}");

    // Request Gradle sync.
    requestSyncAndWait();

    // Verify that buildSrc modules exists.
    Module buildSrcModule = getModule("buildSrc");
    assertNotNull(buildSrcModule);
    DataNode<ModuleData> moduleData = ExternalSystemApiUtil.findModuleData(buildSrcModule, GradleConstants.SYSTEM_ID);
    assertNotNull(moduleData);

    // Verify that ContentRootData DataNode is created for buildSrc module.
    Collection<DataNode<ContentRootData>> contentRootData = ExternalSystemApiUtil.findAll(moduleData, ProjectKeys.CONTENT_ROOT);
    assertThat(contentRootData).hasSize(1);
    assertThat(contentRootData.iterator().next().getData().getRootPath()).isEqualTo(buildSrcDir.getPath());
  }

  public void testViewBindingOptionsAreCorrectlyVisibleFromIDE() throws Exception {
    loadSimpleApplication();

    // Default option value should be false.
    assertFalse(AndroidModuleModel.get(myModules.getAppModule()).getAndroidProject().getViewBindingOptions().isEnabled());

    // Change the option in the build file and re-sync
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid { viewBinding { enabled true }\n}");
    requestSyncAndWait();

    // Check that the new option is visible from the IDE.
    assertTrue(AndroidModuleModel.get(myModules.getAppModule()).getAndroidProject().getViewBindingOptions().isEnabled());
  }

  public void testProjectSyncIssuesAreCorrectlyReported() throws Exception {
    loadProject(HELLO_JNI);

    File appBuildFile = getBuildFilePath("app");

    // Set the ndkVersion to something that doesn't exist.
    appendToFile(appBuildFile, "android.ndkVersion 'i am a good version'");

    String expectedFailure = requestSyncAndGetExpectedFailure();

    assertThat(expectedFailure).isEqualTo("setup project failed: Sync issues found!\n" +
                                          "Module 'app':\nRequested NDK version 'i am a good version' could not be parsed\n");
  }

  public void testKaptIsEnabled() throws Exception {
    loadProject(KOTLIN_KAPT);

    GradleModuleModel appModel = GradleFacet.getInstance(getModule("app")).getGradleModuleModel();
    assertTrue(appModel.isKaptEnabled());

    GradleModuleModel rootModel = GradleFacet.getInstance(getModule("lib")).getGradleModuleModel();
    assertFalse(rootModel.isKaptEnabled());
  }

  @NotNull
  private List<NativeArtifact> getNativeArtifacts() {
    return NdkModuleModel.get(getModule("app")).getVariants().stream()
      .map(it -> it.getArtifacts())
      .flatMap(Collection::stream)
      .collect(toList());
  }
}
