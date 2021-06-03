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
import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.APP_WITH_BUILDSRC;
import static com.android.tools.idea.testing.TestProjectPaths.BASIC;
import static com.android.tools.idea.testing.TestProjectPaths.CUSTOM_BUILD_SCRIPT_DEPS;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_KAPT;
import static com.android.tools.idea.testing.TestProjectPaths.NESTED_MODULE;
import static com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_UNRESOLVED_DEPENDENCY;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.android.tools.idea.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.util.PropertiesFiles.savePropertiesToFile;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.ProjectLibraries;
import com.android.tools.idea.gradle.actions.SyncProjectAction;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.idea.issues.JdkImportCheckException;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.BuildEnvironment;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.TestGradleSyncListener;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.utils.FileUtils;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;
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

    myIdeComponents = new IdeComponents(project, getTestRootDisposable());

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    String externalProjectPath = toCanonicalPath(project.getBasePath());
    projectSettings.setExternalProjectPath(externalProjectPath);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(singletonList(projectSettings));
  }

  @Override
  public void tearDown() throws Exception {
    try {
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

  // https://code.google.com/p/android/issues/detail?id=233038
  public void testLoadPlainJavaProject() throws Exception {
    prepareProjectForImport(PURE_JAVA_PROJECT);

    // Delete local.properties if exists
    File localProps = new File(getProjectFolderPath(), "local.properties");
    WriteAction.runAndWait(() -> LocalFileSystem.getInstance().findFileByIoFile(localProps).delete(this));
    assertFalse(localProps.exists());

    // Ensure import works with no local.properties created beforehand
    importProject();

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      ContentEntry[] entries = ModuleRootManager.getInstance(module).getContentEntries();
      assertThat(entries).named(module.getName() + " should have content entries").isNotEmpty();
    }

    // Ensure no local.properties was created in IDEA.
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      assertFalse(localProps.exists());
    }
  }

  // See https://code.google.com/p/android/issues/detail?id=226802
  public void testNestedModule() throws Exception {
    // Sync must be successful.
    loadProject(NESTED_MODULE);

    Module rootModule = TestModuleUtil.findModule(getProject(), getProject().getName());
    GradleFacet gradleFacet = GradleFacet.getInstance(rootModule);
    // The root module should be considered a Java module.
    assertNotNull(gradleFacet);
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    assertNotNull(gradleModel);
    assertEquals(":", gradleModel.getGradlePath());
  }

  public void testProjectWithCustomBuildScriptDeps() throws Exception {
    // https://youtrack.jetbrains.com/issue/IDEA-228545
    loadProject(CUSTOM_BUILD_SCRIPT_DEPS);
    requestSyncAndWait();
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
    Module library1Module = TestModuleUtil.findModule(getProject(), "library1");
    LanguageLevel javaLanguageLevel = LanguageLevelUtil.getCustomLanguageLevel(library1Module);
    assertEquals(JDK_1_8, javaLanguageLevel);
  }

  // https://code.google.com/p/android/issues/detail?id=227931
  public void testJarsFolderInExplodedAarIsExcluded() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);

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

    Path jarsFolderPath = null;
    for (String url : appCompat.getUrls(CLASSES)) {
      if (url.startsWith(JAR_PROTOCOL_PREFIX)) {
        Path jarPath = FilePaths.getJarFromJarUrl(url);
        assertNotNull(jarPath);
        jarsFolderPath = jarPath.getParent();
        break;
      }
    }
    assertNotNull(jarsFolderPath);

    ContentEntry[] contentEntries = ModuleRootManager.getInstance(appModule).getContentEntries();
    assertThat(contentEntries).hasLength(1);

    ContentEntry contentEntry = contentEntries[0];
    List<String> excludeFolderUrls = contentEntry.getExcludeFolderUrls();
    assertThat(excludeFolderUrls).contains(FilePaths.pathToIdeaUrl(jarsFolderPath.toFile()));
  }

  public void ignore_testSourceAttachmentsForJavaLibraries() throws Exception {
    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library guava = libraries.findMatchingLibrary("Gradle: guava.*");
    assertNotNull(guava);

    String[] sources = guava.getUrls(SOURCES);
    assertThat(sources).isNotEmpty();
  }

  // Verifies that sync does not fail and user is warned when a project contains an Android module without variants.
  // See https://code.google.com/p/android/issues/detail?id=170722
  public void testWithAndroidProjectWithoutVariants() throws Exception {
    Project project = getProject();

    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());

    loadSimpleApplication();
    File appBuildFile = getBuildFilePath("app");

    // Remove all variants.
    appendToFile(appBuildFile, "android.variantFilter { variant -> variant.ignore = true }");

    String failure = requestSyncAndGetExpectedFailure();
    assertThat(failure).contains("No variants found for ':app'. Check build files to ensure at least one variant exists.");
  }

  public void testGradleSyncActionAfterFailedSync() throws Exception {
    loadProject(SIMPLE_APPLICATION);

    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "**error***");
    requestSyncAndGetExpectedFailure();

    SyncProjectAction action = new SyncProjectAction();
    Presentation presentation = new Presentation();
    presentation.setEnabledAndVisible(false);
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getPresentation()).thenReturn(presentation);
    when(event.getProject()).thenReturn(getProject());
    action.update(event);
    assertTrue(presentation.isEnabledAndVisible());
  }

  // Verify that sync issues were reported properly when there're unresolved dependencies
  // due to conflicts in variant attributes.
  // See b/64213214.
  public void testSyncIssueWithNonMatchingVariantAttributes() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());

    // DEPENDENT_MODULES project has two modules, app and lib, app module has dependency on lib module.
    prepareProjectForImport(DEPENDENT_MODULES, null, null, null);
    // Define new buildType qa in app module.
    // This causes sync issues, because app depends on lib module, but lib module doesn't have buildType qa.
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid.buildTypes { qa { } }\n");
    importProject();
    prepareProjectForTest(getProject(), "app");

    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "basicQa");

    // Verify sync issues are reported properly.
    List<NotificationData> messages = syncMessages.getNotifications();
    List<NotificationData> relevantMessages = messages.stream()
      .filter(m -> m.getTitle().equals("Unresolved dependencies") &&
                   m.getMessage().contains(
                     "Unable to resolve dependency for ':app@basicQa/compileClasspath': Could not resolve project :lib.\nAffected Modules:"))
      .collect(toList());
    assertThat(relevantMessages).isNotEmpty();
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
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());
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

  /* TODO(b/142753914): GradleSyncIntegrationTest.testWithKotlinMpp fails with Kotlin version 1.3.60-withExperimentalGoogleExtensions-20191014
  public void testWithKotlinMpp() throws Exception {
    loadProject(KOTLIN_MPP);

    // Verify that 7 modules are created.
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSize(7, modules);

    // Verify module names are as expected.
    List<String> moduleNames = Arrays.stream(modules).map(Module::getName).collect(toList());
    List<String> expectedModuleNames = asList("kotlinMpp", "app", "app_commonMain", "app_commonTest",
                                              "shared", "shared_commonMain", "shared_commonTest");
    assertThat(moduleNames).containsExactlyElementsIn(expectedModuleNames);
  }
  */

  public void testSyncGetsGradlePluginModel() throws Exception {
    loadProject(SIMPLE_APPLICATION);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSize(2, modules);
    for (Module module : modules) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (module.getName().contains("app")) {
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
          "org.gradle.api.plugins.JavaBasePlugin$Inject",
          "org.gradle.api.plugins.JvmEcosystemPlugin",
          "com.android.build.gradle.AppPlugin",
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

  public void testNotLastKnownAgpVersionPopulatedForUnsuccessfulSync() throws Exception {
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
    appendToFile(appBuildFile, "\n@invalidDsl { implementation 'bad:bad:bad' }\n");

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
        assertThat(gradleFacet.getConfiguration().LAST_KNOWN_AGP_VERSION).isNull();
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

    // Verify that the task "help" can be found and executed.
/* b/154962759
    assertTrue(new AndroidGradleTaskManager().executeTasks(taskId, singletonList("help"), project.getBasePath(), null, null,
                                                           new ExternalSystemTaskNotificationListenerAdapter() {
                                                           }));
b/154962759 */
  }

  public void testWithPreSyncCheckFailure() throws Exception {
    Project project = getProject();

    SimulatedSyncErrors.registerSyncErrorToSimulate(new JdkImportCheckException("Presync checks failed"));

    // Spy on SyncView manager to confirm it is displaying the error message
    SyncViewManager spyViewManager = spy(project.getService(SyncViewManager.class));
    ServiceContainerUtil.replaceService(project, SyncViewManager.class, spyViewManager, getTestRootDisposable());

    String syncError = loadProjectAndExpectSyncError(SIMPLE_APPLICATION);
    assertThat(syncError).startsWith("Presync checks failed\n");
  }

  public void testFinishBuildEventOnlyCreatedOnce() throws Exception {
    Project project = getProject();
    // Spy on SyncView manager to capture the build events.
    SyncViewManager spyViewManager = spy(project.getService(SyncViewManager.class));
    myIdeComponents.replaceProjectService(SyncViewManager.class, spyViewManager);

    // Invoke Gradle sync.
    loadSimpleApplication();

    ArgumentCaptor<BuildEvent> buildEventCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    verify(spyViewManager, atLeastOnce()).onEvent(any(Object.class), buildEventCaptor.capture());

    // Verify that FinishBuildEvent was created only once.
    List<BuildEvent> buildEvents = buildEventCaptor.getAllValues().stream().filter(FinishBuildEvent.class::isInstance).collect(toList());
    assertThat(buildEvents).hasSize(1);
    assertThat(buildEvents.get(0).getMessage()).isEqualTo("finished");
  }

  public void testStartAndFinishBuildEventHasSameBuildId() throws Exception {
    Project project = getProject();
    // Spy on SyncView manager to capture the build events.
    SyncViewManager spyViewManager = spy(project.getService(SyncViewManager.class));
    myIdeComponents.replaceProjectService(SyncViewManager.class, spyViewManager);

    // Invoke Gradle sync.
    loadSimpleApplication();

    ArgumentCaptor<Object> startIdCaptor = ArgumentCaptor.forClass(Object.class);
    ArgumentCaptor<Object> finishIdCaptor = ArgumentCaptor.forClass(Object.class);

    verify(spyViewManager).onEvent(startIdCaptor.capture(), any(StartBuildEvent.class));
    verify(spyViewManager).onEvent(finishIdCaptor.capture(), any(FinishBuildEvent.class));

    // Verify that start build event and finish build event are created for the same build id.
    assertEquals(finishIdCaptor.getValue(), startIdCaptor.getValue());
  }

  public void testSyncWithBuildSrcModule() throws Exception {
    loadProject(APP_WITH_BUILDSRC);

    // Verify that buildSrc modules exists.
    Module buildSrcModule = getModule("buildSrc");
    assertNotNull(buildSrcModule);
    DataNode<ModuleData> moduleData = GradleUtil.findGradleModuleData(buildSrcModule);
    assertNotNull(moduleData);

    // Ensure no local.properties was created.
    assertFalse(new File(getProjectFolderPath(), "buildSrc/local.properties").exists());

    // Verify that ContentRootData DataNode is created for buildSrc module.
    Collection<DataNode<ContentRootData>> contentRootData = ExternalSystemApiUtil.findAll(moduleData, ProjectKeys.CONTENT_ROOT);
    File buildSrcDir = new File(getProject().getBasePath(), "buildSrc");
    if (isModulePerSourceSet()) {
      String buildSrcDirPath = buildSrcDir.getPath();
      assertThat(ContainerUtil.map(contentRootData, e -> e.getData().getRootPath())).containsExactly(
        buildSrcDirPath,
        buildSrcDirPath + "/src/main/java",
        buildSrcDirPath + "/src/main/groovy",
        buildSrcDirPath + "/src/main/resources",
        buildSrcDirPath + "/src/test/java",
        buildSrcDirPath + "/src/test/groovy",
        buildSrcDirPath + "/src/test/resources"
      );
    } else {
      assertThat(contentRootData).hasSize(1);
      assertThat(contentRootData.iterator().next().getData().getRootPath())
        .isEqualTo(FileUtils.toSystemIndependentPath(buildSrcDir.getPath()));
    }

    // Verify that buildSrc/lib1 has dependency on buildSrc/lib2.
    Module lib1Module = getModule("lib1");
    assertAbout(moduleDependencies()).that(lib1Module).hasDependency(getModule("lib2").getName(), DependencyScope.COMPILE, false);
  }

  public void testViewBindingOptionsAreCorrectlyVisibleFromIDE() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    // Default option value should be false.
    assertFalse(AndroidModuleModel.get(appModule).getAndroidProject().getViewBindingOptions().getEnabled());

    // Change the option in the build file and re-sync
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid { viewBinding { enabled true }\n}");
    requestSyncAndWait();

    // Check that the new option is visible from the IDE.
    assertTrue(AndroidModuleModel.get(appModule).getAndroidProject().getViewBindingOptions().getEnabled());
  }

  public void testDependenciesInfoOptionsAreCorrectlyVisibleFromIDE() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    // Default option value should be true (at least at the moment)
    assertTrue(AndroidModuleModel.get(appModule).getAndroidProject().getDependenciesInfo().getIncludeInApk());
    assertTrue(AndroidModuleModel.get(appModule).getAndroidProject().getDependenciesInfo().getIncludeInBundle());

    // explicitly set the option
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid { dependenciesInfo { includeInApk false\nincludeInBundle false } }");
    requestSyncAndWait();

    assertFalse(AndroidModuleModel.get(appModule).getAndroidProject().getDependenciesInfo().getIncludeInApk());
    assertFalse(AndroidModuleModel.get(appModule).getAndroidProject().getDependenciesInfo().getIncludeInBundle());
  }

  public void testKaptIsEnabled() throws Exception {
    loadProject(KOTLIN_KAPT);

    GradleModuleModel appModel = GradleFacet.getInstance(getModule("app")).getGradleModuleModel();
    assertTrue(appModel.isKaptEnabled());

    GradleModuleModel rootModel = GradleFacet.getInstance(getModule("lib")).getGradleModuleModel();
    assertFalse(rootModel.isKaptEnabled());
  }

  public void testExceptionsCreateFailedBuildFinishedEvent() throws Exception {
    loadSimpleApplication();
    SyncViewManager viewManager = mock(SyncViewManager.class);
    new IdeComponents(getProject()).replaceProjectService(SyncViewManager.class, viewManager);
    SimulatedSyncErrors.registerSyncErrorToSimulate(new RuntimeException("Fake sync error"));

    requestSyncAndGetExpectedFailure();

    ArgumentCaptor<BuildEvent> eventCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    // FinishBuildEvents are not consumed immediately by AbstractOutputMessageDispatcher.onEvent(), thus we need to wait
    verify(viewManager, after(1000).atLeast(0)).onEvent(any(), eventCaptor.capture());

    List<BuildEvent> events = eventCaptor.getAllValues();
    // There should be at least two events
    assertThat(events.size()).isAtLeast(2);
    // The first event should be a StartBuildEvent
    assertThat(events.get(0)).isInstanceOf(StartBuildEvent.class);
    // And the last event should be a FinishBuildEvent. There may be other progress events in between.
    assertThat(events.get(events.size()-1)).isInstanceOf(FinishBuildEvent.class);
    FinishBuildEvent event = (FinishBuildEvent)events.get(events.size()-1);
    FailureResult failureResult = (FailureResult)event.getResult();
    assertThat(failureResult.getFailures()).isNotEmpty();
    assertThat(failureResult.getFailures().get(0).getMessage()).contains("Fake sync error");
  }

  public void testUnresolvedDependency() throws Exception {
    prepareProjectForImport(SIMPLE_APPLICATION_UNRESOLVED_DEPENDENCY, null, null, null);
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());

    Project project = getProject();
    TestGradleSyncListener syncListener = EdtTestUtil.runInEdtAndGet(() -> {
      GradleProjectImporter.Request request = new GradleProjectImporter.Request(project);
      GradleProjectImporter.configureNewProject(project);
      GradleProjectImporter.getInstance().importProjectNoSync(request);
      return AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest());
    });

    assertFalse(AndroidGradleTests.syncFailed(syncListener));
    List<NotificationData> notifications = syncMessages.getNotifications();
    assertThat(notifications.get(0).getMessage()).startsWith("Failed to resolve: unresolved:dependency:99.9");
  }

  /**
   * Verify that daemons can be stopped (b/150790550).
   * @throws Exception
   */
  public void testDaemonStops() throws Exception {
    loadSimpleApplication();
    GradleDaemonServices.stopDaemons();
    assertThat(areGradleDaemonsRunning()).isFalse();
    requestSyncAndWait();
    assertThat(areGradleDaemonsRunning()).isTrue();
    GradleDaemonServices.stopDaemons();
    assertThat(areGradleDaemonsRunning()).isFalse();
  }

  public void testSyncWithAARDependencyAddsSources() throws Exception {
    Project project = getProject();

    loadProject(SIMPLE_APPLICATION);

    Module appModule = getModule("app");

    ApplicationManager.getApplication().invokeAndWait(() -> runWriteCommandAction(
      project, () -> {
        try {
          VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
          VirtualFile buildGradle =  com.android.tools.idea.gradle.util.GradleUtil.findGradleBuildFile(projectDir);

          String currentBuildFileContents = VfsUtilCore.loadText(buildGradle);

          String newBuildFileContents =
            currentBuildFileContents +
            "\nallprojects { repositories { maven { url \"file://" + getTestDataPath() + "/res/aar-lib-sources/maven/\" }}}";

          VfsUtil.saveText(buildGradle, newBuildFileContents);

          GradleBuildModel gradleBuildModel = ProjectBuildModel.get(project).getModuleBuildModel(appModule);
          gradleBuildModel.dependencies().addArtifact("implementation", "com.test:bar:0.1@aar");

          gradleBuildModel.applyChanges();
        } catch (Exception e) {
          e.printStackTrace(System.out);
          fail(e.getMessage());
        }
      }));

    requestSyncAndWait();

    // Verify that the library has sources.
    ProjectLibraries libraries = new ProjectLibraries(getProject());
    String libraryNameRegex = "Gradle: com.test:bar:0.1@aar";
    Library library = libraries.findMatchingLibrary(libraryNameRegex);

    assertNotNull("Library com.test:bar:0.1 is missing", library);
    VirtualFile[] files = library.getFiles(SOURCES);
    assertThat(files).asList().hasSize(1);
  }

  private boolean isModulePerSourceSet() {
    return !IdeInfo.getInstance().isAndroidStudio();
  }
}
