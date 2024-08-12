/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAarTarget.aar_import;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbTargetBuilder.targetMap;
import static com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes.ANDROID_BINARY;

import com.android.tools.idea.model.AndroidModel;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.libraries.LibraryFileBuilder;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.android.sync.sdk.SdkUtil;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.MultiMap;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Android-specific sync integration tests. This test also covers {@link
 * com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer}
 */
@RunWith(JUnit4.class)
public class AndroidSyncTest extends BlazeAndroidIntegrationTestCase {

  private static final String ANDROID_28 = "android-28";

  private static final class TestProjectArguments {
    Sdk sdk;
    TargetMap targetMap;
    VirtualFile javaRoot;

    TestProjectArguments(Sdk sdk, TargetMap targetMap, VirtualFile javaRoot) {
      this.sdk = checkNotNull(sdk);
      this.targetMap = checkNotNull(targetMap);
      this.javaRoot = checkNotNull(javaRoot);
    }
  }

  public TestProjectArguments createTestProjectArguments() {
    Sdk android25 = MockSdkUtil.registerSdk(workspace, "25");

    RunManager runManager = RunManagerImpl.getInstanceImpl(getProject());
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration(
            "Blaze Android Binary Run Configuration",
            BlazeCommandRunConfigurationType.getInstance().getFactory());
    runManager.addConfiguration(runnerAndConfigurationSettings, false);
    BlazeCommandRunConfiguration configuration =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
    TargetInfo target =
        TargetInfo.builder(
                Label.create("//java/com/android:app"), ANDROID_BINARY.getKind().getKindString())
            .build();
    configuration.setTargetInfo(target);

    workspace.createFile(
        new WorkspacePath("java/com/google/Source.java"),
        "package com.google;",
        "public class Source {}");
    workspace.createFile(
        new WorkspacePath("java/com/google/Other.java"),
        "package com.google;",
        "public class Other {}");
    VirtualFile javaRoot = workspace.createDirectory(new WorkspacePath("java/com/google"));
    TargetMap targetMap =
        targetMap(
            android_library("//java/com/google:lib")
                .java_toolchain_version("8")
                .res("res/values/strings.xml")
                .src("Source.java", "Other.java"),
            android_binary("//java/com/android:app"));
    return new TestProjectArguments(android25, targetMap, javaRoot);
  }

  @Test
  public void testAndroidSyncAugmenterPresent() {
    assertThat(
            Arrays.stream(BlazeJavaSyncAugmenter.EP_NAME.getExtensions())
                .anyMatch(e -> e instanceof BlazeAndroidJavaSyncAugmenter))
        .isTrue();
  }

