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
package com.android.tools.idea.gradle.util

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AgpVersions.newProject
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.google.common.io.Resources
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiManagerEx
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperExecutor

private val LOG = Logger.getInstance(GradleWrapper::class.java)

class GradleWrapper private constructor(val propertiesFilePath: File, private val project: Project?) {
  val propertiesFile: VirtualFile?
    get() = VfsUtil.findFileByIoFile(this.propertiesFilePath, true)

  val gradleVersion: String?
    get() {
      val url = this.distributionUrl
      return getGradleVersion(url)
    }

  val distributionUrl: String?
    get() = properties?.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY)

  private val properties: Properties?
    get() = try {
      PropertiesFiles.getProperties(propertiesFilePath)
    } catch (e: IOException) {
      LOG.error("Cannot read properties", e)
      null
    }

  val distributionSha256: String?
    get() = this.properties?.getProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM)

  /**
   * Updates the distribution (URL and checksum) in the Gradle wrapper properties file.
   * Unexpected errors that occur while updating the file will be displayed in an error dialog.
   *
   * @param gradleVersionString a String representing the Gradle version to update the property to.
   * @return `true` if the distribution URL and sha256 were updated, or `false` if no update was
   * necessary because the properties had correct values.
   */
  fun updateDistributionOrDisplayFailure(gradleVersionString: String): Boolean {
    try {
      val version = GradleVersion.version(gradleVersionString)
      val updated = updateDistribution(version)
      if (updated) {
        return true
      }
    }
    catch (e: IOException) {
      val message = with(StringBuilder()) {
        appendLine("Unable to update Gradle wrapper to use Gradle $gradleVersionString")
        appendLine(e.message)
        toString()
      }
      Messages.showErrorDialog(project, message, "Unexpected Error")
    }
    catch (e: IllegalArgumentException) {
      val message = with(StringBuilder()) {
        appendLine("Invalid Gradle version $gradleVersionString")
        appendLine(e.message)
        toString()
      }
      Messages.showErrorDialog(project, message, "Invalid Gradle Version")
    }
    return false
  }

  /**
   * Updates the 'distributionUrl' & 'distributionSha256Sum' in the given Gradle wrapper properties file.
   * It will attempt to preserve type of distribution already used (-all/-bin) based on filename.
   * Update of the checksum is done on a best-effort basis. If no checksum can be found for the new
   * distribution, there will be no 'distributionSha256Sum' property left after the update.
   *
   * @param gradleVersion the Gradle version to update the property to.
   * @return `true` if URL property was updated, or `false` if no update was necessary because
   * the property already had the correct values.
   * @throws IOException if something goes wrong when reading/saving the properties file.
   */
  fun updateDistribution(gradleVersion: GradleVersion): Boolean {
    val urlProperty = this.distributionUrl
    // preserve -all if used, fallback to -bin otherwise.
    val isUsingSourceAndDocsDistribution = urlProperty?.endsWith("-all.zip") == true

    val newUrl = getDistributionUrl(gradleVersion, !isUsingSourceAndDocsDistribution)
    val newDistributionChecksum = getDistributionSha256(gradleVersion, !isUsingSourceAndDocsDistribution)

    val urlUpdateNotRequired = urlProperty != null && urlProperty == newUrl
    val checksumUpdateNotRequired = this.distributionSha256 == newDistributionChecksum
    if (urlUpdateNotRequired && checksumUpdateNotRequired) {
      return false
    }
    val properties = this.properties ?: throw IOException("Cannot read properties")
    properties.setProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, newUrl)
    if (newDistributionChecksum != null) {
      properties.setProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM, newDistributionChecksum)
    } else {
      properties.remove(WrapperExecutor.DISTRIBUTION_SHA_256_SUM)
    }
    saveProperties(properties, this.propertiesFilePath, project)
    return true
  }

  /**
   * Updates the 'distributionUrl' and 'distributionSha256Sum' in the given Gradle wrapper properties file.
   * For standard-named distributions, the SHA-256 hash will first be retrieved from a local list.
   * If the distribution is a fork, the SHA-256 hash will be calculated instead.
   *
   * @param gradleDistribution A local gradle distribution file.
   * @return `true` if both properties were updated, or `false` if no update was necessary because
   *    * the properties already had the correct values.
   * @throws IOException if something goes wrong when reading/saving the properties file.
   */
  @VisibleForTesting
  fun updateDistribution(gradleDistribution: File): Boolean {
    val path = gradleDistribution.path
    require(FileUtilRt.extensionEquals(path, "zip")) { "'$path' should be a zip file" }
    val properties = this.properties ?: throw IOException("Cannot read properties")

    // if the name matches official distribution - use it
    val sha256 = if (distributionsChecksums.contains(gradleDistribution.name)) {
      distributionsChecksums[gradleDistribution.name]
    } else {
      // otherwise if filename is not in the list - calculate SHA-256 of the file.
      try {
        Files.asByteSource(gradleDistribution).hash(Hashing.sha256()).toString()
      } catch (e: IOException) {
        LOG.warn("Cannot read $gradleDistribution for calculating of SHA-256.", e)
        null
      }
    }
    properties.setProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, gradleDistribution.toURI().toURL().toString())
    if (sha256 != null) {
      properties.setProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM, sha256)
    } else {
      properties.remove(WrapperExecutor.DISTRIBUTION_SHA_256_SUM)
    }
    saveProperties(properties, this.propertiesFilePath, project)
    return true
  }

  /**
   * Return the URL for the distribution of Gradle with version indicated by `gradleVersion`, preserving as
   * much of the existing distributionUrl property, if any, as possible.
   *
   * @param gradleVersion the Gradle version to update to
   * @param binOnlyIfCurrentlyUnknown indicates default -bin/-all suffix if the current URL is missing or unrecognized.
   * @return a String denoting the new Gradle distribution URL.
   */
  @VisibleForTesting
  fun getUpdatedDistributionUrl(gradleVersion: GradleVersion, binOnlyIfCurrentlyUnknown: Boolean): String {
    val current = this.distributionUrl
    return getUpdatedDistributionUrl(current, gradleVersion, binOnlyIfCurrentlyUnknown)
  }

  companion object {
    /**
     * A map from Gradle distribution file names (e.g., "gradle-7.5-bin.zip") to their corresponding SHA-256 checksums.
     * This is used to verify the integrity of the downloaded Gradle distribution.
     * The checksums are loaded from the `gradle-sha256-list.txt` resource file.
     */
    @VisibleForTesting
    val distributionsChecksums: Map<String, String> by lazy {
      val bytes = GradleWrapper::class.java.getResourceAsStream("/templates/project/gradle-sha256-list.txt")
                    ?.readAllBytes() ?: return@lazy emptyMap()
      val content = String(bytes, StandardCharsets.UTF_8)
      return@lazy content.lines()
        .map { it.split(';') }
        .filter { it.size == 2 }
        .associate { (sha256, fileName) ->
          fileName to sha256
        }
    }

    private val GRADLEW_PROPERTIES_PATH: String = FileUtil.join(SdkConstants.FD_GRADLE_WRAPPER, SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES)
    private val GRADLE_DISTRIBUTION_URL_PATTERN: Pattern = Pattern.compile(".*/gradle-([^-]+)(-[^\\/\\\\]+)?-(bin|all).zip")

    @JvmStatic
    fun find(project: Project): GradleWrapper? {
      val basePath = project.basePath ?: // Default project. Unlikely to happen.
                     return null
      val baseDir = File(basePath)
      val propertiesFilePath: File = getDefaultPropertiesFilePath(baseDir)
      return if (propertiesFilePath.isFile()) GradleWrapper(propertiesFilePath, project) else null
    }

    @JvmStatic
    fun get(propertiesFilePath: File, project: Project?): GradleWrapper {
      return GradleWrapper(propertiesFilePath, project)
    }

    /**
     * Creates the Gradle wrapper in the project at the given directory.
     *
     * @param projectPath the project's root directory.
     * @param project     the project, if available, or null if this is not in the context of an existing project.
     * @return an instance of `GradleWrapper` if the project already has the wrapper or the wrapper was successfully created.
     * @throws IOException any unexpected I/O error.
     * @see StudioFlags.AGP_VERSION_TO_USE
     */
    @JvmStatic
    fun create(projectPath: File, project: Project?): GradleWrapper {
      return create(projectPath, gradleVersionToUse, project)
    }

    /**
     * Creates the Gradle wrapper in the project at the given directory.
     *
     * @param projectPath   the project's root directory.
     * @param gradleVersion the version of Gradle to use.
     * @param project       the project, if available, or null if this is not in the context of an existing project.
     * @return an instance of `GradleWrapper` if the project already has the wrapper or the wrapper was successfully created.
     * @throws IOException any unexpected I/O error.
     */
    @JvmStatic
    fun create(projectPath: File, gradleVersion: GradleVersion, project: Project?): GradleWrapper {
      val projectDirVirtualFile = VfsUtil.findFileByIoFile(projectPath, true) ?: throw IOException(
        "Not existent project path: $projectPath")
      return create(projectDirVirtualFile, gradleVersion, project)
    }

    /**
     * Creates the Gradle wrapper in the project at the given directory.
     *
     * @param projectPath   the project's root directory.
     * @param gradleVersion the version of Gradle to use.
     * @param project       the project, if available, or null if this is not in the context of an existing project.
     * @return an instance of `GradleWrapper` if the project already has the wrapper or the wrapper was successfully created.
     * @throws IOException any unexpected I/O error.
     */
    fun create(
      projectPath: VirtualFile,
      gradleVersion: GradleVersion,
      project: Project?
    ): GradleWrapper {
      WriteAction.computeAndWait<Any?, IOException?>(ThrowableComputable {
        if (projectPath.findFileByRelativePath(SdkConstants.FD_GRADLE_WRAPPER) == null) {
          val wrapperVf: VirtualFile = wrapperLocation
          val sourceRootUrl = wrapperVf.url
          VfsUtil.copyDirectory(
            GradleWrapper::class.java,
            wrapperVf,
            projectPath
          ) {
            projectPath.findFileByRelativePath(
              it.url.substring(sourceRootUrl.length)
            ) == null
          }
          val gradlewDestination = projectPath.findChild(SdkConstants.FN_GRADLE_WRAPPER_UNIX)
          val madeExecutable = gradlewDestination != null && File(gradlewDestination.path).setExecutable(true)
          if (!madeExecutable) {
            Logger.getInstance(GradleWrapper::class.java).warn("Unable to make gradlew executable")
          }
        }
        null
      })
      val propertiesFilePath: File = getDefaultPropertiesFilePath(File(projectPath.getPath()))
      val gradleWrapper: GradleWrapper = get(propertiesFilePath, project)
      gradleWrapper.updateDistribution(gradleVersion)
      return gradleWrapper
    }

    private val wrapperLocation: VirtualFile
      get() {
        val resource = File("templates/project/wrapper")
        val resourceName = "/" + resource.path.replace('\\', '/')
        val wrapperUrl = Resources.getResource(GradleWrapper::class.java, resourceName)
        val wrapperVf = checkNotNull(VfsUtil.findFileByURL(wrapperUrl)) { "Wrapper URL not found: $wrapperUrl" }
        wrapperVf.refresh(false, true)
        return wrapperVf
      }

    @JvmStatic
    fun getDefaultPropertiesFilePath(projectPath: File): File {
      return File(projectPath, GRADLEW_PROPERTIES_PATH)
    }

    @JvmStatic
    val gradleVersionToUse: GradleVersion
      get() = getCompatibleGradleVersion(newProject).version

    private fun saveProperties(properties: Properties, file: File, project: Project?) {
      PropertiesFiles.savePropertiesToFile(properties, file, null)
      val virtualFile = VfsUtil.findFileByIoFile(file, false)
      if (virtualFile != null) {
        virtualFile.refresh(false, false)
        if (project != null) {
          val manager = PsiDocumentManager.getInstance(project)
          val psiFile = PsiManagerEx.getInstanceEx(project).findFile(virtualFile)
          if (psiFile != null) {
            val document = manager.getDocument(psiFile)
            if (document != null) {
              val app = ApplicationManager.getApplication()
              app.invokeAndWait(Runnable { app.runWriteAction { manager.commitDocument(document) } })
            }
          }
        }
      }
    }

    /**
     * @param url the URL of the Gradle distribution from which to extract the version of Gradle.
     * @return the version of Gradle encoded in {@param url}
     */
    fun getGradleVersion(url: String?): String? {
      if (url == null) return null
      val m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url)
      if (m.matches()) {
        return m.group(1) + Strings.nullToEmpty(m.group(2))
      }
      return null
    }

    /**
     * Return the URL for the distribution of Gradle with version indicated by {@param gradleVersion}, preserving as
     * much of the existing {@param url} property, if any, as possible.
     *
     * @param url the URL of the Gradle distribution to use as a basis.
     * @param gradleVersion the new Gradle version to use.
     * @param binOnlyIfCurrentlyUnknown indicates default -bin/-all suffix if {@param url} is null or unrecognized .
     * @return a String denoting the new Gradle distribution URL.
     */
    fun getUpdatedDistributionUrl(url: String?, gradleVersion: GradleVersion, binOnlyIfCurrentlyUnknown: Boolean): String {
      if (url == null) {
        // No idea about the current URL: return the default URL.
        return getDistributionUrl(gradleVersion, binOnlyIfCurrentlyUnknown)
      }
      else if (url.contains("://services.gradle.org/")) {
        val m: Matcher = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url)
        return if (m.matches()) {
          // Return the canonical URL, preserving the -bin/-all suffix.
          getDistributionUrl(gradleVersion, "bin" == m.group(3))
        }
        else {
          // The current URL doesn't match; can't update, so return the default URL.
          getDistributionUrl(gradleVersion, binOnlyIfCurrentlyUnknown)
        }
      }
      else {
        val m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url)
        if (m.matches()) {
          // Return the current URL with the new version number spliced in.
          val sb = StringBuilder()
          sb.append(url, 0, m.start(1))
          sb.append(gradleVersion.version)
          sb.append(url, if (m.end(2) == -1) m.end(1) else m.end(2), url.length)
          return sb.toString()
        }
        else {
          // The current URL doesn't match; can't update, so return the default URL.
          return getDistributionUrl(gradleVersion, binOnlyIfCurrentlyUnknown)
        }
      }
    }

    /**
     * @param useBinaryOnlyDistribution - when true this will use -bin distribution (only binaries),
     * otherwise the -all distribution (binaries + source code & documentation).
     */
    @JvmStatic
    fun getDistributionUrl(gradleVersion: GradleVersion, useBinaryOnlyDistribution: Boolean): String {
      val suffix = if (useBinaryOnlyDistribution) "bin" else "all"
      val filename = String.format("gradle-%1\$s-%2\$s.zip", gradleVersion.getVersion(), suffix)

      val localDistributionUrl = StudioFlags.GRADLE_LOCAL_DISTRIBUTION_URL.get()
      if (!localDistributionUrl.isEmpty()) {
        return localDistributionUrl + filename
      }

      // See https://code.google.com/p/android/issues/detail?id=357944
      val folderName = if (gradleVersion.isSnapshot()) "distributions-snapshots" else "distributions"
      return String.format("https://services.gradle.org/%1\$s/%2\$s", folderName, filename)
    }

    @JvmStatic
    fun getDistributionSha256(gradleVersion: GradleVersion, useBinaryOnlyDistribution: Boolean): String? {
      val distributionUrl = getDistributionUrl(gradleVersion, useBinaryOnlyDistribution)
      val distributionFile = distributionUrl.substringAfterLast("/").takeIf { it.endsWith(".zip") }
                             ?: return null
      return distributionsChecksums[distributionFile]
    }
  }
}
