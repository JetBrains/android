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
import static com.android.SdkConstants.DOT_VERSIONS_DOT_TOML;
import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT;
import static com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentUtil.resolveAgpVersionSoftwareEnvironment;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.ide.impl.NewProjectUtil.applyJdkToProject;
import static com.intellij.openapi.application.ActionsKt.invokeAndWaitIfNeeded;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.AgpVersion;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.concurrency.CoroutinesTestUtilsKt;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.plugin.AgpVersions;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectStructureUsageTrackerManager;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.AndroidRunConfigurationsManager;
import com.android.tools.idea.projectsystem.gradle.LinkedAndroidModuleGroupUtilsKt;
import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ThrowableConsumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import junit.framework.TestCase;
import kotlin.Unit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidGradleTests {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTests.class);
  private static final Pattern REPOSITORIES_PATTERN = Pattern.compile("repositories[ ]+\\{");
  private static final Pattern GOOGLE_REPOSITORY_PATTERN = Pattern.compile("google\\(\\)");
  private static final Pattern JCENTER_REPOSITORY_PATTERN = Pattern.compile("jcenter\\(\\)");
  private static final Pattern MAVEN_CENTRAL_REPOSITORY_PATTERN = Pattern.compile("mavenCentral\\(\\)");
  private static final Pattern GRADLE_PLUGIN_PORTAL_REPOSITORY_PATTERN = Pattern.compile("gradlePluginPortal\\(\\)");
  private static final Pattern MAVEN_REPOSITORY_PATTERN = Pattern.compile("maven \\{.*http.*\\}");
  /**
   * Property name that allows adding multiple local repositories via JVM properties
   */
  private static final String ADDITIONAL_REPOSITORY_PROPERTY = "idea.test.gradle.additional.repositories";
  private static final long DEFAULT_TIMEOUT_SOURCES_FOLDER_UPDATES_MILLIS = 1000;
  private static final String NDK_VERSION_PLACEHOLDER = "// ndkVersion \"{placeholder}\"";

  public static void waitForSourceFolderManagerToProcessUpdates(@NotNull Project project) throws Exception {
    waitForSourceFolderManagerToProcessUpdates(project, null);
  }

  public static void waitForSourceFolderManagerToProcessUpdates(@NotNull Project project, @Nullable Long timeoutMillis) throws Exception {
    long timeout = (timeoutMillis == null) ? DEFAULT_TIMEOUT_SOURCES_FOLDER_UPDATES_MILLIS : timeoutMillis;
    // TODO(2025-01-01) Revise and simplify code below after youtrack issue fixed
    // PlatformTestUtil.waitForFuture() pumps the EDT queue using UIUtil.dispatchAllInvocationEvents(),
    // which may flush VFS events on the EDT, thus the operationsStates list might get mutated. This all happens in a "callback" during
    // iteration over operationsStates, so it can trigger a CME (even though everything is happening on the same thread).
    // b/331382821
    // https://youtrack.jetbrains.com/issue/IDEA-350259
    List<Future<?>> pendingRunConfigurations = new ArrayList<>();
    ((SourceFolderManagerImpl)SourceFolderManager.getInstance(project)).consumeBulkOperationsState(future -> {
      pendingRunConfigurations.add(future);
      return null;
    });
    for (Future<?> future : pendingRunConfigurations) {
      PlatformTestUtil.waitForFuture(future, timeout);
    }
  }

  public static void waitForCreateRunConfigurations(@NotNull Project project) throws Exception {
    AndroidRunConfigurationsManager.getInstance(project).consumeBulkOperationsState((job) -> {
      CoroutinesTestUtilsKt.waitCoroutinesBlocking(job);
      return null;
    });
  }

  public static void waitForProjectStructureUsageTracker(@NotNull Project project) throws Exception {
    ProjectStructureUsageTrackerManager.getInstance(project).consumeBulkOperationsState((job) -> {
      CoroutinesTestUtilsKt.waitCoroutinesBlocking(job);
      return null;
    });
  }

  /**
   * Thrown when Android Studios Gradle imports obtains and SyncIssues from Gradle that have SyncIssues.SEVERITY_ERROR.
   */
  public static class SyncIssuesPresentError extends AssertionError {
    @NotNull
    private final List<IdeSyncIssue> issues;

    public SyncIssuesPresentError(@NotNull String message, @NotNull List<IdeSyncIssue> issues) {
      super(message);
      this.issues = issues;
    }

    @NotNull
    public List<IdeSyncIssue> getIssues() {
      return issues;
    }
  }

  /**
   * @deprecated use {@link AndroidGradleTests#updateToolingVersionsAndPaths(java.io.File) instead.}.
   */
  @Deprecated
  public static void updateGradleVersions(@NotNull File folderRootPath) throws IOException {
    updateToolingVersionsAndPaths(folderRootPath, resolveAgpVersionSoftwareEnvironment(AGP_CURRENT), null, emptyList());
  }

  public static void updateToolingVersionsAndPaths(@NotNull File folderRootPath) throws IOException {
    updateToolingVersionsAndPaths(folderRootPath, resolveAgpVersionSoftwareEnvironment(AGP_CURRENT), null, emptyList());
  }

  public static void updateToolingVersionsAndPaths(@NotNull File path,
                                                   @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                                   @Nullable String ndkVersion,
                                                   @NotNull List<File> localRepos)
    throws IOException {

    internalUpdateToolingVersionsAndPaths(path,
                                          true,
                                          agpVersion.getGradleVersion(),
                                          agpVersion.getAgpVersion(),
                                          agpVersion.getKotlinVersion(),
                                          agpVersion.getCompileSdk(),
                                          ndkVersion,
                                          localRepos);
  }

  private static void internalUpdateToolingVersionsAndPaths(@NotNull File path,
                                                            boolean isRoot,
                                                            @NotNull String gradleVersion,
                                                            @NotNull String pluginVersion,
                                                            @NotNull String kotlinVersion,
                                                            @NotNull String compileSdkVersion,
                                                            @Nullable String ndkVersion,
                                                            @NotNull List<File> localRepos) throws IOException {
    // Tools/base versions are the same but with then major incremented by 23
    int firstSeparator = pluginVersion.indexOf('.');
    int majorVersion = Integer.parseInt(pluginVersion.substring(0, firstSeparator)) + 23;
    String toolsBaseVersion = majorVersion + pluginVersion.substring(firstSeparator);

    BasicFileAttributes fileAttributes;
    try {
      fileAttributes = Files.readAttributes(path.toPath(), BasicFileAttributes.class);
    }
    catch (NoSuchFileException e) {
      return;
    }

    if (fileAttributes.isDirectory()) {
      if (isRoot || new File(path, FN_SETTINGS_GRADLE).exists() || new File(path, FN_SETTINGS_GRADLE_KTS).exists()) {
        // Don't update the project if it is a buildSrc project. There could also be a project named buildSrc however
        // since this is used in tests we assume that this will never happen.
        if (path.getName().equals("buildSrc")) {
          return;
        }

        // Override settings just for tests (e.g. sdk.dir)
        updateLocalProperties(path, TestUtils.getSdk().toFile());
        try {
          updateGradleProperties(path, AgpVersion.parse(pluginVersion), new AndroidVersion(compileSdkVersion));
        }
        catch (AndroidVersion.AndroidVersionException e) {
          throw new IOException(e);
        }
        // We need the wrapper for import to succeed
        createGradleWrapper(path, gradleVersion);
      }
      for (File child : notNullize(path.listFiles())) {
        internalUpdateToolingVersionsAndPaths(
          child, false, gradleVersion, pluginVersion, kotlinVersion, compileSdkVersion, ndkVersion, localRepos
        );
      }
    }
    else if (fileAttributes.isRegularFile()) {
      if (path.getPath().endsWith(DOT_GRADLE)) {
        String contentsOrig = Files.readString(path.toPath());
        String contents = contentsOrig;
        String localRepositories = getLocalRepositoriesForGroovy(localRepos);

        contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]", pluginVersion);
        contents = replaceRegexGroup(contents, "id ['\"]com\\.android\\..+['\"].*version ['\"](.+)['\"]", pluginVersion);

        contents = replaceRegexGroup(contents, "ext.kotlin_version ?= ?['\"](.+)['\"]", kotlinVersion);
        contents = replaceRegexGroup(contents, "id ['\"]org.jetbrains.kotlin..+['\"].*version ['\"](.+)['\"]", kotlinVersion);

        contents = replaceRegexGroup(contents, "om.android.tools.lint:lint-api:(.+)['\"]", toolsBaseVersion);
        contents = replaceRegexGroup(contents, "om.android.tools.lint:lint-checks:(.+)['\"]", toolsBaseVersion);

        contents = updateBuildToolsVersion(contents);
        contents = updateCompileSdkVersion(contents, compileSdkVersion);
        contents = updateTargetSdkVersion(contents);
        contents = updateMinSdkVersionOnlyIfGreaterThanExisting(contents, "minSdkVersion *[(=]? *(\\d+)");
        contents = updateMinSdkVersionOnlyIfGreaterThanExisting(contents, "minSdk *= *(\\d+)");
        contents = updateLocalRepositories(contents, localRepositories);

        if (ndkVersion != null) {
          contents = contents.replace(NDK_VERSION_PLACEHOLDER, String.format("ndkVersion=\"%s\"", ndkVersion));
        }

        if (!contents.equals(contentsOrig)) {
          Files.writeString(path.toPath(), contents);
        }
      }
      else if (path.getPath().endsWith(EXT_GRADLE_KTS)) {
        String contentsOrig = Files.readString(path.toPath());
        String contents = contentsOrig;
        String localRepositories = getLocalRepositoriesForKotlin(localRepos);

        contents = replaceRegexGroup(contents, "classpath\\(['\"]com.android.tools.build:gradle:(.+)['\"]", pluginVersion);
        contents = replaceRegexGroup(contents, "id ['\"]com\\.android\\..+['\"].*version ['\"](.+)['\"]", pluginVersion);

        contents = replaceRegexGroup(contents, "[a-zA-Z]+\\s*\\(?\\s*['\"]org.jetbrains.kotlin:kotlin[a-zA-Z\\-]*:(.+)['\"]",
                                     kotlinVersion);
        contents = replaceRegexGroup(contents, "om.android.tools.lint:lint-api:(.+)['\"]", toolsBaseVersion);
        contents = replaceRegexGroup(contents, "om.android.tools.lint:lint-checks:(.+)['\"]", toolsBaseVersion);
        // "implementation"(kotlin("stdlib", "1.3.61"))
        contents = replaceRegexGroup(contents, "\"[a-zA-Z]+\"\\s*\\(\\s*kotlin\\(\"[a-zA-Z\\-]+\",\\s*\"(.+)\"", kotlinVersion);
        contents = replaceRegexGroup(contents, "id ['\"]org.jetbrains.kotlin..+['\"].*version ['\"](.+)['\"]", kotlinVersion);

        final var buildEnvironment = BuildEnvironment.getInstance();
        contents = replaceRegexGroup(contents, "\\(\"com.android.application\"\\) version \"(.+)\"", pluginVersion);
        contents = replaceRegexGroup(contents, "\\(\"com.android.library\"\\) version \"(.+)\"", pluginVersion);
        contents = replaceRegexGroup(contents, "buildToolsVersion\\(\"(.+)\"\\)", buildEnvironment.getBuildToolsVersion());
        contents = replaceRegexGroup(contents, "compileSdkVersion\\((.+)\\)", compileSdkVersion);
        contents = replaceRegexGroup(contents, "compileSdk *= *(\\d+)", compileSdkVersion);
        contents = replaceRegexGroup(contents, "targetSdkVersion\\((.+)\\)", buildEnvironment.getTargetSdkVersion());
        contents = replaceRegexGroup(contents, "targetSdk *= *(\\d+)", buildEnvironment.getTargetSdkVersion());
        contents = updateMinSdkVersionOnlyIfGreaterThanExisting(contents, "minSdkVersion[ (](\\d+)");
        contents = updateMinSdkVersionOnlyIfGreaterThanExisting(contents, "minSdk *= *(\\d+)");
        contents = updateLocalRepositories(contents, localRepositories);

        if (ndkVersion != null) {
          contents = contents.replace(NDK_VERSION_PLACEHOLDER, String.format("ndkVersion=\"%s\"", ndkVersion));
        }

        if (!contents.equals(contentsOrig)) {
          Files.writeString(path.toPath(), contents);
        }
      }
      else if (path.getPath().endsWith(DOT_VERSIONS_DOT_TOML)) {
        String contentsOrig = Files.readString(path.toPath());
        String contents = contentsOrig;

        contents = updateVersionInCatalog(contents, "com.android.application", pluginVersion);
        contents = updateVersionInCatalog(contents, "org.jetbrains.kotlin.android", kotlinVersion);
        contents = updateVersionInCatalog(contents, "com.android.library", pluginVersion);

        if (!contents.equals(contentsOrig)) {
          Files.writeString(path.toPath(), contents);
        }
      }
    }
  }

  public static String updateVersionInCatalog(@NotNull String contents, @NotNull String pluginId, @NotNull String version) {
    // need to find out alias for version if it's there
    Pattern pattern = Pattern.compile("id\\s*=\\s*\"" + pluginId + "\",\\s*version.ref\\s*=\\s*\"(.*)\"");
    Matcher matcher = pattern.matcher(contents);
    if (matcher.find()) {
      String key = matcher.group(1);
      return replaceRegexGroup(contents, key + " *= *\"(.*)\"", version);
    } else {
      // handle map notation with version literal
      String result = replaceRegexGroup(contents, "id\\s*=\\s*\"" + pluginId + "\",\\s*version\\s*=\\s*\"(.*)\"", version);
      if(result.equals(contents)){
        // handle literal notation
        result = replaceRegexGroup(contents, "=\\s*\"" + pluginId + ":(.*)\"", version);
      }
      return result;
    }
  }

  @NotNull
  public static String updateBuildToolsVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "buildToolsVersion ['\"](.+)['\"]", BuildEnvironment.getInstance().getBuildToolsVersion());
  }

  @NotNull
  public static String updateCompileSdkVersion(@NotNull String contents, @NotNull String compileSdkVersion) {
    contents = replaceRegexGroup(contents, "compileSdkVersion *[(=]? *([0-9]+)", compileSdkVersion);
    contents = replaceRegexGroup(contents, "compileSdk *[(=]? *([0-9]+)", compileSdkVersion);
    return contents;
  }

  @NotNull
  public static String updateTargetSdkVersion(@NotNull String contents) {
    contents = replaceRegexGroup(contents, "targetSdkVersion *[(=]? *([0-9]+)", BuildEnvironment.getInstance().getTargetSdkVersion());
    contents = replaceRegexGroup(contents, "targetSdk *[(=]? *([0-9]+)", BuildEnvironment.getInstance().getTargetSdkVersion());
    return contents;
  }

  @NotNull
  public static String updateMinSdkVersionOnlyIfGreaterThanExisting(@NotNull String contents, String regex) {
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
    localProperties.setAndroidSdkPath(sdkPath);
    localProperties.save();
  }

  public static void updateGradleProperties(@NotNull File projectRoot, @NotNull AgpVersion agpVersion, @NotNull AndroidVersion androidVersion) throws IOException {
    GradleProperties gradleProperties = new GradleProperties(new File(projectRoot, FN_GRADLE_PROPERTIES));
    // Inspired by: https://github.com/gradle/gradle/commit/8da8e742c3562a8130d3ddb5c6391d90ec565c39
    String debugIntegrationTest = System.getenv("DEBUG_INNER_TEST");
    String debugJvmArgs = "";
    if (!Strings.isNullOrEmpty(debugIntegrationTest)
        && !debugIntegrationTest.equalsIgnoreCase("n")
        && !(debugIntegrationTest.equalsIgnoreCase("attach-when-debugging") && System.getProperty("intellij.debug.agent") == null)
    ) {
      String serverArg = (debugIntegrationTest.equalsIgnoreCase("socket-listen")
                          || debugIntegrationTest.equalsIgnoreCase("attach-when-debugging")
                         ) ? "n" : "y";
      debugJvmArgs =
        String.format(
          "-agentlib:jdwp=transport=dt_socket,server=%s,suspend=n,address=5006 ",
          serverArg);
      System.out.println("***DEBUGGING GRADLE** via:" + debugJvmArgs);
    }

    gradleProperties.setJvmArgs(Strings.nullToEmpty(gradleProperties.getJvmArgs()) + " -XX:MaxMetaspaceSize=768m " + debugJvmArgs);
    // Disable Gradle file watching as it may be causing DirectoryNotEmptyException, see b/184293946.
    gradleProperties.getProperties().setProperty("org.gradle.vfs.watch", "false");
    if (StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get()) {
      gradleProperties.getProperties().setProperty("org.gradle.parallel", "true");
    }
    if (agpVersion.compareTo(AgpVersions.getLatestKnown()) < 0) {
      Set<String> current = new LinkedHashSet<>(Splitter.on(",").omitEmptyStrings().splitToList(gradleProperties.getProperties().getProperty("android.suppressUnsupportedCompileSdk", "")));
      current.add(androidVersion.getApiStringWithoutExtension());
      gradleProperties.getProperties().setProperty("android.suppressUnsupportedCompileSdk", Joiner.on(",").join(current));
    }
    gradleProperties.save();
  }

  @NotNull
  public static String updateLocalRepositories(@NotNull String contents, @NotNull String localRepositories) {
    String newContents = contents;
    newContents = GOOGLE_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = JCENTER_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = MAVEN_CENTRAL_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = GRADLE_PLUGIN_PORTAL_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = MAVEN_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");

    // Last, as it has maven repos
    newContents = REPOSITORIES_PATTERN.matcher(newContents).replaceAll("repositories {\n" + localRepositories);
    return newContents;
  }

  @NotNull
  public static String getLocalRepositoriesForGroovy(@NotNull List<File> localRepos) {
    // Add metadataSources to work around http://b/144088459. Wrap it in try-catch because
    // we are also using older Gradle versions that do not have this method.
    return StringUtil.join(
      Iterables.concat(getLocalRepositoryDirectories(), localRepos),
      file -> "maven {\n" +
              "  url \"" + file.toURI() + "\"\n" +
              "  try {\n" +
              "    metadataSources() {\n" +
              "      mavenPom()\n" +
              "      artifact()\n" +
              "    }\n" +
              "  } catch (Throwable ignored) { /* In case this Gradle version does not support this. */}\n" +
              "}", "\n");
  }

  @NotNull
  public static String getLocalRepositoriesForKotlin(@NotNull List<File> localRepos) {
    // Add metadataSources to work around http://b/144088459.
    return StringUtil.join(
      Iterables.concat(getLocalRepositoryDirectories(), localRepos),
      file -> "maven {\n" +
              "  setUrl(\"" + file.toURI() + "\")\n" +
              "  metadataSources() {\n" +
              "    mavenPom()\n" +
              "    artifact()\n" +
              "  }\n" +
              "}", "\n");
  }

  @NotNull
  public static Collection<File> getLocalRepositoryDirectories() {
    List<File> repositories = new ArrayList<>();
    repositories.add(TestUtils.getPrebuiltOfflineMavenRepo().toFile());

    if (!TestUtils.runningFromBazel()) {
      Path repo = TestUtils.getWorkspaceRoot().resolve("out/repo");
      if (Files.exists(repo)) {
        repositories.add(repo.toFile());
      }
    }

    // Read optional repositories passed as JVM property (see ADDITIONAL_REPOSITORY_PROPERTY)
    // This property allows multiple local repositories separated by the path separator
    String additionalRepositories = System.getProperty(ADDITIONAL_REPOSITORY_PROPERTY);
    if (additionalRepositories != null) {
      for (String repositoryPath : additionalRepositories.split(File.pathSeparator)) {
        File additionalRepositoryPathFile = new File(repositoryPath.trim());
        if (additionalRepositoryPathFile.exists() && additionalRepositoryPathFile.isDirectory()) {
          LOG.info(String.format("Added additional gradle repository '$1%s' from $2%s property",
                                 additionalRepositoryPathFile, ADDITIONAL_REPOSITORY_PROPERTY));
          repositories.add(additionalRepositoryPathFile);
        }
        else {
          LOG.info(String.format("Unable to find additional gradle repository '$1%s'\n" +
                                 "Check you $2%s property and verify the path",
                                 additionalRepositoryPathFile, ADDITIONAL_REPOSITORY_PROPERTY));
        }
      }
    }

    return repositories;
  }

  /**
   * Takes a regex pattern with a single group in it and replace the contents of that group with a
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
   * regex is well-formed and only includes a single group.
   *
   * @return The {@code contents} string, modified by the replacement {@code value}, (unless no
   * {@code regex} match was found).
   */
  @NotNull
  public static String replaceRegexGroup(String contents, String regex, String value) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(contents);
    if (matcher.find() && !matcher.group(1).equals(value)) {
      return contents.substring(0, matcher.start(1))
             + value
             // Keep replacing the found matches.
             + replaceRegexGroup(contents.substring(matcher.end(1)), regex, value);
    }
    return contents;
  }

  /**
   * Creates a gradle wrapper for use in tests under the {@code projectRoot}.
   */
  public static void createGradleWrapper(@NotNull File projectRoot, @NotNull String gradleVersion) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectRoot, null);
    File path = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(gradleVersion);
    if (path != null) {
      assertAbout(file()).that(path).named("Gradle distribution path").isFile();
      wrapper.updateDistributionUrl(path);
    }
  }

  /**
   * Finds the AndroidFacet to be used by the test.
   */
  @Nullable
  public static AndroidFacet findAndroidFacetForTests(@NotNull Project project, Module[] modules, @Nullable String chosenModuleName) {
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

    // Attempt to find a module with a suffix containing the chosenModuleName
    if (chosenModuleName != null && testAndroidFacet == null && modules.length > 0) {
      Module foundModule = TestModuleUtil.findModule(project, chosenModuleName);
      testAndroidFacet = AndroidFacet.getInstance(foundModule);
    }

    if (testAndroidFacet == null) {
      // then try and find a non-lib facet
      for (Module module : modules) {
        // Look for holder modules only in MPSS case. Otherwise any of the module group can match.
        if (!LinkedAndroidModuleGroupUtilsKt.isHolderModule(module)) {
          continue;
        }
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
        if (!ideSdks.isUsingEnvVariableJdk()) {
          ideSdks.setUseEmbeddedJdk();
          applyJdkToProject(project, ideSdks.getJdk());
        }
        LOG.info("Set JDK to " + ideSdks.getJdkPath());
      }

      Sdks.allowAccessToSdk(projectDisposable);
      final var oldAndroidSdkPath = ideSdks.getAndroidSdkPath();
      ideSdks.setAndroidSdkPath(androidSdkPath);
      Disposer.register(projectDisposable, () -> {
        WriteAction.runAndWait(() -> {
            AndroidSdkPathStore.getInstance().setAndroidSdkPath(oldAndroidSdkPath != null ? oldAndroidSdkPath.toPath() : null);
        });
      });
      IdeSdks.removeJdksOn(projectDisposable);

      LOG.info("Set IDE Sdk Path to " + androidSdkPath);
    });

    Sdk currentJdk = ideSdks.getJdk();
    TestCase.assertNotNull(currentJdk);
    TestCase.assertTrue("JDK 8 is required. Found: " + currentJdk.getHomePath(),
                        IdeSdks.getInstance().isJdkCompatible(currentJdk, JDK_1_8));

    // IntelliJ uses project jdk for gradle import by default, see GradleProjectSettings.myGradleJvm
    // Android Studio overrides GradleInstallationManager.getGradleJdk() using AndroidStudioGradleInstallationManager
    // so it doesn't require the Gradle JDK setting to be defined
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      WriteAction.runAndWait(() -> ProjectRootManager.getInstance(project).setProjectSdk(currentJdk));
    }
  }

  public static void prepareProjectForImportCore(@NotNull File srcRoot,
                                                 @NotNull File projectRoot,
                                                 @NotNull ThrowableConsumer<File, IOException> patcher)
    throws IOException {
    TestCase.assertTrue(srcRoot.getPath(), srcRoot.exists());

    copyDir(srcRoot, projectRoot);

    // patcher may use VFS (in fact, PropertiesFiles is using VFS now), need to refresh
    // otherwise pre-populated properties files are cleared (e.g. `android.useAndroidX` property)
    VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(projectRoot, true));
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

  public static void syncProject(@NotNull Project project,
                                 @NotNull GradleSyncInvoker.Request request,
                                 @NotNull Consumer<TestGradleSyncListener> check) throws InterruptedException {
    if (getProjectSystem(project).getSyncManager().isSyncInProgress()) {
      throw new IllegalStateException("Requesting sync while sync in progress");
    }
    TestGradleSyncListener syncListener = new TestGradleSyncListener();
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, syncListener);
    syncListener.await();
    invokeAndWaitIfNeeded(null, () -> {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      return Unit.INSTANCE;
    });
    check.accept(syncListener);
  }

  public static void checkSyncStatus(@NotNull Project project,
                                     @NotNull TestGradleSyncListener syncListener) throws SyncIssuesPresentError {
    checkSyncStatus(project, syncListener, Collections.emptySet());
  }

  public static void checkSyncStatus(@NotNull Project project,
                                     @NotNull TestGradleSyncListener syncListener,
                                     @NotNull Set<Integer> ignoreSyncIssues) throws SyncIssuesPresentError {
    if (!syncListener.isSyncFinished() || syncFailed(syncListener)) {
      String cause =
        !syncListener.isSyncFinished() ? "<Timed out>" : isEmpty(syncListener.failureMessage) ? "<Unknown>" : syncListener.failureMessage;
      TestCase.fail(cause);
    }
    // Also fail the test if SyncIssues with type errors are present.
    List<IdeSyncIssue> errors =
      Arrays.stream(ModuleManager.getInstance(project).getModules())
        .flatMap(module -> SyncIssues.forModule(module).stream())
        .filter(it -> !ignoreSyncIssues.contains(it.getType()))
        .filter(syncIssueData -> syncIssueData.getSeverity() == SyncIssue.SEVERITY_ERROR).collect(Collectors.toList());
    String errorMessage = errors.stream().map(IdeSyncIssue::toString).collect(Collectors.joining("\n"));
    if (!errorMessage.isEmpty()) {
      throw new SyncIssuesPresentError(errorMessage, errors);
    }
  }

  public static boolean syncFailed(@NotNull TestGradleSyncListener syncListener) {
    return !syncListener.success || !Strings.isNullOrEmpty(syncListener.failureMessage);
  }

  public static void defaultPatchPreparedProject(@NotNull File projectRoot,
                                                 @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                                 @Nullable String ndkVersion,
                                                 File... localRepos) throws IOException {
    preCreateDotGradle(projectRoot);
    // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
    updateToolingVersionsAndPaths(projectRoot, agpVersion, ndkVersion,
                                  Lists.newArrayList(localRepos));
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

  public static void overrideJdkTo8() throws IOException {
    String jdk8Path = getEmbeddedJdk8Path();
    @NotNull IdeSdks ideSdks = IdeSdks.getInstance();
    LOG.info("Using JDK from " + jdk8Path);
    ideSdks.overrideJdkEnvVariable(jdk8Path);
    assertTrue("Could not use JDK from " + jdk8Path, ideSdks.isJdkEnvVariableValid());
  }

  public static void overrideJdkToCurrentJdk() {
    @NotNull IdeSdks ideSdks = IdeSdks.getInstance();
    Path jdkPath = ideSdks.getJdkPath();
    assertNotNull("Could not find path of current JDK", jdkPath);
    LOG.info("Using JDK from " + jdkPath);
    ideSdks.overrideJdkEnvVariable(jdkPath.toAbsolutePath().toString());
    assertTrue("Could not use JDK from " + jdkPath, ideSdks.isJdkEnvVariableValid());
  }

  public static void addJdk8ToTableButUseCurrent() throws IOException {
    String jdk8Path = getEmbeddedJdk8Path();
    Jdks.getInstance().createAndAddJdk(jdk8Path);
    overrideJdkToCurrentJdk();
  }

  public static void restoreJdk() {
    IdeSdks.getInstance().cleanJdkEnvVariableInitialization();
  }

  public static String getEmbeddedJdk8Path() throws IOException {
    Path jdkRootPath = StudioPathManager.resolvePathFromSourcesRoot("prebuilts/studio/jdk/jdk8");
    if (SystemInfo.isWindows) {
      // For JDK8 we have 32 and 64 bits versions on Windows
      jdkRootPath = jdkRootPath.resolve("win64");
    }
    else if (SystemInfo.isLinux) {
      jdkRootPath = jdkRootPath.resolve("linux");
    }
    else if (SystemInfo.isMac) {
      jdkRootPath = jdkRootPath.resolve("mac").resolve(MAC_JDK_CONTENT_PATH);
    }

    // Resolve real path
    //
    // Gradle prior to 6.9 don't work well with symlinks
    // see https://discuss.gradle.org/t/gradle-daemon-different-context/2146/3
    // see https://github.com/gradle/gradle/issues/12840
    //
    // [WARNING] This effective escapes Bazel's sandbox. Remove as soon as possible.
    try {
      Path wellKnownJdkFile = jdkRootPath.resolve("release");
      jdkRootPath = wellKnownJdkFile.toRealPath().getParent();
    }
    catch (IOException ignore) {
    }
    return jdkRootPath.toString();
  }


  /**
   * Returns the main module for the Java module under the given moduleName.
   *
   * @param moduleName the name of the Gradle project to find the main module for
   * @return the main module
   */
  @NotNull
  public static Module getMainJavaModule(@NotNull Project project, @NotNull String moduleName) {
    Module holderModule = TestModuleUtil.findModule(project, moduleName);

    if (AndroidFacet.getInstance(holderModule) != null) {
      throw new IllegalArgumentException("The module named " + moduleName + " must be a Java only module!");
    }

    return TestModuleUtil.findModule(project, moduleName + ".main");
  }
}
