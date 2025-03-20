/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.refactoring.catalog

import com.android.testutils.TestUtils.getDiff
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openPreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withIntegrationTestEnvironment
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import java.util.regex.Pattern
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class MigrateToCatalogTest {
  @get:Rule var projectRule: IntegrationTestEnvironmentRule = withIntegrationTestEnvironment()

  @Test
  fun testGroovy() {
    check(
      testProject = AndroidCoreTestProject.MIGRATE_TO_VERSION_CATALOG,
      diffs =
        listOf(
          "build.gradle" to
            """
            @@ -n +n
                  }
                  dependencies {
            -         classpath 'com.android.tools.build:gradle:INJECTED_VERSION'
            +         classpath libs.gradle
                  }
              }
            """,
          "app/build.gradle" to
            """
            @@ -n +n
              dependencies {
                  api fileTree(dir: 'libs', include: ['*.jar'])
            -     api 'com.android.support:appcompat-v7:+'
            -     api 'com.android.support:cardview-v7:+'
            -     implementation group: 'com.google.guava', name: 'guava', version: '19.0'
            -     api 'com.android.support.constraint:constraint-layout:1.0.0'
            -     api 'com.android.support.constraint:constraint-layout:1.0.2'
            -     testImplementation 'junit:junit:4.12'
            -     androidTestImplementation 'com.android.support.test:runner:+'
            -     androidTestImplementation 'com.android.support.test.espresso:espresso-core:+'
            +     api libs.appcompat.v7
            +     api libs.cardview.v7
            +     implementation libs.guava
            +     api libs.constraint.layout.v100
            +     api libs.constraint.layout
            +     testImplementation libs.junit
            +     androidTestImplementation libs.runner
            +     androidTestImplementation libs.espresso.core
              }
            """,
        ),
      catalog =
        """
        [versions]
        constraintLayout = "1.0.2"
        constraintLayoutV100 = "1.0.0"
        espressoCore = "3.0.2"
        gradle = "INJECTED"
        guava = "19.0"
        junit = "4.12"
        runner = "1.0.2"
        support = "28.0.0"

        [libraries]
        appcompat-v7 = { group = "com.android.support", name = "appcompat-v7", version.ref = "support" }
        cardview-v7 = { group = "com.android.support", name = "cardview-v7", version.ref = "support" }
        constraint-layout = { group = "com.android.support.constraint", name = "constraint-layout", version.ref = "constraintLayout" }
        constraint-layout-v100 = { group = "com.android.support.constraint", name = "constraint-layout", version.ref = "constraintLayoutV100" }
        espresso-core = { group = "com.android.support.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
        gradle = { group = "com.android.tools.build", name = "gradle", version.ref = "gradle" }
        guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
        junit = { group = "junit", name = "junit", version.ref = "junit" }
        runner = { group = "com.android.support.test", name = "runner", version.ref = "runner" }
        """,
    ) {
      updateVersions = false
      unifyVersions = false
      groupVersionVariables = true
    }
  }

  @Test
  fun testUnifyVariables() {
    // Like testGroovy, but unifies variables: we pick a single version for
    // the constraint layout library. We also turn off version grouping,
    // so we should get individual variables for appcompat and card view
    check(
      testProject = AndroidCoreTestProject.MIGRATE_TO_VERSION_CATALOG,
      diffs =
        listOf(
          "build.gradle" to
            """
            @@ -n +n
                  }
                  dependencies {
            -         classpath 'com.android.tools.build:gradle:INJECTED_VERSION'
            +         classpath libs.gradle
                  }
              }
            """,
          "app/build.gradle" to
            """
            @@ -n +n
              dependencies {
                  api fileTree(dir: 'libs', include: ['*.jar'])
            -     api 'com.android.support:appcompat-v7:+'
            -     api 'com.android.support:cardview-v7:+'
            -     implementation group: 'com.google.guava', name: 'guava', version: '19.0'
            -     api 'com.android.support.constraint:constraint-layout:1.0.0'
            -     api 'com.android.support.constraint:constraint-layout:1.0.2'
            -     testImplementation 'junit:junit:4.12'
            -     androidTestImplementation 'com.android.support.test:runner:+'
            -     androidTestImplementation 'com.android.support.test.espresso:espresso-core:+'
            +     api libs.appcompat.v7
            +     api libs.cardview.v7
            +     implementation libs.guava
            +     api libs.constraint.layout
            +     api libs.constraint.layout
            +     testImplementation libs.junit
            +     androidTestImplementation libs.runner
            +     androidTestImplementation libs.espresso.core
              }
            """,
        ),
      catalog =
        """
        [versions]
        agp = "INJECTED"
        appcompatV7 = "28.0.0"
        cardviewV7 = "28.0.0"
        constraintLayout = "1.0.2"
        espressoCore = "3.0.2"
        guava = "19.0"
        junit = "4.12"
        runner = "1.0.2"

        [libraries]
        appcompat-v7 = { group = "com.android.support", name = "appcompat-v7", version.ref = "appcompatV7" }
        cardview-v7 = { group = "com.android.support", name = "cardview-v7", version.ref = "cardviewV7" }
        constraint-layout = { group = "com.android.support.constraint", name = "constraint-layout", version.ref = "constraintLayout" }
        espresso-core = { group = "com.android.support.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
        gradle = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
        guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
        junit = { group = "junit", name = "junit", version.ref = "junit" }
        runner = { group = "com.android.support.test", name = "runner", version.ref = "runner" }
        """,
    ) {
      updateVersions = false
      unifyVersions = true
      groupVersionVariables = false
    }
  }

  @Test
  fun testKts() {
    check(
      testProject = AndroidCoreTestProject.MIGRATE_TO_VERSION_CATALOG_KTS,
      diffs =
        listOf(
          "build.gradle.kts" to
            """
            @@ -n +n
              }}
                dependencies {
            -     classpath("com.android.tools.build:gradle:INJECTED_VERSION")
            -     classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20-RC2")
            +     classpath(libs.gradle)
            +     classpath(libs.kotlin.gradle.plugin)
                }
              }
            """,
          "app/build.gradle.kts" to
            """
            @@ -n +n

              dependencies {
            -   api("com.android.support:appcompat-v7:+")
            -   api("com.android.support:cardview-v7:+")
            -   api("com.google.guava:guava:19.0")
            -   api("com.android.support.constraint:constraint-layout:1.0.2")
            -   implementation(group = "com.google.guava", name = "guava", version = "19.0")
            -   testImplementation("junit:junit:4.12")
            -   androidTestImplementation("com.android.support.test:runner:+")
            -   androidTestImplementation("com.android.support.test.espresso:espresso-core:+")
            +   api(libs.appcompat.v7)
            +   api(libs.cardview.v7)
            +   api(libs.guava)
            +   api(libs.constraint.layout)
            +   implementation(libs.guava)
            +   testImplementation(libs.junit)
            +   androidTestImplementation(libs.runner)
            +   androidTestImplementation(libs.espresso.core)
              }
            """,
        ),
      catalog =
        """
        [versions]
        constraintLayout = "1.0.2"
        espressoCore = "3.0.2"
        gradle = "INJECTED"
        guava = "19.0"
        junit = "4.12"
        kotlinGradlePlugin = "2.1.20-RC2"
        runner = "1.0.2"
        support = "28.0.0"

        [libraries]
        appcompat-v7 = { group = "com.android.support", name = "appcompat-v7", version.ref = "support" }
        cardview-v7 = { group = "com.android.support", name = "cardview-v7", version.ref = "support" }
        constraint-layout = { group = "com.android.support.constraint", name = "constraint-layout", version.ref = "constraintLayout" }
        espresso-core = { group = "com.android.support.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
        gradle = { group = "com.android.tools.build", name = "gradle", version.ref = "gradle" }
        guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
        junit = { group = "junit", name = "junit", version.ref = "junit" }
        kotlin-gradle-plugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlinGradlePlugin" }
        runner = { group = "com.android.support.test", name = "runner", version.ref = "runner" }
        """,
    ) {
      updateVersions = false
      unifyVersions = true
      groupVersionVariables = true
    }
  }

  @Test
  fun testPlugins() {
    check(
      testProject = AndroidCoreTestProject.MIGRATE_TO_VERSION_CATALOG_PLUGINS,
      diffs =
        listOf(
          "build.gradle" to
            """
            @@ -n +n
              plugins {
            -     id 'com.android.application' version "INJECTED_VERSION" apply false
            -     id 'com.android.library' version "INJECTED_VERSION" apply false
            +     alias libs.plugins.android.application apply false
            +     alias libs.plugins.android.library apply false
                  id 'checkstyle'
              }
            """,
          "app/build.gradle" to
            """
            @@ -n +n
              dependencies {
                  api fileTree(dir: 'libs', include: ['*.jar'])
            -     api 'com.android.support:appcompat-v7:+'
            -     api 'com.android.support:cardview-v7:+'
            -     api 'com.google.guava:guava:19.0'
            -     api 'com.android.support.constraint:constraint-layout:1.0.2'
            -     testImplementation 'junit:junit:4.12'
            -     androidTestImplementation 'com.android.support.test:runner:+'
            -     androidTestImplementation 'com.android.support.test.espresso:espresso-core:+'
            +     api libs.appcompat.v7
            +     api libs.cardview.v7
            +     api libs.guava
            +     api libs.constraint.layout
            +     testImplementation libs.junit
            +     androidTestImplementation libs.runner
            +     androidTestImplementation libs.espresso.core
              }
            """,
        ),
      catalog =
        """
        [versions]
        agp = "INJECTED"
        constraintLayout = "1.0.2"
        espressoCore = "3.0.2"
        guava = "19.0"
        junit = "4.12"
        runner = "1.0.2"
        support = "28.0.0"

        [libraries]
        appcompat-v7 = { group = "com.android.support", name = "appcompat-v7", version.ref = "support" }
        cardview-v7 = { group = "com.android.support", name = "cardview-v7", version.ref = "support" }
        constraint-layout = { group = "com.android.support.constraint", name = "constraint-layout", version.ref = "constraintLayout" }
        espresso-core = { group = "com.android.support.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
        guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
        junit = { group = "junit", name = "junit", version.ref = "junit" }
        runner = { group = "com.android.support.test", name = "runner", version.ref = "runner" }

        [plugins]
        android-application = { id = "com.android.application", version.ref = "agp" }
        android-library = { id = "com.android.library", version.ref = "agp" }
        """
          .trimIndent(),
    ) {
      updateVersions = false
      unifyVersions = true
      groupVersionVariables = true
    }
  }

  @Test
  fun testFiltering() {
    check(
      testProject = AndroidCoreTestProject.MIGRATE_TO_VERSION_CATALOG,
      diffs =
        listOf(
          "build.gradle" to
            """
            @@ -n +n
                  }
                  dependencies {
            -         classpath 'com.android.tools.build:gradle:INJECTED_VERSION'
            +         classpath libs.gradle
                  }
              }
            """,
          "app/build.gradle" to
            """
            @@ -n +n
              dependencies {
                  api fileTree(dir: 'libs', include: ['*.jar'])
            -     api 'com.android.support:appcompat-v7:+'
            +     api libs.appcompat.v7
                  api 'com.android.support:cardview-v7:+'
                  implementation group: 'com.google.guava', name: 'guava', version: '19.0'
            @@ -n +n
                  api 'com.android.support:appcompat-v7:+'
                  api 'com.android.support:cardview-v7:+'
            -     implementation group: 'com.google.guava', name: 'guava', version: '19.0'
            +     implementation libs.guava
                  api 'com.android.support.constraint:constraint-layout:1.0.0'
                  api 'com.android.support.constraint:constraint-layout:1.0.2'
            """,
        ),
      """
      [versions]
      constraintLayout = "1.0.2"
      espressoCore = "3.0.2"
      gradle = "INJECTED"
      guava = "19.0"
      junit = "4.12"
      runner = "1.0.2"
      support = "28.0.0"

      [libraries]
      appcompat-v7 = { group = "com.android.support", name = "appcompat-v7", version.ref = "support" }
      gradle = { group = "com.android.tools.build", name = "gradle", version.ref = "gradle" }
      guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
      """,
    ) {
      updateVersions = false
      unifyVersions = true
      groupVersionVariables = true
      includeFilter = {
        it == "com.android.support:appcompat-v7" ||
          it == "com.google.guava:guava" ||
          it == "com.android.tools.build:gradle"
      }
    }
  }

  // ----------------- Test infrastructure only below -----------------

  private fun check(
    testProject: AndroidCoreTestProject,
    diffs: List<Pair<String, String>>,
    @Language("TOML") catalog: String,
    processorSetup: MigrateToCatalogProcessor.() -> Unit = {},
  ) {
    val preparedProject = projectRule.prepareTestProject(testProject)
    openPreparedTestProject(preparedProject) { project: Project ->

      // File contents before refactoring
      val before = diffs.associate { it.first to project.getTextForFile(it.first) }
      MigrateToCatalogProcessor(project).apply {
        processorSetup()
        run()
      }

      // File contents after refactoring
      val after = diffs.associate { it.first to project.getTextForFile(it.first) }

      val catalogAfter = project.getTextForFile("gradle/libs.versions.toml")
      assertThat(
          catalogAfter
            .trim()
            .replaceRegexGroup("gradle = \"(.+)\"", "INJECTED")
            .replaceRegexGroup("agp = \"(.+)\"", "INJECTED")
        )
        .isEqualTo(catalog.trimIndent().trim())

      for ((file, expected) in diffs) {
        val fileBefore = before[file] ?: error("Missing $file")
        val fileAfter = after[file] ?: error("Missing $file")
        val diff =
          makeUnifiedDiff(fileBefore, fileAfter)
            // Strip out AGP version numbers dynamically injected into the
            // test projects by AndroidGradleTests.internalUpdateToolingVersionsAndPaths
            .replaceRegexGroup(
              """classpath ['"]com.android.tools.build:gradle:(.+)['"]""",
              "INJECTED_VERSION",
            )
            .replaceRegexGroup(
              """classpath\("com.android.tools.build:gradle:(.+)"\)""",
              "INJECTED_VERSION",
            )
            .replaceRegexGroup(
              """id ['"]com\.android\..+['"].*version ['"](.+)['"]""",
              "INJECTED_VERSION",
            )
            .replaceRegexGroup(
              """id\("com\.android\..+['"].*version ['"](.+)"\)""",
              "INJECTED_VERSION",
            )

        assertThat(diff.trim()).isEqualTo(expected.trimIndent().trim())
      }
    }
  }

  private fun String.replaceRegexGroup(@Language("Regexp") regex: String, with: String): String {
    val contents = this
    val pattern = Pattern.compile(regex)
    val matcher = pattern.matcher(contents)
    if (matcher.find() && matcher.group(1) != with) {
      return (contents.substring(0, matcher.start(1)) +
        with +
        contents.substring(matcher.end(1)).replaceRegexGroup(regex, with))
    }
    return contents
  }

  private fun makeUnifiedDiff(oldFile: String, newFile: String): String {
    return getDiff(oldFile, newFile, 2).lines().joinToString("\n") {
      // Drop line numbers from diff section headers since these vary between
      // in-IDE test runs and from bazel (probably because the modified
      // build.gradle files are patched differently in the two environments)
      if (it.startsWith("@@")) {
        "@@ -n +n"
      } else {
        it.trimEnd()
      }
    }
  }
}
