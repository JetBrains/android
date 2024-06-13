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
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

class MigrateToResourceNamespacesProcessorTest : AndroidTestCase() {

  private lateinit var libXml: PsiFile

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      true
    )
  }

  override fun setUp() {
    super.setUp()
    replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    myFixture.enableInspections(
      AndroidDomInspection::class.java,
      AndroidUnknownAttributeInspection::class.java,
      AndroidElementNotAllowedInspection::class.java,
      XmlUnusedNamespaceInspection::class.java
    )

    libXml = myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/res/values/lib.xml",
      // language=xml
      """
        <resources>
          <string name="libString">Hello from lib</string>
          <attr name="libAttr" format="string" />
          <attr name="anotherLibAttr" format="string" />

          <style name="LibTheme">
          </style>
        </resources>
      """.trimIndent()
    )

    // This may trigger creation of resource repositories, so let's do last to make local runs less flaky.
    runUndoTransparentWriteAction {
      Manifest.getMainManifest(myFacet)!!.`package`.value = "com.example.app"
      Manifest.getMainManifest(AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!)!!.`package`.value = "com.example.lib"
    }
  }

  // TODO: http://b/316927024
  fun ignoreTestResourceValues() {
    val appXml = myFixture.addFileToProject(
      "/res/values/app.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
          <string name="s1">@string/appString</string>
          <string name="s2">@string/libString</string>
          <attr name="text" format="string" />

          <style name="AppStyle" parent="LibTheme">
            <item name="libAttr">@string/libString</item>
            <item name="text">@string/appString</item>
            <item name="android:text">hello</item>
          </style>

          <style name="Another" parent="@style/LibTheme">
          </style>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            app:libAttr="@string/libString">
          <TextView android:text="@string/appString" app:libAttr="view" />
          <TextView android:text="@string/libString" />
        </LinearLayout>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/res/layout/layout2.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:existing="http://schemas.android.com/apk/res/com.example.lib"
            app:libAttr="layout">
          <TextView android:text="@string/appString" app:libAttr="view" />
          <TextView android:text="@string/libString" />
        </LinearLayout>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/res/layout/layout3.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:lib="http://example.com"
            app:libAttr="layout">
          <TextView android:text="@string/appString" app:libAttr="view" />
          <TextView android:text="@string/libString" />
        </LinearLayout>
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:lib="http://schemas.android.com/apk/res/com.example.lib"
            lib:libAttr="@lib:string/libString">
          <TextView android:text="@string/appString" lib:libAttr="view" />
          <TextView android:text="@lib:string/libString" />
        </LinearLayout>
      """.trimIndent(),
      true
    )

    myFixture.checkResult(
      "/res/layout/layout2.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:existing="http://schemas.android.com/apk/res/com.example.lib"
            existing:libAttr="layout">
          <TextView android:text="@string/appString" existing:libAttr="view" />
          <TextView android:text="@existing:string/libString" />
        </LinearLayout>
      """.trimIndent(),
      true
    )

    myFixture.checkResult(
      "/res/layout/layout3.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:lib="http://example.com"
            xmlns:lib2="http://schemas.android.com/apk/res/com.example.lib"
            lib2:libAttr="layout">
          <TextView android:text="@string/appString" lib2:libAttr="view" />
          <TextView android:text="@lib2:string/libString" />
        </LinearLayout>
      """.trimIndent(),
      true
    )

    FileDocumentManager.getInstance().saveAllDocuments()
    myFixture.configureFromExistingVirtualFile(appXml.virtualFile)

    myFixture.checkResult(
      // language=xml
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib">
          <string name="appString">Hello from app</string>
          <string name="s1">@string/appString</string>
          <string name="s2">@lib:string/libString</string>
          <attr name="text" format="string" />

          <style name="AppStyle" parent="lib:LibTheme">
            <item name="lib:libAttr">@lib:string/libString</item>
            <item name="text">@string/appString</item>
            <item name="android:text">hello</item>
          </style>

          <style name="Another" parent="@lib:style/LibTheme">
          </style>
        </resources>
      """.trimIndent(),
      true
    )

    myFixture.checkHighlighting()
  }

  fun testManifest() {
    runUndoTransparentWriteAction {
      Manifest.getMainManifest(myFacet)!!.application.label.stringValue = "@string/libString"
    }

    refactorAndSync()

    myFixture.checkResult(
      "AndroidManifest.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:lib="http://schemas.android.com/apk/res/com.example.lib"
            package="com.example.app">
            <application android:icon="@drawable/icon" android:label="@lib:string/libString">
            </application>
        </manifest>
      """.trimIndent(),
      true
    )
  }

  fun testCode() {
    myFixture.addFileToProject(
      "/res/values/app.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/com/example/app/MainActivity.java",
      // language=java
      """
        package com.example.app;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getResources().getString(R.string.appString);
                getResources().getString(com.example.app.R.string.appString);

                getResources().getString(R.string.libString);
                getResources().getString(com.example.app.R.string.libString);
                getResources().getString(com.example.lib.R.string.libString);
            }
        }
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "/src/com/example/app/MainActivity.java",
      // language=java
      """
        package com.example.app;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getResources().getString(R.string.appString);
                getResources().getString(com.example.app.R.string.appString);

                getResources().getString(com.example.lib.R.string.libString);
                getResources().getString(com.example.lib.R.string.libString);
                getResources().getString(com.example.lib.R.string.libString);
            }
        }
      """.trimIndent(),
      true
    )
  }

  fun testGradleFiles() {
    // Make sure there's at least on reference to rewrite.
    runUndoTransparentWriteAction {
      Manifest.getMainManifest(myFacet)!!.application.label.stringValue = "@string/libString"
    }

    myFixture.addFileToProject(
      "build.gradle",
      """
        android {
            compileSdkVersion 27

            defaultConfig {
                applicationId "com.example.app"
            }
        }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/build.gradle",
      """
        android {
            compileSdkVersion 27
        }
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "build.gradle",
      """
        android {
            compileSdkVersion 27

            defaultConfig {
                applicationId "com.example.app"
            }
            aaptOptions {
                namespaced true
            }
        }
      """.trimIndent(),
      true
    )

    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/build.gradle",
      """
        android {
            compileSdkVersion 27
            aaptOptions {
                namespaced true
            }
        }
      """.trimIndent(),
      true
    )
  }

  /**
   * Repro case for b/109802379.
   */
  fun testMultipleAttrsInNestedView() {
    myFixture.addFileToProject(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:theme="@style/AppTheme.AppBarOverlay">

                <TextView
                    android:id="@+id/toolbar"
                    android:layout_width="match_parent"
                    android:layout_height="?attr/libAttr"
                    android:background="?attr/anotherLibAttr"
                    app:libAttr="@style/LibStyle" />

            </LinearLayout>

            <TextView
                android:id="@+id/fab"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="bottom|end" />

        </LinearLayout>
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:lib="http://schemas.android.com/apk/res/com.example.lib"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:theme="@style/AppTheme.AppBarOverlay">

                <TextView
                    android:id="@+id/toolbar"
                    android:layout_width="match_parent"
                    android:layout_height="?lib:attr/libAttr"
                    android:background="?lib:attr/anotherLibAttr"
                    lib:libAttr="@style/LibStyle" />

            </LinearLayout>

            <TextView
                android:id="@+id/fab"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="bottom|end" />

        </LinearLayout>
      """.trimIndent(),
      true
    )
  }

  /** Tests that attributes in files other than layouts are also rewritten. */
  fun testOtherAttributes() {
    myFixture.addFileToProject(
      "/res/menu/menu_main.xml",
      // language=xml
      """
        <menu
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto">
            <item
                android:id="@+id/action_settings"
                android:orderInCategory="100"
                android:title="@string/libString"
                app:libAttr="never" />
        </menu>
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "/res/menu/menu_main.xml",
      // language=xml
      """
        <menu
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:lib="http://schemas.android.com/apk/res/com.example.lib">
            <item
                android:id="@+id/action_settings"
                android:orderInCategory="100"
                android:title="@lib:string/libString"
                lib:libAttr="never" />
        </menu>
      """.trimIndent(),
      true
    )
  }

  /**
   * Runs the refactoring and changes the model to enable namespacing, like the sync would do.
   */
  private fun refactorAndSync() {
    val before = libXml.modificationStamp

    MigrateToResourceNamespacesProcessor(myFacet).run()

    assertEquals(before, libXml.modificationStamp)

    enableNamespacing(myFacet, "com.example.app")
    enableNamespacing(AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!, "com.example.lib")
  }
}
