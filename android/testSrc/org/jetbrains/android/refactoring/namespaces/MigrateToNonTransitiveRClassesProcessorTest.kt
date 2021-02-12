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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

class MigrateToNonTransitiveRClassesProcessorTest : AndroidTestCase() {

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

    runUndoTransparentWriteAction {
      Manifest.getMainManifest(myFacet)!!.`package`.value = "com.example.app"
      Manifest.getMainManifest(AndroidFacet.getInstance(libModule)!!)!!.`package`.value = "com.example.lib"
      Manifest.getMainManifest(AndroidFacet.getInstance(sublibModule)!!)!!.`package`.value = "com.example.sublib"
    }
  }

  fun testMiddleModule_Java() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(getAdditionalModuleByName("lib")!!.androidFacet!!).run()

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
  }

  fun testMiddleModule_Kotlin() {
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(getAdditionalModuleByName("lib")!!.androidFacet!!).run()

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
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(myFacet).run()

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
    MigrateToNonTransitiveRClassesProcessor.forSingleModule(myFacet).run()

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

  fun testWholeProject() {
    MigrateToNonTransitiveRClassesProcessor.forEntireProject(project).run()

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

    val properties = VfsUtil.findRelativeFile(project.guessProjectDir(), "gradle.properties")!!
    assertThat(FileDocumentManager.getInstance().getDocument(properties)!!.text).isEqualTo(
      """
        android.nonTransitiveRClass=true
      """.trimIndent()
    )
  }
}
