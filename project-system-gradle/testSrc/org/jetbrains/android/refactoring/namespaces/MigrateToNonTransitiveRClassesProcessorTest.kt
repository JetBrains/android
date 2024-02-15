/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.refactoring.namespaces

import com.android.ide.common.repository.AgpVersion
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.metrics.MetricsTrackerRule
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.ModuleModelBuilder
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.util.androidFacet
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.MIGRATE_TO_NON_TRANSITIVE_R_CLASS
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.SYNC_SKIPPED
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class MigrateToNonTransitiveRClassesProcessorTest {
  private val TestUsageTracker.relevantUsages: List<AndroidStudioEvent>
    get() {
      return usages
        .filter { it.studioEvent.kind == MIGRATE_TO_NON_TRANSITIVE_R_CLASS }
        .map { it.studioEvent }
    }

  private object MigrateToNonTransitiveRClassesTestProject: LightGradleSyncTestProject {
    override val templateProject: TemplateBasedTestProject = TestProject.MIGRATE_TO_NON_TRANSITIVE_R_CLASSES
    override val modelBuilders: List<ModuleModelBuilder> = listOf(
      JavaModuleModelBuilder.rootModuleBuilder.copy(
        groupId = "",
        version = "unspecified",
      ),
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        groupId = "reference",
        version = "unspecified",
        selectedBuildVariant = "debug",
        projectBuilder = AndroidProjectBuilder(
          androidModuleDependencyList = {
            listOf(AndroidModuleDependency(":lib", "debug"))
          },
          namespace = { "com.example.app" }
        ).build(),
      ),
      AndroidModuleModelBuilder(
        gradlePath = ":lib",
        groupId = "reference",
        version = "unspecified",
        selectedBuildVariant = "debug",
        projectBuilder = AndroidProjectBuilder(
          projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
          androidModuleDependencyList = { listOf(AndroidModuleDependency(":sublib", null)) },
          namespace = { "com.example.lib" }
        ).build()
      ),
      AndroidModuleModelBuilder(
        gradlePath = ":sublib",
        groupId = "reference",
        version = "unspecified",
        selectedBuildVariant = "debug",
        projectBuilder = AndroidProjectBuilder(
          projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
          namespace = { "com.example.sublib" }
        ).build()
      ),
    )
  }

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @get:Rule
  val projectRule =
    AndroidProjectRule.testProject(MigrateToNonTransitiveRClassesTestProject).named("migrateToNonTransitiveRClasses")

  @Test
  fun testMiddleModule_Java() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(projectRule.project.findModule("lib.main").androidFacet!!,
                                                            AgpVersion.parse("7.0.0")).run()
    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_app,
                        R.string.another_app_string,
                        R.string.from_lib,
                        R.string.another_lib_string,
                        R.string.from_sublib,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_app_Attr_from_app,
                        R.styleable.styleable_from_lib_Attr_from_lib,
                        R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )

    projectRule.fixture.checkResult(
      "lib/src/main/java/com/example/lib/LibJavaClass.java",
      // language=java
      """
        package com.example.lib;

        public class LibJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_lib,
                        R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == MIGRATE_TO_NON_TRANSITIVE_R_CLASS }
      .map { it.studioEvent }
    assertThat(usages).hasSize(2)

    val findUsagesEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == FIND_USAGES }
    assertThat(findUsagesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(12)

    val executesEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == EXECUTE }
    assertThat(executesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(12)
  }

  @Test
  fun testMiddleModule_Kotlin() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(projectRule.project.findModule("lib.main").androidFacet!!,
                                                            AgpVersion.parse("7.0.0")).run()

    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppKotlinClass.kt",
      // language=kotlin
      """
        package com.example.app

        class AppKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                    R.string.from_app,
                    R.string.another_app_string,
                    R.string.from_lib,
                    R.string.another_lib_string,
                    R.string.from_sublib,
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.sublib.R.string.from_sublib,

                    // Styleable_Attr has more logic than other ResourceTypes
                    R.styleable.styleable_from_app_Attr_from_app,
                    R.styleable.styleable_from_lib_Attr_from_lib,
                    R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent(),
      true
    )

    projectRule.fixture.checkResult(
      "lib/src/main/java/com/example/lib/LibKotlinClass.kt",
      // language=kotlin
      """
        package com.example.lib

        class LibKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                    R.string.from_lib,
                    R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.sublib.R.string.from_sublib,

                    // Styleable_Attr has more logic than other ResourceTypes
                    R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent(),
      true
    )
  }

  @Test
  fun testAppModule_Java() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(projectRule.project.findModule("app.main").androidFacet!!,
                                                            AgpVersion.parse("7.0.0")).run()

    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_app,
                        R.string.another_app_string,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.lib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_app_Attr_from_app,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )

    projectRule.fixture.checkResult(
      "lib/src/main/java/com/example/lib/LibJavaClass.java",
      // language=java
      """
        package com.example.lib;

        public class LibJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_lib,
                        R.string.another_lib_string,
                        R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_lib_Attr_from_lib,
                        R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )
  }

  @Test
  fun testAppModule_Kotlin() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(projectRule.project.findModule("app.main").androidFacet!!,
                                                            AgpVersion.parse("7.0.0")).run()

    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppKotlinClass.kt",
      // language=kotlin
      """
        package com.example.app

        class AppKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                    R.string.from_app,
                    R.string.another_app_string,
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.lib.R.string.from_sublib,
                    com.example.sublib.R.string.from_sublib,

                    // Styleable_Attr has more logic than other ResourceTypes
                    R.styleable.styleable_from_app_Attr_from_app,
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent(),
      true
    )

    projectRule.fixture.checkResult(
      "lib/src/main/java/com/example/lib/LibKotlinClass.kt",
      // language=kotlin
      """
        package com.example.lib

        class LibKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                    R.string.from_lib,
                    R.string.another_lib_string,
                    R.string.from_sublib,
                    com.example.sublib.R.string.from_sublib,

                    // Styleable_Attr has more logic than other ResourceTypes
                    R.styleable.styleable_from_lib_Attr_from_lib,
                    R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent(),
      true
    )
  }

  @Test
  fun testWholeProjectOlderAGP() {
    projectRule.replaceService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("4.2.0")).run()

    val properties = VfsUtil.findRelativeFile(projectRule.project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).contains(
      """
        android.experimental.nonTransitiveAppRClass=true
        android.nonTransitiveRClass=true
      """.trimIndent()
    )

    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_app,
                        R.string.another_app_string,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_app_Attr_from_app,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )
  }

  @Test
  fun testAlphaOrRcVersionOfAgp() {
    projectRule.replaceService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("4.2.0-rc01")).run()
    val properties = VfsUtil.findRelativeFile(projectRule.project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).contains(
      """
        android.experimental.nonTransitiveAppRClass=true
        android.nonTransitiveRClass=true
      """.trimIndent()
    )
  }

  @Test
  fun testAgpNonTransitiveEnabledByDefault() {
    projectRule.replaceService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())
    val gradlePropertiesFile = File(projectRule.project.basePath + "/gradle.properties")
    FileUtils.delete(gradlePropertiesFile)
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("8.0.0-alpha09")).run()
    // MigrateToNonTransitiveRClassesProcessor should not create gradle.properties file for AGP 8.0+ (non-transitive classes enabled by default)
    assertThat(gradlePropertiesFile.exists()).isFalse()
  }

  @Test
  fun testAgpNonTransitiveEnabledByDefaultWithExistingOption() {
    projectRule.replaceService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    projectRule.fixture.addFileToProject("gradle.properties", "android.nonTransitiveRClass=false")

    MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("8.0.0-alpha09")).run()
    val properties = VfsUtil.findRelativeFile(projectRule.project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).isEqualTo("android.nonTransitiveRClass=true")
  }

  @Test
  fun testWholeProjectUsageView() {
    val refactoringProcessor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("7.0.0"))

    // gradle.properties only shows up as a usage when the file exists before the refactoring.
    projectRule.fixture.addFileToProject("gradle.properties", "")

    assertThat(projectRule.fixture.getUsageViewTreeTextRepresentation(refactoringProcessor.findUsages().toList()))
      .isEqualTo("""
        <root> (33)
         References to resources defined in com.example.lib (12)
          Usages (12)
           Resource reference in code (12)
            migrateToNonTransitiveRClasses.app.main (12)
             com.example.app (6)
              AppKotlinClass.kt (3)
               AppKotlinClass (3)
                foo (3)
                 8R.string.from_lib,
                 9R.string.another_lib_string,
                 18R.styleable.styleable_from_lib_Attr_from_lib,
              AppJavaClass (3)
               foo() (3)
                8R.string.from_lib,
                9R.string.another_lib_string,
                18R.styleable.styleable_from_lib_Attr_from_lib,
             com.other.folder (6)
              AppOtherPackageKotlinClass.kt (3)
               AppOtherPackageKotlinClass (3)
                foo (3)
                 8R.string.from_lib,
                 9R.string.another_lib_string,
                 17R.styleable.styleable_from_lib_Attr_from_lib,
              AppOtherPackageJavaClass (3)
               foo() (3)
                8R.string.from_lib,
                9R.string.another_lib_string,
                17R.styleable.styleable_from_lib_Attr_from_lib,
         References to resources defined in com.example.sublib (20)
          Usages (20)
           Resource reference in code (20)
            migrateToNonTransitiveRClasses.app.main (16)
             com.example.app (8)
              AppKotlinClass.kt (4)
               AppKotlinClass (4)
                foo (4)
                 10R.string.from_sublib,
                 13com.example.lib.R.string.from_sublib,
                 19R.styleable.styleable_from_sublib_Attr_from_sublib,
                 21com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
              AppJavaClass (4)
               foo() (4)
                10R.string.from_sublib,
                13com.example.lib.R.string.from_sublib,
                19R.styleable.styleable_from_sublib_Attr_from_sublib,
                21com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
             com.other.folder (8)
              AppOtherPackageKotlinClass.kt (4)
               AppOtherPackageKotlinClass (4)
                foo (4)
                 10R.string.from_sublib,
                 13com.example.lib.R.string.from_sublib,
                 18R.styleable.styleable_from_sublib_Attr_from_sublib,
                 20com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
              AppOtherPackageJavaClass (4)
               foo() (4)
                10R.string.from_sublib,
                13com.example.lib.R.string.from_sublib,
                18R.styleable.styleable_from_sublib_Attr_from_sublib,
                20com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
            migrateToNonTransitiveRClasses.lib.main (4)
             com.example.lib (4)
              LibKotlinClass.kt (2)
               LibKotlinClass (2)
                foo (2)
                 8R.string.from_sublib,
                 13R.styleable.styleable_from_sublib_Attr_from_sublib,
              LibJavaClass (2)
               foo() (2)
                8R.string.from_sublib,
                13R.styleable.styleable_from_sublib_Attr_from_sublib,
         Properties flag to be added: android.nonTransitiveRClass (1)
          Non-code usages (1)
           Gradle properties file (1)
            migrateToNonTransitiveRClasses (1)
              (1)
              gradle.properties (1)
               1

        """.trimIndent())
  }

  @Test
  fun testWholeProject() {
    projectRule.replaceService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("7.0.0")).run()

    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_app,
                        R.string.another_app_string,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_app_Attr_from_app,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )

    // Java files have optimized imports. In this case because it's in a different package, and there are no references to resources in the
    // same module, the import statement has been removed.
    projectRule.fixture.checkResult(
      "app/src/main/java/com/other/folder/AppOtherPackageJavaClass.java",
      // language=java
      """
        package com.other.folder;

        public class AppOtherPackageJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.lib.R.string.from_lib,
                        com.example.lib.R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )

    // Kotlin files do not have optimized imports. An unused R class import is left behind if there are no longer references to current
    // module R class.
    projectRule.fixture.checkResult(
      "app/src/main/java/com/other/folder/AppOtherPackageKotlinClass.kt",
      // language=kotlin
      """
        package com.other.folder

        import com.example.app.R

        class AppOtherPackageKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.sublib.R.string.from_sublib,

                    // Styleable_Attr has more logic than other ResourceTypes
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent(),
      true
    )
    projectRule.fixture.openFileInEditor(
      projectRule.fixture.findFileInTempDir("app/src/main/java/com/other/folder/AppOtherPackageKotlinClass.kt"))
    val highlightInfos = projectRule.fixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfos.first().description).isEqualTo("[UNUSED_VARIABLE] Variable 'ids' is never used")

    projectRule.fixture.checkResult(
      "lib/src/main/java/com/example/lib/LibJavaClass.java",
      // language=java
      """
        package com.example.lib;

        public class LibJavaClass {
            public void foo() {
                int[] ids = new int[] {
                        R.string.from_lib,
                        R.string.another_lib_string,
                        com.example.sublib.R.string.from_sublib,
                        com.example.sublib.R.string.from_sublib,

                        // Styleable_Attr has more logic than other ResourceTypes
                        R.styleable.styleable_from_lib_Attr_from_lib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                        com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent(),
      true
    )

    projectRule.fixture.checkResult(
      "app/src/main/java/com/example/app/AppKotlinClass.kt",
      // language=kotlin
      """
        package com.example.app

        class AppKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                    R.string.from_app,
                    R.string.another_app_string,
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.lib.R.string.from_lib,
                    com.example.lib.R.string.another_lib_string,
                    com.example.sublib.R.string.from_sublib,
                    com.example.sublib.R.string.from_sublib,

                    // Styleable_Attr has more logic than other ResourceTypes
                    R.styleable.styleable_from_app_Attr_from_app,
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                    com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent(),
      true
    )

    val properties = VfsUtil.findRelativeFile(projectRule.project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).contains("android.nonTransitiveRClass=true")

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == MIGRATE_TO_NON_TRANSITIVE_R_CLASS }
      .map { it.studioEvent }
    assertThat(usages).hasSize(3)

    val findUsagesEvent = usageTrackerRule.testTracker.relevantUsages.first {
      it.nonTransitiveRClassMigrationEvent.kind == FIND_USAGES
    }
    assertThat(findUsagesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(32)

    val executesEvent = usageTrackerRule.testTracker.relevantUsages.first {
      it.nonTransitiveRClassMigrationEvent.kind == EXECUTE
    }
    assertThat(executesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(33)

    val syncSkippedEvent = usageTrackerRule.testTracker.relevantUsages.first {
      it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED
    }
    assertThat(syncSkippedEvent.nonTransitiveRClassMigrationEvent.hasUsages()).isFalse()

    val textEditor = TextEditorProvider.getInstance().getTextEditor(projectRule.fixture.editor)
    UndoManagerImpl.ourNeverAskUser = true
    val undoManager = UndoManager.getInstance(projectRule.fixture.project)

    // Undo the migration and assert that sync was triggered again
    undoManager.undo(textEditor)

    val syncSkippedEvents = usageTrackerRule.testTracker.relevantUsages.filter {
      it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED
    }
    assertThat(syncSkippedEvents).hasSize(2)

    // Redo the migration and assert that sync was triggered again
    undoManager.redo(textEditor)

    val syncSkippedEventsAfterRedo = usageTrackerRule.testTracker.relevantUsages.filter {
      it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED
    }
    assertThat(syncSkippedEventsAfterRedo).hasSize(3)
  }

  /**
   * Test for [ResourcePackageGroupingRuleProvider], checks that the relevant UsageGroupingRules are included.
   */
  @Test
  fun testWholeProjectGroupingRules() {
    val refactoringProcessor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(projectRule.project, AgpVersion.parse("7.0.0"))
    // gradle.properties only shows up as a usage when the file exists before the refactoring.
    projectRule.fixture.addFileToProject("gradle.properties", "")

    val usageInfo = refactoringProcessor.findUsages().toList()
    val usageTypeTexts = usageInfo.map { getUsageGroup(it).presentableGroupText }
    assertThat(usageTypeTexts).containsAllOf("References to resources defined in com.example.lib",
                                             "References to resources defined in com.example.sublib",
                                             "Properties flag to be added: android.nonTransitiveRClass")
  }

  private fun getUsageGroup(usageInfo: UsageInfo): UsageGroup {
    val groupingRules = ResourcePackageGroupingRuleProvider().getActiveRules(projectRule.project)
    val groups = groupingRules.map { it.getParentGroupsFor(UsageInfo2UsageAdapter(usageInfo), UsageTarget.EMPTY_ARRAY) }.flatten()
    assertThat(groups).hasSize(1)
    return groups[0]
  }
}
