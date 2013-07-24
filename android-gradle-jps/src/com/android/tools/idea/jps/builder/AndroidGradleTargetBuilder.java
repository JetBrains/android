/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.jps.builder;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.model.JpsAndroidGradleModuleExtension;
import com.android.tools.idea.jps.output.parser.GradleErrorOutputParser;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Builds Gradle-based Android project using Gradle.
 */
public class AndroidGradleTargetBuilder extends TargetBuilder<AndroidGradleBuildTarget.RootDescriptor, AndroidGradleBuildTarget> {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTargetBuilder.class);
  private static final GradleErrorOutputParser ERROR_OUTPUT_PARSER = new GradleErrorOutputParser();

  @NonNls private static final String CLEAN_TASK_NAME = "clean";
  @NonNls private static final String DEFAULT_ASSEMBLE_TASK_NAME = "assemble";

  @NonNls private static final String JVM_ARG_FORMAT = "-D%1$s=%2$s";
  @NonNls private static final String ANDROID_HOME_JVM_ARG = "android.home";

  @NonNls private static final String BUILDER_NAME = "Android Gradle Target Builder";

  public AndroidGradleTargetBuilder() {
    super(Collections.singletonList(AndroidGradleBuildTarget.TargetType.INSTANCE));
  }

  /**
   * Builds a Gradle-based Android project using Gradle.
   */
  @Override
  public void build(@NotNull AndroidGradleBuildTarget target,
                    @NotNull DirtyFilesHolder<AndroidGradleBuildTarget.RootDescriptor, AndroidGradleBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    JpsProject project = target.getProject();

    BuilderExecutionSettings executionSettings;
    try {
      executionSettings = new BuilderExecutionSettings();
    } catch (RuntimeException e) {
      throw new ProjectBuildException(e);
    }

    LOG.info("Using execution settings: " + executionSettings);


    String[] buildTasks = getBuildTasks(project, context, executionSettings);
    if (buildTasks.length == 0) {
      String format = "No build tasks found for project '%1$s'. Nothing done.";
      LOG.info(String.format(format, project.getName()));
      return;
    }

    String msg = "Gradle build using tasks: " + Arrays.toString(buildTasks);
    context.processMessage(new ProgressMessage(msg));
    LOG.info(msg);

    ensureTempDirExists();

    String androidHome = null;
    if (!isAndroidHomeKnown(executionSettings)) {
      androidHome = getAndroidHomeFromModuleSdk(project);
    }

    String format = "About to build project '%1$s' located at %2$s";
    LOG.info(String.format(format, project.getName(), executionSettings.getProjectDir().getAbsolutePath()));

    doBuild(context, buildTasks, executionSettings, androidHome);
  }

  @NotNull
  private static String[] getBuildTasks(@NotNull JpsProject project,
                                        @NotNull CompileContext context,
                                        @NotNull BuilderExecutionSettings executionSettings) {
    boolean buildTests = AndroidJpsUtil.isInstrumentationTestContext(context);
    List<String> tasks = Lists.newArrayList();
    for (JpsModule module : project.getModules()) {
      populateBuildTasks(module, executionSettings, tasks, buildTests);
    }
    if (!tasks.isEmpty()) {
      boolean rebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);
      if (rebuild) {
        tasks.add(0, CLEAN_TASK_NAME);
      }
    }
    return tasks.toArray(new String[tasks.size()]);
  }

  private static void populateBuildTasks(@NotNull JpsModule module,
                                         @NotNull BuilderExecutionSettings executionSettings,
                                         @NotNull List<String> tasks,
                                         boolean buildTests) {
    JpsAndroidGradleModuleExtension androidGradleFacet = AndroidGradleJps.getExtension(module);
    if (androidGradleFacet == null) {
      return;
    }
    String gradleProjectPath = androidGradleFacet.getProperties().GRADLE_PROJECT_PATH;
    if (gradleProjectPath == null) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String format = "Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.";
      String msg = String.format(format, module.getName());
      LOG.warn(msg);
      return;
    }
    String assembleTaskName = null;
    JpsAndroidModuleExtensionImpl androidFacet = (JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();
      assembleTaskName = executionSettings.isGenerateSourceOnly() ? properties.SOURCE_GEN_TASK_NAME : properties.ASSEMBLE_TASK_NAME;
    }
    if (Strings.isNullOrEmpty(assembleTaskName)) {
      assembleTaskName = DEFAULT_ASSEMBLE_TASK_NAME;
    }
    assert assembleTaskName != null;
    tasks.add(createBuildTask(gradleProjectPath, assembleTaskName));

    if (buildTests && androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();
      String assembleTestTaskName = properties.ASSEMBLE_TEST_TASK_NAME;
      if (!Strings.isNullOrEmpty(assembleTestTaskName)) {
        tasks.add(createBuildTask(gradleProjectPath, assembleTestTaskName));
      }
    }
  }

  @NotNull
  private static String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    return gradleProjectPath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }

  private static void ensureTempDirExists() {
    // Gradle checks that the dir at "java.io.tmpdir" exists, and if it doesn't it fails (on Windows.)
    String tmpDirProperty = System.getProperty("java.io.tmpdir");
    if (!Strings.isNullOrEmpty(tmpDirProperty)) {
      File tmpDir = new File(tmpDirProperty);
      try {
        FileUtil.ensureExists(tmpDir);
      }
      catch (IOException e) {
        LOG.warn("Unable to create temp directory", e);
      }
    }
  }
  /**
   * Indicates whether the path of the Android SDK home directory is specified in a local.properties file or in the ANDROID_HOME environment
   * variable.
   *
   * @param settings build execution settings.
   * @return {@code true} if the Android SDK home directory is specified in the project's local.properties file or in the ANDROID_HOME
   *         environment variable; {@code false} otherwise.
   */
  private static boolean isAndroidHomeKnown(@NotNull BuilderExecutionSettings settings) {
    String androidHome = getAndroidHomeFromLocalPropertiesFile(settings.getProjectDir());
    if (!Strings.isNullOrEmpty(androidHome)) {
      String msg = String.format("Found Android SDK home at '%1$s' (from local.properties file)", androidHome);
      LOG.info(msg);
      return true;
    }
    androidHome = System.getenv(AndroidSdkUtils.ANDROID_HOME_ENV);
    if (!Strings.isNullOrEmpty(androidHome)) {
      String msg = String.format("Found Android SDK home at '%1$s' (from ANDROID_HOME environment variable)", androidHome);
      LOG.info(msg);
      return true;
    }
    return false;
  }

  @Nullable
  private static String getAndroidHomeFromLocalPropertiesFile(@NotNull File projectDir) {
    File filePath = new File(projectDir, SdkConstants.FN_LOCAL_PROPERTIES);
    if (!filePath.isFile()) {
      return null;
    }
    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(filePath);
      properties.load(fileInputStream);
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      String msg = String.format("Failed to read file '%1$s'", filePath.getPath());
      LOG.error(msg, e);
      return null;
    } finally {
      Closeables.closeQuietly(fileInputStream);
    }
    return properties.getProperty(LocalProperties.SDK_DIR_PROPERTY);
  }


  @Nullable
  private static String getAndroidHomeFromModuleSdk(@NotNull JpsProject project) {
    JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = getFirstAndroidSdk(project);
    if (androidSdk == null) {
      // TODO: Figure out what changes in IDEA made androidSdk null. It used to work.
      String msg = String.format("There is no Android SDK specified for project '%1$s'", project.getName());
      LOG.error(msg);
      return null;
    }
    String androidHome = androidSdk.getHomePath();
    if (Strings.isNullOrEmpty(androidHome)) {
      String msg = "Selected Android SDK does not have a home directory path";
      LOG.error(msg);
      return null;
    }
    return androidHome;
  }

  @Nullable
  private static JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> getFirstAndroidSdk(@NotNull JpsProject project) {
    for (JpsModule module : project.getModules()) {
      JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = module.getSdk(JpsAndroidSdkType.INSTANCE);
      if (sdk != null) {
        return sdk;
      }
    }
    return null;
  }

  private static void doBuild(@NotNull CompileContext context,
                              @NotNull String[] buildTasks,
                              @NotNull BuilderExecutionSettings executionSettings,
                              @Nullable String androidHome) throws ProjectBuildException {
    GradleConnector connector = getGradleConnector(executionSettings);

    ProjectConnection connection = connector.connect();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    try {
      BuildLauncher launcher = connection.newBuild();
      launcher.forTasks(buildTasks);

      List<String> jvmArgs = Lists.newArrayList();

      if (!Strings.isNullOrEmpty(androidHome)) {
        String androidSdkArg = String.format(JVM_ARG_FORMAT, ANDROID_HOME_JVM_ARG, androidHome);
        jvmArgs.add(androidSdkArg);
      }

      jvmArgs.addAll(executionSettings.getGradleDaemonVmOptions());

      if (!jvmArgs.isEmpty()) {
        LOG.info("Passing JVM args to Gradle Tooling API: " + jvmArgs);
        launcher.setJvmArguments(jvmArgs.toArray(new String[jvmArgs.size()]));
      }

      if (executionSettings.isParallelBuild()) {
        LOG.info("Using 'parallel' build option");
        launcher.withArguments("--parallel");
      }

      File javaHomeDir = executionSettings.getJavaHomeDir();
      if (javaHomeDir != null) {
        launcher.setJavaHome(javaHomeDir);
      }

      launcher.setStandardOutput(stdout);
      launcher.setStandardError(stderr);
      launcher.run();
    }
    catch (BuildException e) {
      handleBuildException(e, context, stderr.toString());
    }
    finally {
      String outText = stdout.toString();
      context.processMessage(new ProgressMessage(outText, 1.0f));
      Closeables.closeQuietly(stdout);
      Closeables.closeQuietly(stderr);
      connection.close();
    }
  }

  @NotNull
  private static GradleConnector getGradleConnector(@NotNull BuilderExecutionSettings executionSettings) {
    GradleConnector connector = GradleConnector.newConnector();
    if (connector instanceof DefaultGradleConnector) {
      DefaultGradleConnector defaultConnector = (DefaultGradleConnector)connector;

      if (executionSettings.isEmbeddedGradleDaemonEnabled()) {
        LOG.info("Using Gradle embedded mode.");
        defaultConnector.embedded(true);
      }

      defaultConnector.setVerboseLogging(executionSettings.isVerboseLoggingEnabled());

      int daemonMaxIdleTimeInMs = executionSettings.getGradleDaemonMaxIdleTimeInMs();
      if (daemonMaxIdleTimeInMs > 0) {
        defaultConnector.daemonMaxIdleTime(daemonMaxIdleTimeInMs, TimeUnit.MILLISECONDS);
      }
    }

    connector.forProjectDirectory(executionSettings.getProjectDir());

    File gradleHomeDir = executionSettings.getGradleHomeDir();
    if (gradleHomeDir != null) {
      connector.useInstallation(gradleHomeDir);
    }

    File gradleServiceDir = executionSettings.getGradleServiceDir();
    if (gradleServiceDir != null) {
      connector.useGradleUserHomeDir(gradleServiceDir);
    }

    return connector;
  }

  /**
   * Something went wrong while invoking Gradle. Since we cannot distinguish an execution error from compilation errors easily, we first try
   * to show, in the "Problems" view, compilation errors by parsing the error output. If no errors are found, we show the stack trace in the
   * "Problems" view. The idea is that we need to somehow inform the user that something went wrong.
   */
  private static void handleBuildException(BuildException e, CompileContext context, String stdErr) throws ProjectBuildException {
    Collection<CompilerMessage> compilerMessages = ERROR_OUTPUT_PARSER.parseErrorOutput(stdErr);
    if (!compilerMessages.isEmpty()) {
      for (CompilerMessage message : compilerMessages) {
        context.processMessage(message);
      }
      return;
    }
    // There are no error messages to present. Show some feedback indicating that something went wrong.
    if (!stdErr.isEmpty()) {
      // Show the contents of stderr as a compiler error.
      context.processMessage(createCompilerErrorMessage(stdErr));
    }
    else {
      // Since we have nothing else to show, just print the stack trace of the caught exception.
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        e.printStackTrace(new PrintStream(out));
        String message = "Internal error:" + SystemProperties.getLineSeparator() + out.toString();
        context.processMessage(createCompilerErrorMessage(message));
      }
      finally {
        Closeables.closeQuietly(out);
      }
    }
    throw new ProjectBuildException(e.getMessage());
  }

  @NotNull
  private static CompilerMessage createCompilerErrorMessage(@NotNull String msg) {
    return AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.ERROR, msg);
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
