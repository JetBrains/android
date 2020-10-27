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
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.testing.ProjectFiles
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.MIGRATE_TO_NON_TRANSITIVE_R_CLASS
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.SYNC_SKIPPED
import com.intellij.codeInspection.unusedImport.UnusedImportInspection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.KotlinUnusedImportInspection

class MigrateToNonTransitiveRClassesProcessorTest : AndroidTestCase() {

  private val myUsageTracker = TestUsageTracker(VirtualTimeScheduler())

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
                  R.string.from_lib,
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                  R.string.from_lib,
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                                                            GradleVersion.tryParse("7.0.0")!!).run()

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                  R.string.from_app,
                  R.string.from_lib,
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                                                            GradleVersion.tryParse("7.0.0")!!).run()

    myFixture.checkResult(
      "src/com/example/app/AppKotlinClass.kt",
      // language=kotlin
      """
        package com.example.app

        class AppKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                  R.string.from_app,
                  R.string.from_lib,
                  R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(myFacet, GradleVersion.tryParse("7.0.0")!!).run()

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                  R.string.from_app,
                  com.example.lib.R.string.from_lib,
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(myFacet, GradleVersion.tryParse("7.0.0")!!).run()

    myFixture.checkResult(
      "src/com/example/app/AppKotlinClass.kt",
      // language=kotlin
      """
        package com.example.app

        class AppKotlinClass {
            fun foo() {
                val ids = intArrayOf(
                  R.string.from_app,
                  com.example.lib.R.string.from_lib,
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, GradleVersion.tryParse("4.2.0")!!).run()

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
                  com.example.lib.R.string.from_lib,
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, GradleVersion.tryParse("4.2.0-rc01")!!).run()
    val properties = VfsUtil.findRelativeFile(project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).isEqualTo(
      """
        android.experimental.nonTransitiveAppRClass=true
        android.nonTransitiveRClass=true
      """.trimIndent()
    )
  }

  fun testWholeProjectUsageView() {
    val refactoringProcessor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(project,
                                                                                        GradleVersion.tryParse("7.0.0")!!)

    // gradle.properties only shows up as a usage when the file exists before the refactoring.
    myFixture.addFileToProject("gradle.properties", "")

    assertThat(myFixture.getUsageViewTreeTextRepresentation(refactoringProcessor.findUsages().toList()))
      .isEqualTo("""
        <root> (29)
         References to resources defined in com.example.lib (8)
          Found usages (8)
           Resource reference in code (8)
            app (8)
             com.example.app (4)
              AppJavaClass (2)
               foo() (2)
                7R.string.from_lib,
                15R.styleable.styleable_from_lib_Attr_from_lib,
              AppKotlinClass.kt (2)
               AppKotlinClass (2)
                foo (2)
                 7R.string.from_lib,
                 15R.styleable.styleable_from_lib_Attr_from_lib,
             com.other.folder (4)
              AppOtherPackageJavaClass (2)
               foo() (2)
                8R.string.from_lib,
                15R.styleable.styleable_from_lib_Attr_from_lib,
              AppOtherPackageKotlinClass.kt (2)
               AppOtherPackageKotlinClass (2)
                foo (2)
                 8R.string.from_lib,
                 15R.styleable.styleable_from_lib_Attr_from_lib,
         References to resources defined in com.example.sublib (20)
          Found usages (20)
           Resource reference in code (20)
            app (16)
             com.example.app (8)
              AppJavaClass (4)
               foo() (4)
                8R.string.from_sublib,
                10com.example.lib.R.string.from_sublib,
                16R.styleable.styleable_from_sublib_Attr_from_sublib,
                18com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
              AppKotlinClass.kt (4)
               AppKotlinClass (4)
                foo (4)
                 8R.string.from_sublib,
                 10com.example.lib.R.string.from_sublib,
                 16R.styleable.styleable_from_sublib_Attr_from_sublib,
                 18com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
             com.other.folder (8)
              AppOtherPackageJavaClass (4)
               foo() (4)
                9R.string.from_sublib,
                11com.example.lib.R.string.from_sublib,
                16R.styleable.styleable_from_sublib_Attr_from_sublib,
                18com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
              AppOtherPackageKotlinClass.kt (4)
               AppOtherPackageKotlinClass (4)
                foo (4)
                 9R.string.from_sublib,
                 11com.example.lib.R.string.from_sublib,
                 16R.styleable.styleable_from_sublib_Attr_from_sublib,
                 18com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
            lib (4)
             com.example.lib (4)
              LibJavaClass (2)
               foo() (2)
                7R.string.from_sublib,
                12R.styleable.styleable_from_sublib_Attr_from_sublib,
              LibKotlinClass.kt (2)
               LibKotlinClass (2)
                foo (2)
                 7R.string.from_sublib,
                 12R.styleable.styleable_from_sublib_Attr_from_sublib,
         Properties flag to be added: android.nonTransitiveRClass (1)
          Non-code usages (1)
           Gradle properties file (1)
            app (1)
              (1)
              gradle.properties (1)
               1

        """.trimIndent())
  }

  fun testWholeProject() {
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, GradleVersion.tryParse("7.0.0")!!).run()

    myFixture.checkResult(
      "src/com/example/app/AppJavaClass.java",
      // language=java
      """
        package com.example.app;

        public class AppJavaClass {
            public void foo() {
                int[] ids = new int[] {
                  R.string.from_app,
                  com.example.lib.R.string.from_lib,
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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
                  com.example.lib.R.string.from_lib,
                  com.example.sublib.R.string.from_sublib,
                  com.example.lib.R.string.from_lib,
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

    val findUsagesEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == FIND_USAGES }
    assertThat(findUsagesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(28)

    val executesEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == EXECUTE }
    assertThat(executesEvent.nonTransitiveRClassMigrationEvent.usages).isEqualTo(28)

    val syncEvent = usages.first { it.nonTransitiveRClassMigrationEvent.kind == SYNC_SKIPPED }
    assertThat(syncEvent.nonTransitiveRClassMigrationEvent.hasUsages()).isFalse()
  }
}
