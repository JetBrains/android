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

import com.android.builder.model.AndroidProject;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.jps.AndroidGradleJps;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModule;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleBuilds.*;
import static com.intellij.util.ArrayUtil.toStringArray;

/**
 * Builds Gradle-based Android project using Gradle.
 */
public class AndroidGradleTargetBuilder extends TargetBuilder<AndroidGradleBuildTarget.RootDescriptor, AndroidGradleBuildTarget> {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTargetBuilder.class);

  @NonNls private static final String BUILDER_NAME = "Android Gradle Target Builder";

  private static final int BUFFER_SIZE = 2048;

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
    checkUnsupportedModules(project, context);

    BuilderExecutionSettings executionSettings;
    try {
      executionSettings = new BuilderExecutionSettings();
    }
    catch (RuntimeException e) {
      throw new ProjectBuildException(e);
    }

    LOG.info("Using execution settings: " + executionSettings);

    List<String> buildTasks = executionSettings.getGradleTasksToInvoke();
    if (buildTasks.isEmpty()) {
      String format = "No build tasks found for project '%1$s'. Nothing done.";
      LOG.info(String.format(format, project.getName()));
      return;
    }

    context.processMessage(AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.INFO, "Executing tasks: " + buildTasks));

    String msg = "Gradle build using tasks: " + buildTasks;
    context.processMessage(new ProgressMessage(msg));
    LOG.info(msg);

    ensureTempDirExists();

    String androidHome = null;
    if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(executionSettings.getProjectDir())) {
      androidHome = getAndroidHomeFromModuleSdk(project);
    }

    String format = "About to build project '%1$s' located at %2$s";
    LOG.info(String.format(format, project.getName(), executionSettings.getProjectDir().getAbsolutePath()));

    doBuild(context, buildTasks, executionSettings, androidHome);
  }

  private static void checkUnsupportedModules(JpsProject project, CompileContext context) {
    for (JpsTypedModule<JpsDummyElement> module : project.getModules(JpsJavaModuleType.INSTANCE)) {
      if (AndroidGradleJps.getGradleSystemExtension(module) == null) {
        context.processMessage(
          AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.WARNING, "module '" + module.getName() + "' won't be compiled. " +
                                                                            "Unfortunately you can't have non-Gradle Java module and Android-Gradle module in one project."));
      }
    }
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
                              @NotNull List<String> buildTasks,
                              @NotNull BuilderExecutionSettings executionSettings,
                              @Nullable String androidHome) throws ProjectBuildException {
    GradleConnector connector = getGradleConnector(executionSettings);

    ProjectConnection connection = connector.connect();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream(BUFFER_SIZE);
    ByteArrayOutputStream stderr = new ByteArrayOutputStream(BUFFER_SIZE);

    try {
      BuildLauncher launcher = connection.newBuild();
      launcher.forTasks(toStringArray(buildTasks));

      List<String> jvmArgs = Lists.newArrayList();
      BuildMode buildMode = executionSettings.getBuildMode();
      if (BuildMode.ASSEMBLE_TRANSLATE == buildMode) {
        String arg = AndroidGradleSettings.createJvmArg(GradleBuilds.ENABLE_TRANSLATION_JVM_ARG, true);
        jvmArgs.add(arg);
      }

      if (androidHome != null && !androidHome.isEmpty()) {
        String androidSdkArg = AndroidGradleSettings.createAndroidHomeJvmArg(androidHome);
        jvmArgs.add(androidSdkArg);
      }

      jvmArgs.addAll(executionSettings.getJvmOptions());

      LOG.info("Build JVM args: " + jvmArgs);
      if (!jvmArgs.isEmpty()) {
        launcher.setJvmArguments(toStringArray(jvmArgs));
      }

      List<String> commandLineArgs = Lists.newArrayList();
      commandLineArgs.addAll(executionSettings.getCommandLineOptions());
      commandLineArgs.add(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, true));

      if (executionSettings.isParallelBuild() && !commandLineArgs.contains(PARALLEL_BUILD_OPTION)) {
        commandLineArgs.add(PARALLEL_BUILD_OPTION);
      }

      if (executionSettings.isOfflineBuild() && !commandLineArgs.contains(OFFLINE_MODE_OPTION)) {
        commandLineArgs.add(OFFLINE_MODE_OPTION);
      }

      if (executionSettings.isConfigureOnDemand() && !commandLineArgs.contains(CONFIGURE_ON_DEMAND_OPTION)) {
        commandLineArgs.add(CONFIGURE_ON_DEMAND_OPTION);
      }

      LOG.info("Build command line args: " + commandLineArgs);
      if (!commandLineArgs.isEmpty()) {
        launcher.withArguments(toStringArray(commandLineArgs));
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
      try {
        Closeables.close(stdout, true);
        Closeables.close(stderr, true);
      } catch (IOException e) {
        LOG.debug(e);
      }
      connection.close();
    }
  }

  @NotNull
  private static GradleConnector getGradleConnector(@NotNull BuilderExecutionSettings executionSettings) {
    GradleConnector connector = GradleConnector.newConnector();
    if (connector instanceof DefaultGradleConnector) {
      DefaultGradleConnector defaultConnector = (DefaultGradleConnector)connector;

      if (executionSettings.isEmbeddedModeEnabled()) {
        LOG.info("Using Gradle embedded mode.");
        defaultConnector.embedded(true);
      }

      defaultConnector.setVerboseLogging(executionSettings.isVerboseLoggingEnabled());
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
    Iterable<PatternAwareOutputParser> parsers = JpsServiceManager.getInstance().getExtensions(PatternAwareOutputParser.class);
    Collection<Message> compilerMessages = new BuildOutputParser(parsers).parseGradleOutput(stdErr);
    if (!compilerMessages.isEmpty()) {
      boolean hasError = false;
      for (Message message : compilerMessages) {
        if (message.getKind() == Message.Kind.ERROR) {
          hasError = true;
        }
        for (CompilerMessage compilerMessage: AndroidGradleJps.createCompilerMessages(message)) {
          context.processMessage(compilerMessage);
        }
      }
      if (hasError) {
        return;
      }
    }
    // There are no error messages to present. Show some feedback indicating that something went wrong.
    if (!stdErr.isEmpty()) {
      // Show the contents of stderr as a compiler error.
      context.processMessage(createCompilerErrorMessage(stdErr));
    }
    else {
      // Since we have nothing else to show, just print the stack trace of the caught exception.
      ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        e.printStackTrace(new PrintStream(out));
        String message = "Internal error:" + SystemProperties.getLineSeparator() + out.toString();
        context.processMessage(createCompilerErrorMessage(message));
      }
      finally {
        try {
          Closeables.close(out, true);
        }
        catch (IOException e1) {
          LOG.debug(e1);
        }
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
