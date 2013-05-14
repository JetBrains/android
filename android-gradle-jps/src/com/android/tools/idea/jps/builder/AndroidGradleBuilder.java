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
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.model.JpsAndroidGradleModuleExtension;
import com.android.tools.idea.jps.model.impl.JpsAndroidGradleModuleProperties;
import com.android.tools.idea.jps.output.parser.GradleErrorOutputParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.AndroidSourceGeneratingBuilder;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

/**
 * Builds an IDEA project using Gradle.
 */
public class AndroidGradleBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(AndroidGradleBuilder.class);
  private static final GradleErrorOutputParser ERROR_OUTPUT_PARSER = new GradleErrorOutputParser();

  @NonNls private static final String ANDROID_HOME_JVM_ARG_FORMAT_WIN = "\"-Dandroid.home=%1$s\"";
  @NonNls private static final String ANDROID_HOME_JVM_ARG_FORMAT_UNIX = "-Dandroid.home=%1$s";

  @NonNls private static final String BUILDER_NAME = "Android Gradle Builder";
  @NonNls private static final String DEFAULT_ASSEMBLE_TASKNAME = "assemble";

  protected AndroidGradleBuilder() {
    super(BuilderCategory.TRANSLATOR);
    ResourcesBuilder.registerEnabler(new StandardResourceBuilderEnabler() {
      @Override
      public boolean isResourceProcessingEnabled(JpsModule module) {
        JpsProject project = module.getProject();
        return !AndroidGradleJps.hasAndroidGradleFacet(project);
      }
    });
  }

  /**
   * Disables IDEA's Java and Android builders for Gradle-imported projects. They are no longer needed since we build with Gradle.
   */
  @Override
  public void buildStarted(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    if (AndroidGradleJps.hasAndroidGradleFacet(project)) {
      JavaBuilder.IS_ENABLED.set(context, false);
      AndroidSourceGeneratingBuilder.IS_ENABLED.set(context, false);
    }
  }

  /**
   * Builds a project using Gradle.
   *
   * @return {@link ExitCode#OK} if compilation with Gradle succeeds without errors.
   * @throws ProjectBuildException if something goes wrong while invoking Gradle or if there are
   *                               compilation errors. Compilation errors are displayed in IDEA's
   *                               "Problems" view.
   */
  @NotNull
  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException {
    JpsAndroidGradleModuleExtension extension = AndroidGradleJps.getFirstExtension(chunk);
    if (extension == null) {
      String format = "Project '%1$s' does not have the '%2$s' facet. Nothing done.";
      LOG.info(String.format(format, getProjectName(context), AndroidGradleFacet.NAME));
      return ExitCode.NOTHING_DONE;
    }
    ensureTempDirExists();
    File projectDir = getProjectDir(context, extension);
    File gradleHomeDir = getGradleHomeDir(context, extension);
    String androidHome = getAndroidHome(context, chunk);
    String format = "About to build project '%1$s' located at %2$s";
    LOG.info(String.format(format, getProjectName(context), projectDir.getAbsolutePath()));
    return doBuild(context, chunk, projectDir, gradleHomeDir, androidHome);
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

  @NotNull
  private static File getProjectDir(@NotNull CompileContext context, @NotNull JpsAndroidGradleModuleExtension extension)
    throws ProjectBuildException {
    JpsAndroidGradleModuleProperties properties = extension.getProperties();
    String projectPath = properties.PROJECT_ABSOLUTE_PATH;
    if (Strings.isNullOrEmpty(projectPath)) {
      String format = "Unable to obtain path of project '%1$s'. Please re-import the project.";
      String msg = String.format(format, getProjectName(context));
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    File projectDir = new File(projectPath);
    if (!projectDir.isDirectory()) {
      String format = "The project path, '%1$s', does not belong to an existing directory";
      String msg = String.format(format, projectPath);
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    return projectDir;
  }


  @NotNull
  private static CompilerMessage createCompilerErrorMessage(@NotNull String msg) {
    return AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.ERROR, msg);
  }

  @Nullable
  private static File getGradleHomeDir(@NotNull CompileContext context, @NotNull JpsAndroidGradleModuleExtension extension)
    throws ProjectBuildException {
    JpsAndroidGradleModuleProperties properties = extension.getProperties();
    String gradleHomeDirPath = properties.GRADLE_HOME_DIR_PATH;
    if (Strings.isNullOrEmpty(gradleHomeDirPath)) {
      return null;
    }
    File gradleHomeDir = new File(gradleHomeDirPath);
    if (!gradleHomeDir.isDirectory()) {
      String format = "The gradle home path, '%1$s', does not belong to an existing directory";
      String msg = String.format(format, gradleHomeDir);
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    return gradleHomeDir;
  }

  @Nullable
  private static String getAndroidHome(@NotNull CompileContext context, @NotNull ModuleChunk chunk) throws ProjectBuildException {
    JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = AndroidGradleJps.getFirstAndroidSdk(chunk);
    if (androidSdk == null) {
      // TODO: Figure out what changes in IDEA made androidSdk null. It used to work.
      String format = "There is no Android SDK specified for project '%1$s'";
      String msg = String.format(format, getProjectName(context));
      LOG.warn(msg);
      return null;
    }
    String androidHome = androidSdk.getHomePath();
    if (Strings.isNullOrEmpty(androidHome)) {
      String msg = "Selected Android SDK does not have a home directory path";
      LOG.warn(msg);
    }
    return androidHome;
  }

  @NotNull
  private static String getProjectName(@NotNull CompileContext context) {
    return context.getProjectDescriptor().getProject().getName();
  }

  @NotNull
  private static ExitCode doBuild(@NotNull CompileContext context,
                                  @NotNull ModuleChunk chunk,
                                  @NotNull File projectDir,
                                  @Nullable File gradleHomeDir,
                                  @Nullable String androidHome) throws ProjectBuildException {
    GradleConnector connector = GradleConnector.newConnector();
    if (connector instanceof DefaultGradleConnector && SystemInfo.isWindows) {
      LOG.info("Using Gradle embedded mode.");
      ((DefaultGradleConnector)connector).embedded(true);
    }
    connector.forProjectDirectory(projectDir);
    if (gradleHomeDir != null) {
      connector.useInstallation(gradleHomeDir);
    }
    ProjectConnection connection = connector.connect();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      BuildLauncher launcher = connection.newBuild();
      String buildTasks = getBuildTasks(chunk);
      LOG.info("Gradle build using tasks: " + buildTasks);
      launcher.forTasks(buildTasks);
      if (!Strings.isNullOrEmpty(androidHome)) {
        String androidSdkArg = getAndroidHomeJvmArg(androidHome);
        launcher.setJvmArguments(androidSdkArg);
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
    return ExitCode.OK;
  }

  @NotNull
  private static String getBuildTasks(@NotNull ModuleChunk chunk) {
    StringBuilder tasks = new StringBuilder();
    for (JpsModule module : chunk.getModules()) {
      String buildTask = getBuildTask(module);
      if (buildTask == null) {
        continue;
      }
      if (tasks.length() > 0) {
        tasks.append(" ");
      }
      tasks.append(buildTask);
    }
    String buildTasks = tasks.toString();
    if (Strings.isNullOrEmpty(buildTasks)) {
      buildTasks = "build";
    }
    return buildTasks;
  }

  @Nullable
  private static String getBuildTask(@NotNull JpsModule module) {
    JpsAndroidGradleModuleExtension androidGradleFacet = AndroidGradleJps.getExtension(module);
    if (androidGradleFacet == null) {
      return null;
    }
    String moduleName = module.getName();
    String assembleTaskName = null;
    JpsAndroidModuleExtensionImpl androidFacet = (JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();
      assembleTaskName = properties.ASSEMBLE_TASK_NAME;
    }
    if (Strings.isNullOrEmpty(assembleTaskName)) {
      assembleTaskName = DEFAULT_ASSEMBLE_TASKNAME;
    }
    return moduleName + ":" + assembleTaskName;
  }

  @NotNull
  private static String getAndroidHomeJvmArg(@NotNull String androidHome) {
    boolean isWin = File.separator.equals("\\");
    String argName = isWin ? ANDROID_HOME_JVM_ARG_FORMAT_WIN : ANDROID_HOME_JVM_ARG_FORMAT_UNIX;
    return String.format(argName, androidHome);
  }

  /**
   * Something went wrong while invoking Gradle. Since we cannot distinguish an execution error from compilation errors easily, we first try
   * to show, in the "Problems" view, compilation errors by parsing the error output. If no errors are found, we show the stack trace in the
   * "Problems" view. The idea is that we need to somehow inform the user that something went wrong.
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
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
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  @NotNull
  @Override
  public List<String> getCompilableFileExtensions() {
    return ImmutableList.of(SdkConstants.EXT_AIDL, SdkConstants.EXT_FS, SdkConstants.EXT_JAVA, SdkConstants.EXT_RS);
  }
}
