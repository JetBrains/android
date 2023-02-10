/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.templates.diff

import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.smartDiffAgpVersion
import com.android.tools.idea.wizard.template.Template
import com.google.common.truth.Truth
import com.intellij.util.containers.isEmpty
import com.intellij.util.io.isDirectory
import org.junit.Assert
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path

class ProjectDiffer(template: Template) : ProjectRenderer(template) {
  override fun handleDirectories(goldenDir: Path, projectDir: Path) {
    diffDirectories(goldenDir, projectDir, "")
  }
}

/**
 * Recursively diffs the files in two directories, ignoring certain filenames specified by FILES_TO_IGNORE
 */
private fun diffDirectories(goldenDir: Path, projectDir: Path, printPrefix: String = "") {
  val goldenFiles = getNonEmptyDirEntries(goldenDir, printPrefix)
  val projectFiles = getNonEmptyDirEntries(projectDir, printPrefix)
  projectFiles.removeAll(FILES_TO_IGNORE.toSet())

  Assert.assertEquals(goldenFiles, projectFiles)

  if (goldenFiles.isEmpty()) {
    return
  }

  for (filename in goldenFiles) {
    val projectFile = projectDir.resolve(filename)
    val goldenFile = goldenDir.resolve(filename)

    Assert.assertEquals("$projectFile and $goldenFile are not of same file type", projectFile.isDirectory(), goldenFile.isDirectory())

    if (projectFile.isDirectory()) {
      println("${printPrefix}$projectFile is a directory")
      diffDirectories(goldenFile, projectFile, "$printPrefix..")
      continue
    }

    println("${printPrefix}diffing $projectFile and $goldenFile")

    // Checking whether it's a text file is complicated, and we don't really need to check the text, it's just for human readability, so
    // diffing all lines and only reading bytes if text fails is simpler.
    try {
      val goldenLines = Files.readAllLines(goldenFile).map { replaceVariables(it, printPrefix) }
      val projectLines = Files.readAllLines(projectFile).map { replaceVariables(it, printPrefix) }
      Truth.assertThat(projectLines).isEqualTo(goldenLines)
    }
    catch (error: MalformedInputException) {
      println("${printPrefix}reading lines failed, compare bytes instead")
      val goldenBytes = Files.readAllBytes(goldenFile)
      val projectBytes = Files.readAllBytes(projectFile)
      Truth.assertThat(projectBytes).isEqualTo(goldenBytes)
    }
  }
}

private fun getNonEmptyDirEntries(dir: Path, printPrefix: String = ""): MutableSet<String> {
  return Files.list(dir).filter {
    !it.isDirectory() || !isDirectoryEmpty(it, printPrefix)
  }.map { it.fileName.toString() }.toList().toMutableSet()
}

private fun isDirectoryEmpty(dir: Path, printPrefix: String = ""): Boolean {
  val empty = Files.list(dir).isEmpty()
  if (empty) {
    println("${printPrefix}Skipping empty $dir")
  }
  return empty
}

private fun replaceVariables(text: String, printPrefix: String = ""): String {
  replaceDates(text, printPrefix).also { dateReplaced ->
    replaceDistributionUrl(dateReplaced, printPrefix).also { distributionReplaced ->
      replaceMavenRepoUrl(distributionReplaced, printPrefix).also { mavenReplaced ->
        return replaceLatestGradleVersion(mavenReplaced, printPrefix)
      }
    }
  }
}

// TODO: check what localization it is that we generate the files with, date format could be different...
// This applies to gradle-wrapper.properties and local.properties. Example: #Tue Mar 21 15:26:47 PDT 2023
// According to Wikipedia's list of time zone abbreviations, some of them just use +03, etc. instead of being letters, hence the \S+
val PROPERTIES_DATE = Regex(
  "(Mon|Tue|Wed|Thu|Fri|Sat|Sun) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \\d{2} \\d{2}:\\d{2}:\\d{2} \\S+ \\d{4}")

// This string replaces dates in template-generated files. See replaceDates.
const val PROPERTIES_DATE_REPLACEMENT = "DATE_REPLACED"

/**
 * Some metadata files contain the generated date/time, which we need to replace in order to diff the whole file
 */
