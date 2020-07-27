/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.testing;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.io.Files.asCharSink;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.testutils.TestUtils;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.npw.template.KotlinVersionProvider;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.base.Strings;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ThrowableConsumer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.RegEx;
import junit.framework.TestCase;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidGradleTests {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTests.class);
  private static final Pattern REPOSITORIES_PATTERN = Pattern.compile("repositories[ ]+\\{");
  private static final Pattern GOOGLE_REPOSITORY_PATTERN = Pattern.compile("google\\(\\)");
  private static final Pattern JCENTER_REPOSITORY_PATTERN = Pattern.compile("jcenter\\(\\)");
  private static final Pattern MAVEN_REPOSITORY_PATTERN = Pattern.compile("maven \\{.*http.*\\}");

  /**
   * @deprecated use {@link AndroidGradleTests#updateToolingVersionsAndPaths(java.io.File) instead.}.
   */
  @Deprecated
  public static void updateGradleVersions(@NotNull File folderRootPath) throws IOException {
    updateToolingVersionsAndPaths(folderRootPath, null, null);
  }

  public static void updateToolingVersionsAndPaths(@NotNull File folderRootPath) throws IOException {
    updateToolingVersionsAndPaths(folderRootPath, null, null);
  }

  public static void updateToolingVersionsAndPaths(@NotNull File folderRootPath,
                                                   @Nullable String gradleVersion,
                                                   @Nullable String gradlePluginVersion) throws IOException {
    updateToolingVersionsAndPaths(folderRootPath, null, gradleVersion, gradlePluginVersion);
  }

  public static void updateToolingVersionsAndPaths(@NotNull File path,
                                                   @Nullable String repositories,
                                                   @Nullable String gradleVersion,
                                                   @Nullable String gradlePluginVersion)
    throws IOException {
    updateToolingVersionsAndPaths(path, true, repositories, gradleVersion, gradlePluginVersion);
  }

  private static void updateToolingVersionsAndPaths(@NotNull File path,
                                                    boolean isRoot,
                                                    @Nullable String localRepositories,
                                                    @Nullable String gradleVersion,
                                                    @Nullable String gradlePluginVersion)
    throws IOException {
    if (path.isDirectory()) {
      if (isRoot || new File(path, FN_SETTINGS_GRADLE).exists() || new File(path, FN_SETTINGS_GRADLE_KTS).exists()) {
        // Override settings just for tests (e.g. sdk.dir)
        updateLocalProperties(path, getSdk());
        // We need the wrapper for import to succeed
        createGradleWrapper(path, gradleVersion != null ? gradleVersion : GRADLE_LATEST_VERSION);
      }
      for (File child : notNullize(path.listFiles())) {
        updateToolingVersionsAndPaths(child, false, localRepositories, gradleVersion, gradlePluginVersion);
      }
      return;
    }

    BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();
    String pluginVersion = gradlePluginVersion != null ? gradlePluginVersion : buildEnvironment.getGradlePluginVersion();
    String kotlinVersion = KotlinVersionProvider.getInstance().getKotlinVersionForGradle();

    if (path.getPath().endsWith(DOT_GRADLE) && path.isFile()) {
      String contentsOrig = Files.toString(path, StandardCharsets.UTF_8);
      String contents = contentsOrig;
      if (localRepositories == null) {
        localRepositories = getLocalRepositoriesForGroovy();
      }

      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   pluginVersion);

      contents = replaceRegexGroup(contents, "ext.kotlin_version ?= ?['\"](.+)['\"]", kotlinVersion);

      // App compat version needs to match compile SDK
      String appCompatMainVersion = BuildEnvironment.getInstance().getCompileSdkVersion();
      // TODO(145548476): convert to androidx
      if (!appCompatMainVersion.equals("29")) {
        contents = replaceRegexGroup(contents, "com.android.support:appcompat-v7:(\\+)", appCompatMainVersion + ".+");
      }

      contents = updateBuildToolsVersion(contents);
      contents = updateCompileSdkVersion(contents);
      contents = updateTargetSdkVersion(contents);
      contents = updateMinSdkVersion(contents);
      contents = updateLocalRepositories(contents, localRepositories);

      if (!contents.equals(contentsOrig)) {
        asCharSink(path, StandardCharsets.UTF_8).write(contents);
      }
    }
    else if (path.getPath().endsWith(EXT_GRADLE_KTS) && path.isFile()) {
      String contentsOrig = Files.toString(path, StandardCharsets.UTF_8);
      String contents = contentsOrig;
      if (localRepositories == null) {
        localRepositories = getLocalRepositoriesForKotlin();
      }

      contents = replaceRegexGroup(contents, "classpath\\(['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   pluginVersion);
      contents = replaceRegexGroup(contents, "[a-zA-Z]+\\s*\\(?\\s*['\"]org.jetbrains.kotlin:kotlin[a-zA-Z\\-]*:(.+)['\"]",
                                   kotlinVersion);
      // "implementation"(kotlin("stdlib", "1.3.61"))
      contents =
        replaceRegexGroup(contents, "\"[a-zA-Z]+\"\\s*\\(\\s*kotlin\\(\"[a-zA-Z\\-]+\",\\s*\"(.+)\"", kotlinVersion);
      contents = replaceRegexGroup(contents, "\\(\"com.android.application\"\\) version \"(.+)\"", pluginVersion);
      contents = replaceRegexGroup(contents, "\\(\"com.android.library\"\\) version \"(.+)\"", pluginVersion);
      contents = replaceRegexGroup(contents, "buildToolsVersion\\(\"(.+)\"\\)", buildEnvironment.getBuildToolsVersion());
      contents = replaceRegexGroup(contents, "compileSdkVersion\\((.+)\\)", buildEnvironment.getCompileSdkVersion());
      contents = replaceRegexGroup(contents, "targetSdkVersion\\((.+)\\)", buildEnvironment.getTargetSdkVersion());
      contents = replaceRegexGroup(contents, "minSdkVersion\\((.*)\\)", buildEnvironment.getMinSdkVersion());
      contents = updateLocalRepositories(contents, localRepositories);

      if (!contents.equals(contentsOrig)) {
        asCharSink(path, StandardCharsets.UTF_8).write(contents);
      }
    }
  }

  @NotNull
  public static String updateBuildToolsVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "buildToolsVersion ['\"](.+)['\"]", BuildEnvironment.getInstance().getBuildToolsVersion());
  }

  @NotNull
  public static String updateCompileSdkVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "compileSdkVersion[ (]([0-9]+)", BuildEnvironment.getInstance().getCompileSdkVersion());
  }

  @NotNull
  public static String updateTargetSdkVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "targetSdkVersion[ (]([0-9]+)", BuildEnvironment.getInstance().getTargetSdkVersion());
  }

  @NotNull
  public static String updateMinSdkVersion(@NotNull String contents) {
    String regex = "minSdkVersion[ (]([0-9]+)";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(contents);
    String minSdkVersion = BuildEnvironment.getInstance().getMinSdkVersion();
    if (matcher.find()) {
      try {
        if (Integer.parseInt(matcher.group(1)) < Integer.parseInt(minSdkVersion)) {
          contents = contents.substring(0, matcher.start(1)) + minSdkVersion + contents.substring(matcher.end(1));
        }
      }
      catch (NumberFormatException ignore) {
      }
    }
    return contents;
  }

  public static void updateLocalProperties(@NotNull File projectRoot, @NotNull File sdkPath) throws IOException {
    LocalProperties localProperties = new LocalProperties(projectRoot);
    assertAbout(file()).that(sdkPath).named("Android SDK path").isDirectory();
    localProperties.setAndroidSdkPath(sdkPath.getPath());
    localProperties.save();
  }

  /**
   * Prevents leaking classloaders in gradle daemon
   */
  public static void applyUglyWorkaroundForMetaspaceOOMInGradleDaemon(File projectRoot) throws IOException {
    File projectBuildGradle = new File(projectRoot, "build.gradle");
    if (projectBuildGradle.isFile()) {
      asCharSink(projectBuildGradle, StandardCharsets.UTF_8, FileWriteMode.APPEND).write(
        "\n\n" +
        "// Ugly workaround for OOME:Metaspace in Gradle daemon\n" +
        "gradle.services.get(org.gradle.tooling.internal.provider.serialization.PayloadSerializer.class)\n" +
        "  .classLoaderRegistry.delegate.cache.classLoaderIds.localCache.values()\n" +
        "  .each {\n" +
        "    if (it.hasProperty('name') && it.name == 'client-owned-daemon-payload-loader')\n" +
        "      it.loadClass('org.gradle.tooling.internal.adapter.ProtocolToModelAdapter').newInstance().REFLECTION_METHOD_INVOKER.lookupCache.store.clear()\n" +
        "  }");
    }
  }

  @NotNull
  public static String updateLocalRepositories(@NotNull String contents, @NotNull String localRepositories) {
    String newContents = contents;
    newContents = GOOGLE_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = JCENTER_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = MAVEN_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");

    // Last, as it has maven repos
    newContents = REPOSITORIES_PATTERN.matcher(newContents).replaceAll("repositories {\n" + localRepositories);
    return newContents;
  }

  @NotNull
  public static String getLocalRepositoriesForGroovy() {
    // Add metadataSources to work around http://b/144088459. Wrap it in try-catch because
    // we are also using older Gradle versions that do not have this method.
    return StringUtil.join(getLocalRepositoryDirectories(),
                           file -> "maven {\n" +
                                   "  url \"" + file.toURI().toString() + "\"\n" +
                                   "  try {\n" +
                                   "    metadataSources() {\n" +
                                   "      mavenPom()\n" +
                                   "      artifact()\n" +
                                   "    }\n" +
                                   "  } catch (Throwable ignored) { /* In case this Gradle version does not support this. */}\n" +
                                   "}", "\n");
  }

  @NotNull
  public static String getLocalRepositoriesForKotlin() {
    // Add metadataSources to work around http://b/144088459.
    return StringUtil.join(getLocalRepositoryDirectories(),
                           file -> "maven {\n" +
                                   "  setUrl(\"" + file.toURI().toString() + "\")\n" +
                                   "  metadataSources() {\n" +
                                   "    mavenPom()\n" +
                                   "    artifact()\n" +
                                   "  }\n" +
                                   "}", "\n");
  }

  @NotNull
  public static Collection<File> getLocalRepositoryDirectories() {
    List<File> repositories = new ArrayList<>();
    String prebuiltsRepo = "prebuilts/tools/common/m2/repository";
    String publishLocalRepo = "out/repo";
    if (TestUtils.runningFromBazel() && false) { // FIXME-ank: env variables imply Bazel. This is not correct when running from IU.
      // Based on EmbeddedDistributionPaths#findAndroidStudioLocalMavenRepoPaths:
      File tmp = new File(PathManager.getHomePath()).getParentFile().getParentFile();
      File file = new File(tmp, prebuiltsRepo);
      if (file.exists()) {
        repositories.add(file);
      }
      else {
        repositories.add(getWorkspaceFile(prebuiltsRepo));
      }
      // publish local should already be available inside prebuilts
    }
    else if (System.getProperty("idea.gui.test.running.on.release") != null) {
      repositories.add(new File(PathManager.getHomePath(), "gradle"));
    }
    else {
      repositories.add(getWorkspaceFile(prebuiltsRepo));
      repositories.add(getWorkspaceFile(publishLocalRepo));
    }
    return repositories;
  }


  /**
   * Take a regex pattern with a single group in it and replace the contents of that group with a
   * new value.
   * <p>
   * For example, the pattern "Version: (.+)" with value "Test" would take the input string
   * "Version: Production" and change it to "Version: Test"
   * <p>
   * The reason such a special-case pattern substitution utility method exists is this class is
   * responsible for loading read-only gradle test files and copying them over into a mutable
   * version for tests to load. When doing so, it updates obsolete values (like old android
   * platforms) to more current versions. This lets tests continue to run whenever we update our
   * tools to the latest versions, without having to go back and change a bunch of broken tests
   * each time.
   * <p>
   * If a regex is passed in with more than one group, later groups will be ignored; and if no
   * groups are present, this will throw an exception. It is up to the caller to ensure that the
   * regex is well formed and only includes a single group.
   *
   * @return The {@code contents} string, modified by the replacement {@code value}, (unless no
   * {@code regex} match was found).
   */
  @NotNull
  public static String replaceRegexGroup(String contents, @RegEx String regex, String value) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(contents);
    if (matcher.find()) {
      contents = contents.substring(0, matcher.start(1)) + value + contents.substring(matcher.end(1));
    }
    return contents;
  }

  /**
   * Creates a gradle wrapper for use in tests under the {@code projectRoot}.
   *
   * @throws IOException
   */
  public static void createGradleWrapper(@NotNull File projectRoot, @NotNull String gradleVersion) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectRoot);
    File path = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(gradleVersion);
    TestCase.assertNotNull("Gradle version not found in EmbeddedDistributionPaths. Version = " + gradleVersion, path);
    assertAbout(file()).that(path).named("Gradle distribution path").isFile();
    wrapper.updateDistributionUrl(path);
  }

  /**
   * Finds the AndroidFacet to be used by the test.
   */
  @Nullable
  public static AndroidFacet findAndroidFacetForTests(Module[] modules, @Nullable String chosenModuleName) {
    AndroidFacet testAndroidFacet = null;
    // if module name is specified, find it
    if (chosenModuleName != null) {
      for (Module module : modules) {
        if (chosenModuleName.equals(module.getName())) {
          testAndroidFacet = AndroidFacet.getInstance(module);
          break;
        }
      }
    }

    if (testAndroidFacet == null) {
      // then try and find a non-lib facet
      for (Module module : modules) {
        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        if (androidFacet != null && androidFacet.getConfiguration().isAppProject()) {
          testAndroidFacet = androidFacet;
          break;
        }
      }
    }

    // then try and find ANY android facet
    if (testAndroidFacet == null) {
      for (Module module : modules) {
        testAndroidFacet = AndroidFacet.getInstance(module);
        if (testAndroidFacet != null) {
          break;
        }
      }
    }
    return testAndroidFacet;
  }

  public static void setUpSdks(@NotNull CodeInsightTestFixture fixture, @NotNull File androidSdkPath) {
    setUpSdks(fixture.getProject(), fixture.getProjectDisposable(), androidSdkPath);
  }

  public static void setUpSdks(
    @NotNull Project project,
    @NotNull Disposable projectDisposable,
    @NotNull File androidSdkPath
  ) {
    // We seem to have two different locations where the SDK needs to be specified.
    // One is whatever is already defined in the JDK Table, and the other is the global one as defined by IdeSdks.
    // Gradle import will fail if the global one isn't set.

    IdeSdks ideSdks = IdeSdks.getInstance();
    runWriteCommandAction(project, () -> {
      if (IdeInfo.getInstance().isAndroidStudio()) {
        ideSdks.setUseEmbeddedJdk();
        LOG.info("Set JDK to " + ideSdks.getJdkPath());
      }

      Sdks.allowAccessToSdk(projectDisposable);
      ideSdks.setAndroidSdkPath(androidSdkPath, project);
      IdeSdks.removeJdksOn(projectDisposable);

      LOG.info("Set IDE Sdk Path to " + androidSdkPath);
    });

    Sdk currentJdk = ideSdks.getJdk();
    TestCase.assertNotNull(currentJdk);
    TestCase.assertTrue("JDK 8 is required. Found: " + currentJdk.getHomePath(), IdeSdks.getInstance().isJdkCompatible(currentJdk, JDK_1_8));

    // IntelliJ uses project jdk for gradle import by default, see GradleProjectSettings.myGradleJvm
    // Android Studio overrides GradleInstallationManager.getGradleJdk() using AndroidStudioGradleInstallationManager
    // so it doesn't require the Gradle JDK setting to be defined
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) {
          ProjectRootManager.getInstance(project).setProjectSdk(currentJdk);
        }
      }.execute();
    }
  }

  /**
   * Imports {@code project}, syncs the project and checks the result.
   */
  public static void importProject(@NotNull Project project, GradleSyncInvoker.Request syncRequest) throws Exception {
    TestGradleSyncListener syncListener = EdtTestUtil.runInEdtAndGet(() -> {
      GradleProjectImporter.Request request = new GradleProjectImporter.Request(project);
      GradleProjectImporter.getInstance().importProjectNoSync(request);
      return syncProject(project, syncRequest);
    });

    AndroidGradleTests.checkSyncStatus(syncListener);
    AndroidTestBase.refreshProjectFiles();
  }

  public static void prepareProjectForImportCore(@NotNull File srcRoot,
                                                 @NotNull File projectRoot,
                                                 @NotNull ThrowableConsumer<File, IOException> patcher)
    throws IOException {
    TestCase.assertTrue(srcRoot.getPath(), srcRoot.exists());

    copyDir(srcRoot, projectRoot);

    patcher.consume(projectRoot);

    // Refresh project dir to have files under of the project.getBaseDir() visible to VFS.
    // Do it in a slower but reliable way.
    VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(projectRoot, true));
  }

  public static void validateGradleProjectSource(@NotNull File srcRoot) {
    File settings = new File(srcRoot, FN_SETTINGS_GRADLE);
    File build = new File(srcRoot, FN_BUILD_GRADLE);
    File ktsSettings = new File(srcRoot, FN_SETTINGS_GRADLE_KTS);
    File ktsBuild = new File(srcRoot, FN_BUILD_GRADLE_KTS);
    TestCase.assertTrue("Couldn't find build.gradle(.kts) or settings.gradle(.kts) in " + srcRoot.getPath(),
                        settings.exists() || build.exists() || ktsSettings.exists() || ktsBuild.exists());
  }

  public static TestGradleSyncListener syncProject(@NotNull Project project,
                                                   @NotNull GradleSyncInvoker.Request request) throws InterruptedException {
    TestGradleSyncListener syncListener = new TestGradleSyncListener();
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, syncListener);
    syncListener.await();
    syncListener.collectErrors(project);
    return syncListener;
  }

  public static void checkSyncStatus(@NotNull TestGradleSyncListener syncListener) {
    if (syncFailed(syncListener)) {
      String cause =
        !syncListener.isSyncFinished() ? "<Timed out>" : isEmpty(syncListener.failureMessage) ? "<Unknown>" : syncListener.failureMessage;
      TestCase.fail(cause);
    }
  }

  public static boolean syncFailed(@NotNull TestGradleSyncListener syncListener) {
    return !syncListener.success || !Strings.isNullOrEmpty(syncListener.failureMessage) || syncListener.hasErrors;
  }

  public static void defaultPatchPreparedProject(@NotNull File projectRoot, @Nullable String gradleVersion,
                                                 @Nullable String gradlePluginVersion) throws IOException {
    preCreateDotGradle(projectRoot);
    // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
    updateToolingVersionsAndPaths(projectRoot, gradleVersion, gradlePluginVersion);
    applyUglyWorkaroundForMetaspaceOOMInGradleDaemon(projectRoot);
  }

  /**
   * Pre-creates .gradle directory under the project root to avoid it being asynchronously created by Gradle.
   */
  public static void preCreateDotGradle(@NotNull File projectRoot) {
    File dotGradle = new File(projectRoot, ".gradle");
    if (!dotGradle.exists()) {
      //noinspection ResultOfMethodCallIgnored
      dotGradle.mkdir();
    }
  }
}