  @Test
  public void testSimpleSync_invalidSdkAndFailToReInstall() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    MockSdkUtil.registerSdk(workspace, "28", "5", MultiMap.create(), false);
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-28");
    setTargetMap(testEnvArgument.targetMap);
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
    List<Sdk> allSdks = BlazeSdkProvider.getInstance().getAllAndroidSdks();
    assertThat(allSdks).hasSize(1);
    assertThat(allSdks.get(0).getName()).isEqualTo(testEnvArgument.sdk.getName());
    errorCollector.assertIssues(
        String.format(
            AndroidSdkFromProjectView.NO_SDK_ERROR_TEMPLATE,
            ANDROID_28,
            Joiner.on(", ").join(AndroidSdkFromProjectView.getAvailableSdkTargetHashes(allSdks))));
    assertThat(ModuleManager.getInstance(getProject()).getModules()).isEmpty();
  }

  @Test
  public void testSimpleSync_invalidSdkAndSReInstall() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    MockSdkUtil.registerSdk(workspace, "28", "5", MultiMap.create(), true);
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-28");
    setTargetMap(testEnvArgument.targetMap);
    // When IDE re-add local SDK into {link @ProjectJdkTable}, it need access to embedded jdk. Set
    // path to mock jdk as embedded jdk path to avoid NPE.
    Sdk jdk = IdeaTestUtil.getMockJdk18();
    File jdkFile = new File(jdk.getHomePath());
    if (!jdkFile.exists()) {
      jdkFile.mkdirs();
      jdkFile.deleteOnExit();
      TestUtils.setSystemProperties(
          getTestRootDisposable(), "android.test.embedded.jdk", jdkFile.getPath());
    }
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
    assertSyncSuccess(testEnvArgument.targetMap, testEnvArgument.javaRoot);
    assertThat(SdkUtil.containsJarAndRes(BlazeSdkProvider.getInstance().findSdk(ANDROID_28)))
        .isTrue();
  }

  @Test
  public void testSimpleSync_noAndroidProjectData_workspaceModuleHasAndroidFacet() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-25");

    setTargetMap(testEnvArgument.targetMap);

    // A directory-only sync (a sync with SyncMode == NO_BUILD) won't generate blaze project data.
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.NO_BUILD)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    // Even if there was no sync data it's still good to create the .workspace module and attach an
    // AndroidFacet to it.  Some IDE functionalities will work as long as that's available, such as
    // logcat window.
    Module workspaceModule =
        ModuleManager.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(workspaceModule)).isNotNull();
  }

  @Test
  public void testSimpleSync_directoryOnlySyncAfterSuccessfulSync_reusesProjectData() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-25");

    setTargetMap(testEnvArgument.targetMap);
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
    assertSyncSuccess(testEnvArgument.targetMap, testEnvArgument.javaRoot);

    // Syncing again with SyncMode.NO_BUILD should still yield the same success.
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.NO_BUILD)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
    assertSyncSuccess(testEnvArgument.targetMap, testEnvArgument.javaRoot);
  }

  @Test
  public void testSimpleSync() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-25");

    setTargetMap(testEnvArgument.targetMap);
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());
    assertSyncSuccess(testEnvArgument.targetMap, testEnvArgument.javaRoot);
  }

  @Test
  public void testPackageNameParsing() {
    setProjectView(
        "directories:",
        "  java/com",
        "targets:",
        "  //java/com/withpackage:lib",
        "  //java/com/nopackage:lib",
        "android_sdk_platform: android-25");

    MockSdkUtil.registerSdk(workspace, "25");
    workspace.createFile(
        new WorkspacePath("java/com/withpackage/AndroidManifest.xml"),
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest package=\"com.android.testapp\"/>");
    workspace.createFile(
        new WorkspacePath("java/com/nopackage/AndroidManifest.xml"),
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest package=\"\"/>");
    TargetMap targetMap =
        targetMap(
            android_library("//java/com/withpackage:lib")
                .java_toolchain_version("8")
                .res("res/values/strings.xml")
                .manifest("AndroidManifest.xml"),
            android_library("//java/com/nopackage:lib")
                .java_toolchain_version("8")
                .res("res/values/strings.xml")
                .manifest("AndroidManifest.xml"));

    setTargetMap(targetMap);
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    // Resource module without a package name defined in its manifest uses a default name derived
    // from the module name as its resource package name.
    Module defaultAppIdModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.nopackage.lib");
    assertThat(defaultAppIdModule).isNotNull();
    assertThat(AndroidModel.get(defaultAppIdModule).getApplicationId()).isEqualTo("com.nopackage");

    // Resource module with a custom defined package name defined in its manifest uses it as the
    // resource package name.
    Module customAppIdModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.withpackage.lib");
    assertThat(customAppIdModule).isNotNull();
    assertThat(AndroidModel.get(customAppIdModule).getApplicationId())
        .isEqualTo("com.android.testapp");
  }

  /**
   * Validates that when an aar_import rule is used with a srcjar attribute set, then the project
   * library table includes the correct source root.
   */
  @Test
  public void testAarImportWithSources() {
    // Setup: a single aar_import target, along with an android_library that depends on it.
    MockSdkUtil.registerSdk(workspace, "25");
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:foo",
        "  //java/com/google:lib",
        "android_sdk_platform: android-25");

    workspace.createFile(new WorkspacePath("java/com/google/foo.aar"));
    workspace.createFile(new WorkspacePath("foo.srcjar"));
    workspace.createDirectory(new WorkspacePath("java/com/google"));
    workspace.createFile(
        new WorkspacePath("java/com/google/Source.java"),
        "package com.google;",
        "public class Source {}");
    workspace.createFile(
        new WorkspacePath("java/com/google/Other.java"),
        "package com.google;",
        "public class Other {}");

    // construct an aar file with a res file.
    LibraryFileBuilder.aar(workspaceRoot, "java/com/google/foo.aar")
        .addContent(
            "res/values/colors.xml",
            ImmutableList.of(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <color name=\"aarColor\">#ffffff</color>",
                "</resources>"))
        .build();

    // Most of the processing in the android plugin happens only when there is an android_library
    // or an android_binary to import. So we set up a android_library that depends on the aar that
    // we really want to test.
    TargetMap targetMap =
        targetMap(
            aar_import("//java/com/google:foo")
                .aar("foo.aar")
                .generated_jar("_aar/an_aar/classes_and_libs_merged.jar", "foo.srcjar"),
            android_library("//java/com/google:lib")
                .java_toolchain_version("8")
                .res("res/values/strings.xml")
                .src("Source.java", "Other.java")
                .dep("//java/com/google:foo"));
    setTargetMap(targetMap);

    // Run sync
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    // Validate results
    errorCollector.assertNoIssues();

    // There should be a single library corresponding to the aar.
    Library[] libraries =
        LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()).getLibraries();
    assertThat(libraries).hasLength(1);

    // Its source root must point to the given srcjar.
    String[] sourceUrls = libraries[0].getUrls(OrderRootType.SOURCES);
    assertThat(sourceUrls).hasLength(1);
    assertThat(sourceUrls[0]).endsWith("foo.srcjar!/");
  }

  private void assertSyncSuccess(TargetMap targetMap, VirtualFile javaRoot) {
    errorCollector.assertNoIssues();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType())
        .isEqualTo(WorkspaceType.ANDROID);

    ImmutableList<ContentEntry> contentEntries = getWorkspaceContentEntries();
    assertThat(contentEntries).hasSize(1);
    assertThat(findContentEntry(javaRoot)).isNotNull();
    assertThat(findContentEntry(javaRoot).getSourceFolders()).hasLength(1);

    // Check that the workspace is set to android
    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(workspaceModule)).isNotNull();

    // Check that a resource module was created
    Module resourceModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.google.lib");
    assertThat(resourceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(resourceModule)).isNotNull();

    // The default language level should be whatever is specified in the toolchain info
    assertThat(LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel())
        .isEqualTo(LanguageLevel.JDK_1_8);
  }
}
