/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import java.io.File

val GRADLE_SNAPSHOTS_URL = "https://services.gradle.org/distributions-snapshots"
val TOOLS_EXTERNAL_GRADLE_PATH = "tools/external/gradle"
val AGP_INTEGRATION_TESTS_PATH = "tools/adt/idea/android-test-framework/testSrc/com/android/tools/idea/testing/AgpIntegrationTests.kt"


fun main(args: Array<String>) {
  val type = args[0]
  val repoDir = System.getenv("BUILD_WORKSPACE_DIRECTORY")
  checkNotNull(repoDir) { "Must use bazel to invoke this script!" }
  check(type == "gradle" || type == "kotlin") { "first argument must be gradle or kotlin"}
  if (type == "gradle") {
    val gradleVersion = args[1]

    val distName = gradleVersion.versionToDist()
    val url = "$GRADLE_SNAPSHOTS_URL/$distName"

    var oldGradleVersion: String? = null

    println("Record the old version and update gradle version")
    File("$repoDir/$TOOLS_EXTERNAL_GRADLE_PATH/BUILD").apply {
      val lineStart = "GRADLE_LATEST_SNAPSHOT_VERSION = "
      oldGradleVersion = readLines().singleOrNull { it.startsWith(lineStart) }?.split("=", "\"")?.filter { it.isNotBlank() }?.last()?.trim()
      replaceLine(lineStart = lineStart, newVersion = gradleVersion)
    }
    checkNotNull(oldGradleVersion)
    println("Old version: $oldGradleVersion")

    println("Download the new version")
    "curl -L -o $repoDir/$TOOLS_EXTERNAL_GRADLE_PATH/$distName $url".runCommand(repoDir)

    println("Delete the old version")
    File("$repoDir/$TOOLS_EXTERNAL_GRADLE_PATH/${oldGradleVersion!!.versionToDist()}").delete()

    println("Update the gradle version in the test code")
    File("$repoDir/$AGP_INTEGRATION_TESTS_PATH")
      .replaceLine("const val GRADLE_SNAPSHOT_VERSION = ", newVersion = gradleVersion)

    val commitMessage = """
    Update comparison benchmark Gradle version

    to $gradleVersion

    Bug: N/A
    Test: N/A
  """.trimIndent()
    println("Creating tools/external/gradle change")
    runGitCommit(repoDir, TOOLS_EXTERNAL_GRADLE_PATH, commitMessage)
    println("Creating tools/adt/idea change")
    runGitCommit(repoDir, "tools/adt/idea", commitMessage, listOf(AGP_INTEGRATION_TESTS_PATH))
  } else {
    val kotlinVersion = args[1]

    println("Record the old version and update kotlin version")
    var oldKotlinVersion: String? = null
    File("$repoDir/$AGP_INTEGRATION_TESTS_PATH").apply {
      val lineStart = "const val KOTLIN_SNAPSHOT_VERSION = "
      oldKotlinVersion = readLines().singleOrNull { it.startsWith(lineStart) }?.split("=", "\"")?.last { it.isNotBlank() }?.trim()
      replaceLine(lineStart, newVersion = kotlinVersion)
    }
    checkNotNull(oldKotlinVersion)
    println("Old version: $oldKotlinVersion")

    println("Update artifacts.bzl")
    File("$repoDir/tools/base/bazel/maven/artifacts.bzl")
      .replaceLine(lineEnd = "$oldKotlinVersion\",", newVersion = kotlinVersion)
    println("Update tools/base/build-system/integration-test/BUILD.bazel")
    File("$repoDir/tools/base/build-system/integration-test/BUILD.bazel")
      .replaceLine(lineStart = "LATEST_KOTLIN_VERSION_FOR_SYNC_BENCHMARKS = ", newVersion = kotlinVersion)


    println("Running maven_fetch.sh")
    "$repoDir/tools/base/bazel/maven/maven_fetch.sh".runCommand(repoDir)

    println("Delete all directories named $oldKotlinVersion in prebuilts/tools")
    File("$repoDir/prebuilts/tools/common/m2").walkTopDown().filter{
      it.isDirectory && it.name == oldKotlinVersion
    }.forEach { it.deleteRecursively() }

    val commitMessage = """
    Update comparison benchmark Kotlin version

    to $kotlinVersion

    Bug: N/A
    Test: N/A
  """.trimIndent()


    println("Creating tools/adt/idea change")
    runGitCommit(repoDir, "tools/adt/idea", commitMessage, listOf(AGP_INTEGRATION_TESTS_PATH))
    println("Creating prebuilts/tools change")
    runGitCommit(repoDir, "prebuilts/tools", commitMessage)
    println("Creating tools/base change")
    runGitCommit(repoDir, "tools/base", commitMessage, listOf(
      "tools/base/bazel/maven/artifacts.bzl",
      "tools/base/bazel/maven/BUILD.maven",
      "tools/base/build-system/integration-test/BUILD.bazel"))
  }
}

private fun File.replaceLine(lineStart: String? = null, lineEnd: String? = null, newVersion: String) = apply {
  writeText(readLines().joinToString("\n", postfix = "\n") {
    if (lineStart != null && it.startsWith(lineStart)) {
      "$lineStart\"$newVersion\""
    } else if (lineEnd != null && it.endsWith(lineEnd)) {
      it.replace(lineEnd, "$newVersion\",")
    }
    else
      it
  })
}

private fun String.versionToDist() = "gradle-$this-bin.zip"

private fun runGitCommit(repoDir: String, gitPath: String, message: String, changedFiles: List<String>? = null) {
  val files = changedFiles?.joinToString(" ") { "$repoDir/$it" } ?: "-A" // add all if null
  "git -C $repoDir/$gitPath add $files".runCommand(repoDir)
  runCommand(repoDir,*"git -C $repoDir/$gitPath commit -m".split(" ").toTypedArray() + message)
}

private fun String.runCommand(directory: String) = runCommand(directory, *split(" ").toTypedArray())

private fun runCommand(directory: String, vararg command: String) {
  ProcessBuilder(command.toList())
    .directory(File(directory))
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start().waitFor()

}