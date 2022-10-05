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

import com.android.AndroidProjectTypes
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.testing.ProjectFiles
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.MIGRATE_TO_NON_TRANSITIVE_R_CLASS
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.SYNC_SKIPPED
import com.intellij.codeInspection.unusedImport.UnusedImportInspection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.KotlinUnusedImportInspection

class MigrateToNonTransitiveRClassesProcessorTest : AndroidTestCase() {

  private val myUsageTracker = TestUsageTracker(VirtualTimeScheduler())
  private val TestUsageTracker.relevantUsages: List<AndroidStudioEvent>
    get() {
      return usages
        .filter { it.studioEvent.kind == MIGRATE_TO_NON_TRANSITIVE_R_CLASS }
        .map { it.studioEvent }
    }

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      false
    )

    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "sublib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      false
    )
  }

  override fun setUp() {
    super.setUp()

    UsageTracker.setWriterForTest(myUsageTracker)

    replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    val appModule = myFixture.module
    val libModule = getAdditionalModuleByName("lib")!!
    val sublibModule = getAdditionalModuleByName("sublib")!!
    ModuleRootModificationUtil.addDependency(appModule, libModule)
    ModuleRootModificationUtil.addDependency(libModule, sublibModule)

    myFixture.addFileToProject(
      "${getAdditionalModulePath("sublib")}/res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="from_sublib">From sublib</string>
          <declare-styleable name="styleable_from_sublib">
            <attr name="Attr_from_sublib" format="string"/>
          </declare-styleable>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="from_lib">From lib</string>
          <string name="another.lib.string">From lib</string>
          <declare-styleable name="styleable_from_lib">
            <attr name="Attr_from_lib" format="string"/>
          </declare-styleable>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="from_app">From app</string>
          <string name="another.app.string">From app</string>
          <declare-styleable name="styleable_from_app">
            <attr name="Attr_from_app" format="string"/>
          </declare-styleable>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "src/com/example/app/AppJavaClass.java",
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
                  com.example.lib.R.string.from_sublib,
                  com.example.sublib.R.string.from_sublib,

                  // Styleable_Attr has more logic than other ResourceTypes
                  R.styleable.styleable_from_app_Attr_from_app,
                  R.styleable.styleable_from_lib_Attr_from_lib,
                  R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                  com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "src/com/example/app/AppKotlinClass.kt",
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
                  com.example.lib.R.string.from_sublib,
                  com.example.sublib.R.string.from_sublib,
                  
                  // Styleable_Attr has more logic than other ResourceTypes
                  R.styleable.styleable_from_app_Attr_from_app,
                  R.styleable.styleable_from_lib_Attr_from_lib,
                  R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                  com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent()
    )

    myFixture.enableInspections(KotlinUnusedImportInspection())
    myFixture.enableInspections(UnusedImportInspection())

    myFixture.addFileToProject(
      "src/com/other/folder/AppOtherPackageJavaClass.java",
      // language=java
      """
        package com.other.folder;

        import com.example.app.R;

        public class AppOtherPackageJavaClass {
            public void foo() {
                int[] ids = new int[] {
                  R.string.from_lib,
                  R.string.another_lib_string,
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
                  com.example.lib.R.string.another_lib_string,
                  com.example.lib.R.string.from_sublib,
                  com.example.sublib.R.string.from_sublib,

                  // Styleable_Attr has more logic than other ResourceTypes
                  R.styleable.styleable_from_lib_Attr_from_lib,
                  R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                  com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                };
            }
        }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "src/com/other/folder/AppOtherPackageKotlinClass.kt",
      // language=kotlin
      """
        package com.other.folder

        import com.example.app.R

        class AppOtherPackageKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                  R.string.from_lib,
                  R.string.another_lib_string,
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
                  com.example.lib.R.string.another_lib_string,
                  com.example.lib.R.string.from_sublib,
                  com.example.sublib.R.string.from_sublib,

                  // Styleable_Attr has more logic than other ResourceTypes
                  R.styleable.styleable_from_lib_Attr_from_lib,
                  R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                  com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                  com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
                )
            }
        }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibJavaClass.java",
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
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibKotlinClass.kt",
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
      """.trimIndent()
    )

    // A settings.gradle file is needed to trick the IDE into thinking this is a project built using gradle, necessary for removing
    // generated build files from search scope.
    ProjectFiles.createFileInProjectRoot(project, "settings.gradle")

    myFixture.addFileToProject(
      "build/generated/source/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                  R.string.from_app,
                  R.string.from_lib,
                  R.string.from_sublib
                };
            }
        }
      """.trimIndent()
    )


    runUndoTransparentWriteAction {
      Manifest.getMainManifest(myFacet)!!.`package`.value = "com.example.app"
      Manifest.getMainManifest(AndroidFacet.getInstance(libModule)!!)!!.`package`.value = "com.example.lib"
      Manifest.getMainManifest(AndroidFacet.getInstance(sublibModule)!!)!!.`package`.value = "com.example.sublib"
    }
  }

  override fun tearDown() {
    super.tearDown()

    myUsageTracker.close()
    cleanAfterTesting()
  }

  fun testMiddleModule_Java() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(getAdditionalModuleByName("lib")!!.androidFacet!!,
                                                            AgpVersion.parse("7.0.0")).run()

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
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

    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibJavaClass.java",
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

    val usages = myUsageTracker.usages
      .filter { it.studioEvent.kind == MIGRATE_TO_NON_TRANSITIVE_R_CLASS }
      .map { it.studioEvent }
    assertThat(usages).hasSize(2)

    val findUsagesEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == FIND_USAGES }
    assertThat(findUsagesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(12)

    val executesEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == EXECUTE }
    assertThat(executesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(12)
  }

  fun testMiddleModule_Kotlin() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(getAdditionalModuleByName("lib")!!.androidFacet!!,
                                                            AgpVersion.parse("7.0.0")).run()

    myFixture.checkResult(
      "src/com/example/app/AppKotlinClass.kt",
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

    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibKotlinClass.kt",
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

  fun testAppModule_Java() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(myFacet, AgpVersion.parse("7.0.0")).run()

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
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

    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibJavaClass.java",
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

  fun testAppModule_Kotlin() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(myFacet, AgpVersion.parse("7.0.0")).run()

    myFixture.checkResult(
      "src/com/example/app/AppKotlinClass.kt",
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

    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibKotlinClass.kt",
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

  fun testWholeProjectOlderAGP() {
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, AgpVersion.parse("4.2.0")).run()

    val properties = VfsUtil.findRelativeFile(project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).isEqualTo(
      """
        android.experimental.nonTransitiveAppRClass=true
        android.nonTransitiveRClass=true
      """.trimIndent()
    )

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
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

  fun testAlphaOrRcVersionOfAgp() {
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, AgpVersion.parse("4.2.0-rc01")).run()
    val properties = VfsUtil.findRelativeFile(project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).isEqualTo(
      """
        android.experimental.nonTransitiveAppRClass=true
        android.nonTransitiveRClass=true
      """.trimIndent()
    )
  }

  fun testWholeProjectUsageView() {
    val refactoringProcessor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, AgpVersion.parse("7.0.0"))

    // gradle.properties only shows up as a usage when the file exists before the refactoring.
    myFixture.addFileToProject("gradle.properties", "")

    assertThat(myFixture.getUsageViewTreeTextRepresentation(refactoringProcessor.findUsages().toList()))
      .isEqualTo("""
        <root> (33)
         References to resources defined in com.example.lib (12)
          Usages in (12)
           Resource reference in code (12)
            app (12)
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
          Usages in (20)
           Resource reference in code (20)
            app (16)
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
            lib (4)
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
          Non-code usages in (1)
           Gradle properties file (1)
            app (1)
              (1)
              gradle.properties (1)
               1

        """.trimIndent())
  }

  fun testWholeProject() {
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, AgpVersion.parse("7.0.0")).run()

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
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
    myFixture.checkResult(
      "src/com/other/folder/AppOtherPackageJavaClass.java",
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


    // Kotlin files do not have optimized imports. A unused R class import is left behind if there are no longer references to current
    // module R class.
    myFixture.checkResult(
      "src/com/other/folder/AppOtherPackageKotlinClass.kt",
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
    myFixture.openFileInEditor(myFixture.findFileInTempDir("src/com/other/folder/AppOtherPackageKotlinClass.kt"))
    val highlightInfos = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfos.first().description).isEqualTo(KotlinBundle.message("unused.import.directive"))


    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/src/com/example/lib/LibJavaClass.java",
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

    myFixture.checkResult(
      "src/com/example/app/AppKotlinClass.kt",
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

    myFixture.checkResult(
      "build/generated/source/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                  R.string.from_app,
                  R.string.from_lib,
                  R.string.from_sublib
                };
            }
        }
      """.trimIndent(),
      true
    )

    val properties = VfsUtil.findRelativeFile(project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).isEqualTo(
      """
        android.nonTransitiveRClass=true
      """.trimIndent()
    )

    val usages = myUsageTracker.usages
      .filter { it.studioEvent.kind == MIGRATE_TO_NON_TRANSITIVE_R_CLASS }
      .map { it.studioEvent }
    assertThat(usages).hasSize(3)

    val findUsagesEvent = myUsageTracker.relevantUsages.first { it.nonTransitiveRClassMigrationEvent.kind == FIND_USAGES }
    assertThat(findUsagesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(32)

    val executesEvent = myUsageTracker.relevantUsages.first { it.nonTransitiveRClassMigrationEvent.kind == EXECUTE }
    assertThat(executesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(32)

    val syncSkippedEvent = myUsageTracker.relevantUsages.first { it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED }
    assertThat(syncSkippedEvent.nonTransitiveRClassMigrationEvent.hasUsages()).isFalse()

    val textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    UndoManagerImpl.ourNeverAskUser = true
    val undoManager = UndoManager.getInstance(myFixture.project)

    // Undo the migration and assert that sync was triggered again
    undoManager.undo(textEditor)

    val syncSkippedEvents = myUsageTracker.relevantUsages.filter { it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED }
    assertThat(syncSkippedEvents).hasSize(2)

    // Redo the migration and assert that sync was triggered again
    undoManager.redo(textEditor)

    val syncSkippedEventsAfterRedo = myUsageTracker.relevantUsages.filter { it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED }
    assertThat(syncSkippedEventsAfterRedo).hasSize(3)
  }

  /**
   * Test for [ResourcePackageGroupingRuleProvider], checks that the relevant UsageGroupingRules are included.
   */
  fun testWholeProjectGroupingRules() {
    val refactoringProcessor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, AgpVersion.parse("7.0.0"))
    // gradle.properties only shows up as a usage when the file exists before the refactoring.
    myFixture.addFileToProject("gradle.properties", "")

    val usageInfo = refactoringProcessor.findUsages().toList()
    val usageTypeTexts = usageInfo.map { getUsageGroup(it).presentableGroupText }
    assertThat(usageTypeTexts).containsAllOf("References to resources defined in com.example.lib",
                                             "References to resources defined in com.example.sublib",
                                             "Properties flag to be added: android.nonTransitiveRClass")
  }

  private fun getUsageGroup(usageInfo: UsageInfo): UsageGroup {
    val groupingRules = ResourcePackageGroupingRuleProvider().getActiveRules(myFixture.project)
    val groups = groupingRules.map { it.getParentGroupsFor(UsageInfo2UsageAdapter(usageInfo), UsageTarget.EMPTY_ARRAY) }.flatten()
    assertThat(groups).hasSize(1)
    return groups[0]
  }
}
