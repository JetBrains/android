/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.*;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.inspections.lint.IntellijLintIssueRegistry;
import org.jetbrains.android.inspections.lint.IntellijLintRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectValidator {
  private static final Pattern ANDROID_GRADLE_PLUGIN_DEPENDENCY_PATTERN = Pattern.compile("['\"]com.android.tools.build:gradle:(.+)['\"]");
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN =
    Pattern.compile("http://services\\.gradle\\.org/distributions/gradle-(.+)-(.+)\\.zip");

  private static final Logger LOG = Logger.getInstance(ProjectValidator.class);

  public static final Key<List<Message>> VALIDATION_MESSAGES = Key.create("gradle.validation.messages");

  private static final Set<String> FILES_TO_PROCESS =
    ImmutableSet.of(SdkConstants.FN_SETTINGS_GRADLE, SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_LOCAL_PROPERTIES,
                    SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES);

  private static final String MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13 = "2.0";
  private static final FullRevision MINIMUM_SUPPORTED_GRADLE_REVISION_FOR_PLUGIN_0_13 =
    FullRevision.parseRevision(MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13);

  private ProjectValidator() {
  }

  /**
   * Runs lint checks on all the Gradle files found underneath the given root directory and reports messages to the
   * {@link ProjectSyncMessages} message window. Returns true if there are no significant problems, or false if there are critical errors.
   * Any errors or warnings that are generated are saved, and are send to the Messages window upon a subsequent call to the
   * {@link #mergeQueuedMessages(com.intellij.openapi.project.Project)} method.
   */
  public static boolean validate(@NotNull Project project, @NotNull File rootDir) {
    VirtualFile file = VfsUtil.findFileByIoFile(rootDir, true);
    if (file == null) {
      return false;
    }
    VirtualFile rootDirectory = file.isDirectory() ? file : file.getParent();

    final List<File> filesToProcess = Lists.newArrayList();
    VfsUtil.processFileRecursivelyWithoutIgnored(rootDirectory, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (FILES_TO_PROCESS.contains(virtualFile.getName().toLowerCase())) {
          filesToProcess.add(VfsUtilCore.virtualToIoFile(virtualFile));
        }
        return true;
      }
    });

    attemptToUpdateGradleVersionIfApplicable(project, filesToProcess);

    if (project.isInitialized()) {
      // Lint requires the project to be initialized.
      MyLintClient lintClient = new MyLintClient(project);
      ImmutableList<Module> modules = ImmutableList.of();
      LintRequest request = new IntellijLintRequest(lintClient, project, null, modules, false) {
        @NonNull
        @Override
        public List<File> getFiles() {
          return filesToProcess;
        }
      };
      LintDriver lintDriver = new LintDriver(new IntellijLintIssueRegistry(), lintClient);
      lintDriver.analyze(request);
      return !lintClient.hasFatalError();
    }

    return true;
  }

  private static void attemptToUpdateGradleVersionIfApplicable(@NotNull Project project, @NotNull List<File> gradleFiles) {
    String originalPluginVersion = null;
    for (File fileToCheck : gradleFiles) {
      if (SdkConstants.FN_BUILD_GRADLE.equals(fileToCheck.getName())) {
        try {
          String contents = Files.toString(fileToCheck, Charsets.UTF_8);
          Matcher matcher = ANDROID_GRADLE_PLUGIN_DEPENDENCY_PATTERN.matcher(contents);
          if (matcher.find()) {
            originalPluginVersion = matcher.group(1);
            if (!StringUtil.isEmpty(originalPluginVersion)) {
              break;
            }
          }
        }
        catch (IOException e) {
          LOG.warn("Failed to read contents of " + fileToCheck.getPath());
        }
      }
    }
    if (StringUtil.isEmpty(originalPluginVersion)) {
      // Could not obtain plug-in version. Continue with sync.
      return;
    }
    String pluginVersion = originalPluginVersion.replace('+', '0');
    FullRevision pluginRevision = null;
    try {
      pluginRevision = FullRevision.parseRevision(pluginVersion);
    }
    catch (NumberFormatException e) {
      LOG.warn("Failed to parse '" + pluginVersion + "'");
    }
    if (pluginRevision == null || (pluginRevision.getMajor() == 0 && pluginRevision.getMinor() <= 12)) {
      // Unable to parse the plug-in version, or the plug-in is version 0.12 or older. Continue with sync.
      return;
    }

    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;

    boolean usingWrapper = distributionType == DistributionType.DEFAULT_WRAPPED && wrapperPropertiesFile != null;
    if (usingWrapper) {
      attemptToUpdateGradleVersionInWrapper(wrapperPropertiesFile, originalPluginVersion, project);
    }
    else if (distributionType == DistributionType.LOCAL) {
      attemptToUseSupportedLocalGradle(gradleSettings);
    }
  }

  private static void attemptToUpdateGradleVersionInWrapper(@NotNull final File wrapperPropertiesFile,
                                                            @NotNull String pluginVersion,
                                                            @NotNull Project project) {
    Properties wrapperProperties = null;
    try {
      wrapperProperties = GradleUtil.loadGradleWrapperProperties(wrapperPropertiesFile);
    }
    catch (IOException e) {
      LOG.warn("Failed to read file " + wrapperPropertiesFile.getPath());
    }

    if (wrapperProperties == null) {
      // There is a wrapper, but the Gradle version could not be read. Continue with sync.
      return;
    }
    String url = wrapperProperties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY);
    Matcher matcher = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url);
    if (!matcher.matches()) {
      // Could not get URL of Gradle distribution. Continue with sync.
      return;
    }
    String gradleVersion = matcher.group(1);
    FullRevision gradleRevision = FullRevision.parseRevision(gradleVersion);

    if (!isSupportedGradleVersion(gradleRevision)) {
      String newGradleVersion = MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13;
      String msg = "Version " + pluginVersion + " of the Android Gradle plug-in requires Gradle " + newGradleVersion + " or newer.\n\n" +
                   "Click 'OK' to automatically update the Gradle version in the Gradle wrapper and continue.";
      Messages.showMessageDialog(project, msg, "Gradle Sync", Messages.getQuestionIcon());
      try {
        GradleUtil.updateGradleDistributionUrl(newGradleVersion, wrapperPropertiesFile);
      }
      catch (IOException e) {
        LOG.warn("Failed to update Gradle wrapper file to Gradle version " + newGradleVersion);
      }
    }
  }

  private static void attemptToUseSupportedLocalGradle(@NotNull GradleProjectSettings gradleSettings) {
    String gradleHome = gradleSettings.getGradleHome();
    if (StringUtil.isEmpty(gradleHome)) {
      // Unable to obtain the path of the Gradle local installation. Continue with sync.
      return;
    }
    File gradleHomePath = new File(gradleHome);
    FullRevision gradleVersion = GradleUtil.getGradleVersion(gradleHomePath);

    if (gradleVersion == null) {
      // Unable to obtain the path of the Gradle local installation. Continue with sync.
      return;
    }

    if (!isSupportedGradleVersion(gradleVersion)) {
      String newGradleVersion = MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13;
      ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog(newGradleVersion);
      dialog.setTitle(String.format("Please select the location of a Gradle distribution version %1$s or newer", newGradleVersion));
      if (dialog.showAndGet()) {
        String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
        gradleSettings.setGradleHome(enteredGradleHomePath);
      }
    }
  }

  private static boolean isSupportedGradleVersion(@NotNull FullRevision gradleVersion) {
    // Plug-in v0.13.+ supports Gradle 2.0+ only.
    return gradleVersion.compareTo(MINIMUM_SUPPORTED_GRADLE_REVISION_FOR_PLUGIN_0_13) >= 0;
  }

  /**
   * Takes any accumulated validation errors that have been stored with the project and shows them in the Messages tool window.
   */
  public static void mergeQueuedMessages(@NotNull Project project) {
    List<Message> messages = project.getUserData(VALIDATION_MESSAGES);
    if (messages == null) {
      return;
    }
    ProjectSyncMessages projectSyncMessages = ProjectSyncMessages.getInstance(project);
    for (Message message : messages) {
      projectSyncMessages.add(message);
    }
    project.putUserData(VALIDATION_MESSAGES, null);
  }

  private static class MyLintClient extends IntellijLintClient {
    private static final String GROUP_NAME = "Project import";
    private boolean myFatalError = false;
    private final List<Message> myMessages = Lists.newArrayList();

    protected MyLintClient(@NonNull Project project) {
      super(project);
      project.putUserData(VALIDATION_MESSAGES, myMessages);
    }

    @Override
    public void report(@NonNull Context context,
                       @NonNull Issue issue,
                       @NonNull Severity severity,
                       @Nullable Location location,
                       @NonNull String message,
                       @Nullable Object data) {
      myFatalError |= severity.compareTo(Severity.ERROR) <= 0;

      File file = location != null ? location.getFile() : null;
      VirtualFile virtualFile = file != null ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
      Position start = location != null ? location.getStart() : null;
      Message.Type type = convertSeverity(severity);
      if (virtualFile != null && start != null) {
        try {
          int line = start.getLine();
          int column = start.getColumn();
          int offset = start.getOffset();
          if (line == -1 && offset >= 0) {
            PsiManager psiManager = PsiManager.getInstance(myProject);
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile != null) {
              Document document = psiDocumentManager.getDocument(psiFile);
              if (document != null) {
                line = document.getLineNumber(offset);
                column = offset - document.getLineStartOffset(line);
              }
            }
          }
          myMessages.add(new Message(myProject, GROUP_NAME, type, virtualFile, line, column, message));
        }
        catch (Exception e) {
          // There are cases where the offset lookup is wrong; e.g.
          //   java.lang.IndexOutOfBoundsException: Wrong offset: 312. Should be in range: [0, 16]
          // in this case, just report the message without a location
          myMessages.add(new Message(GROUP_NAME, type, message));
        }
      }
      else {
        myMessages.add(new Message(GROUP_NAME, type, message));
      }
    }

    @Override
    public void log(@NonNull Severity severity, @Nullable Throwable exception, @Nullable String format, @Nullable Object... args) {
      myMessages.add(new Message(GROUP_NAME, convertSeverity(severity), format != null ? String.format(format, args) : ""));
      if (exception != null) {
        LOG.warn("Exception occurred during validation of project", exception);
      }
    }

    @NonNull
    private static Message.Type convertSeverity(@NonNull Severity severity) {
      switch (severity) {
        case ERROR:
        case FATAL:
          return Message.Type.ERROR;
        case IGNORE:
          return Message.Type.INFO;
        default:
        case INFORMATIONAL:
          return Message.Type.INFO;
        case WARNING:
          return Message.Type.WARNING;
      }
    }

    public boolean hasFatalError() {
      return myFatalError;
    }

    @NonNull
    @Override
    protected List<Issue> getIssues() {
      return new IntellijLintIssueRegistry().getIssues();
    }

    @Nullable
    @Override
    protected Module getModule() {
      return null;
    }

    @Override
    public boolean isProjectDirectory(@NonNull File dir) {
      return new File(dir, SdkConstants.FN_BUILD_GRADLE).exists();
    }
  }
}
