/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_UNIX;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByURL;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_SHA_256_SUM;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wizard.template.TemplateData;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.io.Resources;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleWrapper {
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN = Pattern.compile(".*/gradle-([^-]+)(-[^\\/\\\\]+)?-(bin|all).zip");

  @NotNull private final File myPropertiesFilePath;
  @Nullable private final Project myProject;

  @Nullable
  public static GradleWrapper find(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) {
      // Default project. Unlikely to happen.
      return null;
    }
    File baseDir = new File(basePath);
    File propertiesFilePath = getDefaultPropertiesFilePath(baseDir);
    return propertiesFilePath.isFile() ? new GradleWrapper(propertiesFilePath, project) : null;
  }

  @NotNull
  public static GradleWrapper get(@NotNull File propertiesFilePath, @Nullable Project project) {
    return new GradleWrapper(propertiesFilePath, project);
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectPath the project's root directory.
   * @param project     the project, if available, or null if this is not in the context of an existing project.
   * @return an instance of {@code GradleWrapper} if the project already has the wrapper or the wrapper was successfully created.
   * @throws IOException any unexpected I/O error.
   * @see StudioFlags#AGP_VERSION_TO_USE
   */
  @NotNull
  public static GradleWrapper create(@NotNull File projectPath, @Nullable Project project) throws IOException {
    return create(projectPath, GradleWrapper.getGradleVersionToUse(), project);
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectPath   the project's root directory.
   * @param gradleVersion the version of Gradle to use.
   * @param project       the project, if available, or null if this is not in the context of an existing project.
   * @return an instance of {@code GradleWrapper} if the project already has the wrapper or the wrapper was successfully created.
   * @throws IOException any unexpected I/O error.
   */
  @NotNull
  public static GradleWrapper create(@NotNull File projectPath, @NotNull String gradleVersion, @Nullable Project project)
    throws IOException {
    VirtualFile projectDirVirtualFile = findFileByIoFile(projectPath, true);
    if (projectDirVirtualFile == null) throw new IOException("Not existent project path: " + projectPath);
    return create(projectDirVirtualFile, gradleVersion, project);
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectPath   the project's root directory.
   * @param gradleVersion the version of Gradle to use.
   * @param project       the project, if available, or null if this is not in the context of an existing project.
   * @return an instance of {@code GradleWrapper} if the project already has the wrapper or the wrapper was successfully created.
   * @throws IOException any unexpected I/O error.
   */
  @NotNull
  public static GradleWrapper create(@NotNull VirtualFile projectPath,
                                     @NotNull String gradleVersion,
                                     @Nullable Project project) throws IOException {
    WriteAction.computeAndWait(() -> {
      if (projectPath.findFileByRelativePath(FD_GRADLE_WRAPPER) == null) {
        VirtualFile wrapperVf = getWrapperLocation();
        String sourceRootUrl = wrapperVf.getUrl();
        VfsUtil.copyDirectory(GradleWrapper.class, wrapperVf, projectPath, it ->
          projectPath.findFileByRelativePath(it.getUrl().substring(sourceRootUrl.length())) == null
        );
        VirtualFile gradlewDest = projectPath.findChild(FN_GRADLE_WRAPPER_UNIX);
        boolean madeExecutable = gradlewDest != null && new File(gradlewDest.getPath()).setExecutable(true);
        if (!madeExecutable) {
          Logger.getInstance(GradleWrapper.class).warn("Unable to make gradlew executable");
        }
      }
      return null;
    });
    File propertiesFilePath = getDefaultPropertiesFilePath(new File(projectPath.getPath()));
    GradleWrapper gradleWrapper = get(propertiesFilePath, project);
    gradleWrapper.updateDistributionUrl(gradleVersion);
    return gradleWrapper;
  }

  @NotNull
  private static VirtualFile getWrapperLocation() {
    File resource = new File("templates/project/wrapper");
    String resourceName = "/" + resource.getPath().replace('\\', '/');
    URL wrapperUrl = Resources.getResource(TemplateData.class, resourceName);
    VirtualFile wrapperVf = findFileByURL(wrapperUrl);
    assert wrapperVf != null;
    wrapperVf.refresh(false, true);
    return wrapperVf;
  }

  private GradleWrapper(@NotNull File propertiesFilePath, @Nullable Project project) {
    myProject = project;
    myPropertiesFilePath = propertiesFilePath;
  }

  @NotNull
  public File getPropertiesFilePath() {
    return myPropertiesFilePath;
  }

  @Nullable
  public VirtualFile getPropertiesFile() {
    return findFileByIoFile(myPropertiesFilePath, true);
  }

  @NotNull
  public static File getDefaultPropertiesFilePath(@NotNull File projectPath) {
    return new File(projectPath, GRADLEW_PROPERTIES_PATH);
  }

  /**
   * Updates the 'distributionUrl' in the Gradle wrapper properties file. An unexpected errors that occur while updating the file will be
   * displayed in an error dialog.
   *
   * @param gradleVersion the Gradle version to update the property to.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   */
  public boolean updateDistributionUrlAndDisplayFailure(@NotNull String gradleVersion) {
    try {
      boolean updated = updateDistributionUrl(gradleVersion);
      if (updated) {
        return true;
      }
    }
    catch (IOException e) {
      String msg = String.format("Unable to update Gradle wrapper to use Gradle %1$s\n", gradleVersion);
      msg += e.getMessage();
      Messages.showErrorDialog(myProject, msg, "Unexpected Error");
    }
    return false;
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file.
   *
   * @param gradleVersion the Gradle version to update the property to.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   * @throws IOException if something goes wrong when saving the file.
   */
  public boolean updateDistributionUrl(@NotNull String gradleVersion) throws IOException {
    Properties properties = getProperties();
    String distributionUrl = getDistributionUrl(gradleVersion, true);
    String property = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (property != null && (property.equals(distributionUrl) || property.equals(getDistributionUrl(gradleVersion, true)))) {
      return false;
    }
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, distributionUrl);
    saveProperties(properties, myPropertiesFilePath, myProject);
    return true;
  }

  public static String getGradleVersionToUse() {
    String agpVersion = StudioFlags.AGP_VERSION_TO_USE.get();
    if (agpVersion.isEmpty()) {
      return GRADLE_LATEST_VERSION;
    }

    AgpVersion parsedVersion = AgpVersion.parse(agpVersion);
    CompatibleGradleVersion gradleVersion = CompatibleGradleVersion.Companion.getCompatibleGradleVersion(parsedVersion);

    return gradleVersion.getVersion().getVersion();
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file.
   *
   * @param gradleDistribution A local gradle distribution file.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   * @throws IOException if something goes wrong when saving the file.
   */
  public boolean updateDistributionUrl(@NotNull File gradleDistribution) throws IOException {
    String path = gradleDistribution.getPath();
    if (!extensionEquals(path, "zip")) {
      throw new IllegalArgumentException("'" + path + "' should be a zip file");
    }
    Properties properties = getProperties();
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, gradleDistribution.toURI().toURL().toString());
    saveProperties(properties, myPropertiesFilePath, myProject);
    return true;
  }

  @NotNull
  public Properties getProperties() throws IOException {
    return PropertiesFiles.getProperties(myPropertiesFilePath);
  }

  private static void saveProperties(@NotNull Properties properties, @NotNull File file, @Nullable Project project) throws IOException {
    savePropertiesToFile(properties, file, null);
    VirtualFile virtualFile = findFileByIoFile(file, false);
    if (virtualFile != null) {
      virtualFile.refresh(false, false);
      if (project != null) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(virtualFile);
        if (psiFile != null) {
          Document document = manager.getDocument(psiFile);
          if (document != null) {
            Application app = ApplicationManager.getApplication();
            app.invokeAndWait(() -> app.runWriteAction(() -> manager.commitDocument(document)));
          }
        }
      }
    }
  }

  @Nullable
  public String getGradleVersion() throws IOException {
    String url = getProperties().getProperty(DISTRIBUTION_URL_PROPERTY);
    if (url != null) {
      Matcher m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url);
      if (m.matches()) {
        return m.group(1) + Strings.nullToEmpty(m.group(2));
      }
    }
    return null;
  }

  @Nullable
  public String getDistributionSha256Sum() throws IOException {
    return getProperties().getProperty(DISTRIBUTION_SHA_256_SUM);
  }

  @Nullable
  public String getDistributionUrl() throws IOException {
    return getProperties().getProperty(DISTRIBUTION_URL_PROPERTY);
  }

  /**
   * Return the URL for the distribution of Gradle version indicated by {@code gradleVersion}, preserving as
   * much of the existing distributionUrl property, if any, as possible.
   *
   * @param gradleVersion the version number to update to
   * @param binOnlyIfCurrentlyUnknown indicates default -bin/-all suffix if the current URL is unparseable
   * @return a String denoting the new Gradle distribution URL.
   */
  @NotNull
  public String getUpdatedDistributionUrl(String gradleVersion, boolean binOnlyIfCurrentlyUnknown) throws IOException {
    String current = getDistributionUrl();
    if (current == null) {
      // No idea about the current URL: return the default URL.
      return GradleWrapper.getDistributionUrl(gradleVersion, binOnlyIfCurrentlyUnknown);
    }
    else if (current.contains("://services.gradle.org/")) {
      Matcher m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(current);
      if (m.matches()) {
        // Return the canonical URL, preserving the -bin/-all suffix.
        return GradleWrapper.getDistributionUrl(gradleVersion, "bin".equals(m.group(3)));
      }
      else {
        // The current URL doesn't match; can't update, so return the default URL.
        return GradleWrapper.getDistributionUrl(gradleVersion, binOnlyIfCurrentlyUnknown);
      }
    }
    else {
      Matcher m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(current);
      if (m.matches()) {
        // Return the current URL with the new version number spliced in.
        StringBuilder sb = new StringBuilder();
        sb.append(current, 0, m.start(1));
        sb.append(gradleVersion);
        sb.append(current, m.end(2) == -1 ? m.end(1) : m.end(2), current.length());
        return sb.toString();
      }
      else {
        // The current URL doesn't match; can't update, so return the default URL.
        return GradleWrapper.getDistributionUrl(gradleVersion, binOnlyIfCurrentlyUnknown);
      }
    }
  }

  @NotNull
  public static String getDistributionUrl(@NotNull String gradleVersion, boolean binOnly) {
    String suffix = binOnly ? "bin" : "all";
    String filename = String.format("gradle-%1$s-%2$s.zip", gradleVersion, suffix);

    String localDistributionUrl = StudioFlags.GRADLE_LOCAL_DISTRIBUTION_URL.get();
    if (!localDistributionUrl.isEmpty()) {
      return localDistributionUrl + filename;
    }

    // See https://code.google.com/p/android/issues/detail?id=357944
    String folderName = isSnapshot(gradleVersion) ? "distributions-snapshots" : "distributions";
    return String.format("https://services.gradle.org/%1$s/%2$s", folderName, filename);
  }

  @VisibleForTesting
  static boolean isSnapshot(@NotNull String gradleVersion) {
    return gradleVersion.indexOf('-') != -1 && gradleVersion.endsWith("+0000");
  }
}