private fun replaceDates(text: String, printPrefix: String = ""): String {
  val replacedText = PROPERTIES_DATE.replace(text, PROPERTIES_DATE_REPLACEMENT)
  if (replacedText != text) {
    println("${printPrefix}Replaced date in $text")
  }
  return replacedText
}

// The distributionUrl value in gradle-wrapper.properties of similar format to:
// distributionUrl=file\:/C\:/src/studio-main/tools/external/gradle/gradle-8.0-bin.zip
val GRADLE_DISTRIBUTION_URL = Regex("distributionUrl=.*[\\\\/]")

// This string replaces the distributionUrl path in template-generated files. See replaceDistributionUrl.
// It replaces the path such that we can still diff the gradle version:
// distributionUrl=DISTRIBUTION_URL_REPLACEDgradle-8.0-bin.zip
const val GRADLE_DISTRIBUTION_URL_REPLACEMENT = "distributionUrl=DISTRIBUTION_URL_REPLACED"

/**
 * gradle-wrapper.properties files contain the generated Gradle distribution path, which we need to replace in order to diff the whole file
 */
private fun replaceDistributionUrl(text: String, printPrefix: String = ""): String {
  val replacedText = GRADLE_DISTRIBUTION_URL.replace(text, GRADLE_DISTRIBUTION_URL_REPLACEMENT)
  if (replacedText != text) {
    println("${printPrefix}Replaced distribution URL in $text")
  }
  return replacedText
}

// The url value in build.gradle under the maven section. When run from IDEA it may be of similar format to:
// url "file:/C:/src/studio-main/prebuilts/tools/common/m2/repository/"
// When run from Bazel it may be of similar format to:
// url "file:/C:/botcode/w40/_tmp/64cb621d51aeb63b419ca295dc73be0b/offline-maven-repo/"
val MAVEN_REPO_URL = Regex("url \".*[\\\\/]\"")

// This string replaces the Maven repo path in template-generated files. See replaceMavenRepoUrl.
const val MAVEN_REPO_URL_REPLACEMENT = "url \"MAVEN_REPO_PATH\""

/**
 * build.gradle files contain the generated Maven repo path, which we need to replace in order to diff the whole file
 */
private fun replaceMavenRepoUrl(text: String, printPrefix: String = ""): String {
  val replacedText = MAVEN_REPO_URL.replace(text, MAVEN_REPO_URL_REPLACEMENT)
  if (replacedText != text) {
    println("${printPrefix}Replaced Maven repo URL in $text")
  }
  return replacedText
}

// The dependency in build.gradle of similar format to:
// com.android.tools.build:gradle:8.0.0-beta04
val AGP_VERSION_CLASSPATH = Regex("com\\.android\\.tools\\.build:gradle:.*")

// This string replaces the dependency classpath in template-generated build.gradle. See replaceLatestGradleVersion.
// It is only replaced when we test the latest AGP version.
const val AGP_VERSION_CLASSPATH_REPLACEMENT = "LATEST_GRADLE_VERSION"

// The Gradle version in gradle-wrapper.properties of similar format to:
// distributionUrl=file\:/C\:/src/studio-main/tools/external/gradle/gradle-8.0-bin.zip
val GRADLE_VERSION_ZIP = Regex("gradle-\\d\\.\\d(\\.\\d)?-bin\\.zip")

// This string replaces the distributionUrl file path in template-generated files. See replaceLatestGradleVersion.
// It is only replaced when we test the latest AGP version.
const val GRADLE_VERSION_ZIP_REPLACEMENT = "gradle-LATEST_VERSION-bin.zip"

/**
 * When testing the latest AGP version, we replace the version string because it changes ~weekly, to avoid constantly updating golden files.
 */
private fun replaceLatestGradleVersion(text: String, printPrefix: String = ""): String {
  if (!smartDiffAgpVersion()) {
    return text
  }

  var replacedText = AGP_VERSION_CLASSPATH.replace(text, AGP_VERSION_CLASSPATH_REPLACEMENT)
  replacedText = GRADLE_VERSION_ZIP.replace(replacedText, GRADLE_VERSION_ZIP_REPLACEMENT)
  if (replacedText != text) {
    println("${printPrefix}Replaced AGP and/or Gradle version in $text")
  }
  return replacedText
}