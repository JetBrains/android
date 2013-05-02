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
import com.android.tools.idea.jps.parser.GradleErrorOutputParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.AndroidSourceGeneratingBuilder;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * Builds an IDEA project using Gradle.
 */
public class AndroidGradleBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(AndroidGradleBuilder.class);
  private static final GradleErrorOutputParser ERROR_OUTPUT_PARSER = new GradleErrorOutputParser();

  @NonNls private static final String BUILDER_NAME = "Android Gradle Builder";

  protected AndroidGradleBuilder() {
    super(BuilderCategory.TRANSLATOR);
  }

  @NotNull
  private static File getProjectDirectory(@NotNull CompileContext context, @NotNull JpsAndroidGradleModuleExtension extension)
    throws ProjectBuildException {
    JpsAndroidGradleModuleProperties properties = extension.getProperties();
    String projectPath = properties.PROJECT_ABSOLUTE_PATH;
    if (Strings.isNullOrEmpty(projectPath)) {
      String format = "Unable to obtain path of project '%1$s'. Please re-import the project.";
      String msg = String.format(format, getProjectName(context));
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    File projectDirectory = new File(projectPath);
    if (!projectDirectory.isDirectory()) {
      String format = "The path '%1$s' does not belong to an existing directory";
      String msg = String.format(format, projectPath);
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    return projectDirectory;
  }

  @NotNull
  private static CompilerMessage createCompilerErrorMessage(@NotNull String msg) {
    return AndroidGradleJps.createCompilerMessage(msg, BuildMessage.Kind.ERROR);
  }

  @NotNull
  private static String getProjectName(@NotNull CompileContext context) {
    return context.getProjectDescriptor().getProject().getName();
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
    File projectDirectory = getProjectDirectory(context, extension);
    String androidHome = getAndroidHome(context, chunk);
    String format = "About to build project '%1$s' located at %2$s";
    LOG.info(String.format(format, getProjectName(context), projectDirectory.getAbsolutePath()));
    return doBuild(context, projectDirectory, androidHome);
  }

  @NotNull
  private static String getAndroidHome(@NotNull CompileContext context, @NotNull ModuleChunk chunk) throws ProjectBuildException {
    JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = AndroidGradleJps.getFirstAndroidSdk(chunk);
    if (androidSdk == null) {
      String format = "There is no Android SDK specified for project '%1$s'";
      String msg = String.format(format, getProjectName(context));
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    String androidHome = androidSdk.getHomePath();
    if (Strings.isNullOrEmpty(androidHome)) {
      String msg = "Selected Android SDK does not have a home directory path";
      context.processMessage(createCompilerErrorMessage(msg));
      throw new ProjectBuildException(msg);
    }
    return androidHome;
  }

  @NotNull
  private static ExitCode doBuild(@NotNull CompileContext context, @NotNull File projectDirectory, @NotNull String androidHome)
    throws ProjectBuildException {
    GradleConnector connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDirectory);
    ProjectConnection connection = connector.connect();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      BuildLauncher launcher = connection.newBuild();
      launcher.forTasks("build");
      String androidSdkArg = String.format("-Dandroid.home=\"%1$s\"", androidHome);
      launcher.setJvmArguments(androidSdkArg);
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

  /**
   * Something went wrong while invoking Gradle. Since we cannot distinguish an execution error from compilation errors easily, we first try
   * to show, in the "Problems" view, compilation errors by parsing the error output. If no errors are found, we show the stack trace in the
   * "Problems" view. The idea is that we need to somehow inform the user that something went wrong.
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static void handleBuildException(BuildException e, CompileContext context, String stdErr) throws ProjectBuildException {
    List<CompilerMessage> compilerMessages = ERROR_OUTPUT_PARSER.parseErrorOutput(stdErr);
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
