/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.editors.manifest

import com.android.manifmerger.ManifestModel
import com.android.manifmerger.XmlNode
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.util.androidFacet
import com.android.utils.FileUtils.toSystemIndependentPath
import com.android.utils.HtmlBuilder
import com.android.utils.forEach
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.w3c.dom.Element
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class ManifestPanelContentTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  @Test
  fun testContent() {
    val projectRoot = prepareGradleProject(TestProjectPaths.NAVIGATION_EDITOR_INCLUDE_FROM_LIB, "project")
    openPreparedProject("project") { project ->
      val appModule = project.gradleModule(":app")?.getMainModule() ?: error("Cannot find :app module")
      val appModuleFacet = appModule.androidFacet ?: error("Cannot find the facet for :app")

      val mergedManifest = MergedManifestManager.getMergedManifestSupplier(appModule).get().get(2, TimeUnit.SECONDS)

      val panel = ManifestPanel(appModuleFacet, projectRule.testRootDisposable)
      panel.showManifest(mergedManifest, appModuleFacet.sourceProviders.mainManifestFile!!, false)

      val reportBuilder = HtmlBuilder()
      mergedManifest.document?.getElementsByTagName("*")?.forEach { node ->
        if (node is Element) {
          val key = XmlNode.NodeKey.fromXml(node, ManifestModel())
          reportBuilder.newline()
          reportBuilder.addBold(key.toString())
          reportBuilder.newline()
          panel.prepareSelectedNodeReport(node, reportBuilder)
        }
      }

      ProjectDumper().nest(projectRoot, "~") {
        assertThat(normalizeReportForTests(reportBuilder))
          .isEqualTo("""

<B>manifest</B>

Added from the <a href='<~>/app/src/main/AndroidManifest.xml:1:0'>navgraph.app main</a> manifest (this file), line 1

Merged from the <a href='<~>/lib/src/main/AndroidManifest.xml'>navgraph.lib</a> manifest

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-ui-2.3.5/AndroidManifest.xml:16:0'>AndroidManifest.xml</a> navigation file, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/material-1.0.0/AndroidManifest.xml:16:0'>material:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/appcompat-1.3.0/AndroidManifest.xml:16:0'>appcompat:1.3.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/legacy-support-v4-1.0.0/AndroidManifest.xml:16:0'>legacy-support-v4:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-fragment-2.3.5/AndroidManifest.xml:16:0'>AndroidManifest.xml</a> navigation file, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/fragment-1.3.4/AndroidManifest.xml:16:0'>fragment:1.3.4</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-runtime-2.3.5/AndroidManifest.xml:16:0'>AndroidManifest.xml</a> navigation file, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/activity-1.2.3/AndroidManifest.xml:16:0'>activity:1.2.3</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/appcompat-resources-1.3.0/AndroidManifest.xml:16:0'>appcompat-resources:1.3.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/recyclerview-1.0.0/AndroidManifest.xml:16:0'>recyclerview:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/legacy-support-core-ui-1.0.0/AndroidManifest.xml:16:0'>legacy-support-core-ui:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/drawerlayout-1.1.1/AndroidManifest.xml:16:0'>drawerlayout:1.1.1</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/viewpager-1.0.0/AndroidManifest.xml:16:0'>viewpager:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/coordinatorlayout-1.0.0/AndroidManifest.xml:16:0'>coordinatorlayout:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/slidingpanelayout-1.0.0/AndroidManifest.xml:16:0'>slidingpanelayout:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/customview-1.1.0/AndroidManifest.xml:16:0'>customview:1.1.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/media-1.0.0/AndroidManifest.xml:16:0'>media:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/legacy-support-core-utils-1.0.0/AndroidManifest.xml:16:0'>legacy-support-core-utils:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/loader-1.0.0/AndroidManifest.xml:16:0'>loader:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/vectordrawable-animated-1.1.0/AndroidManifest.xml:16:0'>vectordrawable-animated:1.1.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/vectordrawable-1.1.0/AndroidManifest.xml:16:0'>vectordrawable:1.1.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/transition-1.3.0/AndroidManifest.xml:16:0'>transition:1.3.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/swiperefreshlayout-1.0.0/AndroidManifest.xml:16:0'>swiperefreshlayout:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/asynclayoutinflater-1.0.0/AndroidManifest.xml:16:0'>asynclayoutinflater:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/core-1.5.0/AndroidManifest.xml:16:0'>core:1.5.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/cursoradapter-1.0.0/AndroidManifest.xml:16:0'>cursoradapter:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-viewmodel-savedstate-2.3.1/AndroidManifest.xml:16:0'>lifecycle-viewmodel-savedstate:2.3.1</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/savedstate-1.1.0/AndroidManifest.xml:16:0'>savedstate:1.1.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-runtime-2.3.1/AndroidManifest.xml:16:0'>lifecycle-runtime:2.3.1</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/versionedparcelable-1.1.1/AndroidManifest.xml:16:0'>versionedparcelable:1.1.1</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-viewmodel-2.3.1/AndroidManifest.xml:16:0'>lifecycle-viewmodel:2.3.1</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-common-2.3.5/AndroidManifest.xml:16:0'>AndroidManifest.xml</a> navigation file, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/cardview-1.0.0/AndroidManifest.xml:16:0'>cardview:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/documentfile-1.0.0/AndroidManifest.xml:16:0'>documentfile:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/localbroadcastmanager-1.0.0/AndroidManifest.xml:16:0'>localbroadcastmanager:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/print-1.0.0/AndroidManifest.xml:16:0'>print:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/interpolator-1.0.0/AndroidManifest.xml:16:0'>interpolator:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-livedata-2.0.0/AndroidManifest.xml:16:0'>lifecycle-livedata:2.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-livedata-core-2.3.1/AndroidManifest.xml:16:0'>lifecycle-livedata-core:2.3.1</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/core-runtime-2.1.0/AndroidManifest.xml:16:0'>core-runtime:2.1.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/annotation-experimental-1.0.0/AndroidManifest.xml:16:0'>annotation-experimental:1.0.0</a> manifest, line 16

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/constraintlayout-1.1.3/AndroidManifest.xml:1:0'>constraintlayout:1.1.3</a> manifest, line 1

Value provided by Gradle



<B>uses-sdk</B>

Value provided by Gradle

Merged from the <a href='<~>/lib/src/main/AndroidManifest.xml:1:145'>navgraph.lib</a> manifest, line 1

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-ui-2.3.5/AndroidManifest.xml:19:4'>AndroidManifest.xml</a> navigation file, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/material-1.0.0/AndroidManifest.xml:19:4'>material:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/appcompat-1.3.0/AndroidManifest.xml:19:4'>appcompat:1.3.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/legacy-support-v4-1.0.0/AndroidManifest.xml:19:4'>legacy-support-v4:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-fragment-2.3.5/AndroidManifest.xml:19:4'>AndroidManifest.xml</a> navigation file, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/fragment-1.3.4/AndroidManifest.xml:19:4'>fragment:1.3.4</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-runtime-2.3.5/AndroidManifest.xml:19:4'>AndroidManifest.xml</a> navigation file, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/activity-1.2.3/AndroidManifest.xml:19:4'>activity:1.2.3</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/appcompat-resources-1.3.0/AndroidManifest.xml:19:4'>appcompat-resources:1.3.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/recyclerview-1.0.0/AndroidManifest.xml:19:4'>recyclerview:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/legacy-support-core-ui-1.0.0/AndroidManifest.xml:19:4'>legacy-support-core-ui:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/drawerlayout-1.1.1/AndroidManifest.xml:19:4'>drawerlayout:1.1.1</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/viewpager-1.0.0/AndroidManifest.xml:19:4'>viewpager:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/coordinatorlayout-1.0.0/AndroidManifest.xml:19:4'>coordinatorlayout:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/slidingpanelayout-1.0.0/AndroidManifest.xml:19:4'>slidingpanelayout:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/customview-1.1.0/AndroidManifest.xml:19:4'>customview:1.1.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/media-1.0.0/AndroidManifest.xml:19:4'>media:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/legacy-support-core-utils-1.0.0/AndroidManifest.xml:19:4'>legacy-support-core-utils:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/loader-1.0.0/AndroidManifest.xml:19:4'>loader:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/vectordrawable-animated-1.1.0/AndroidManifest.xml:19:4'>vectordrawable-animated:1.1.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/vectordrawable-1.1.0/AndroidManifest.xml:19:4'>vectordrawable:1.1.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/transition-1.3.0/AndroidManifest.xml:19:4'>transition:1.3.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/swiperefreshlayout-1.0.0/AndroidManifest.xml:19:4'>swiperefreshlayout:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/asynclayoutinflater-1.0.0/AndroidManifest.xml:19:4'>asynclayoutinflater:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/core-1.5.0/AndroidManifest.xml:19:4'>core:1.5.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/cursoradapter-1.0.0/AndroidManifest.xml:19:4'>cursoradapter:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-viewmodel-savedstate-2.3.1/AndroidManifest.xml:19:4'>lifecycle-viewmodel-savedstate:2.3.1</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/savedstate-1.1.0/AndroidManifest.xml:19:4'>savedstate:1.1.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-runtime-2.3.1/AndroidManifest.xml:19:4'>lifecycle-runtime:2.3.1</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/versionedparcelable-1.1.1/AndroidManifest.xml:19:4'>versionedparcelable:1.1.1</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-viewmodel-2.3.1/AndroidManifest.xml:19:4'>lifecycle-viewmodel:2.3.1</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/navigation-common-2.3.5/AndroidManifest.xml:19:4'>AndroidManifest.xml</a> navigation file, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/cardview-1.0.0/AndroidManifest.xml:19:4'>cardview:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/documentfile-1.0.0/AndroidManifest.xml:19:4'>documentfile:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/localbroadcastmanager-1.0.0/AndroidManifest.xml:19:4'>localbroadcastmanager:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/print-1.0.0/AndroidManifest.xml:19:4'>print:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/interpolator-1.0.0/AndroidManifest.xml:19:4'>interpolator:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-livedata-2.0.0/AndroidManifest.xml:19:4'>lifecycle-livedata:2.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/lifecycle-livedata-core-2.3.1/AndroidManifest.xml:19:4'>lifecycle-livedata-core:2.3.1</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/core-runtime-2.1.0/AndroidManifest.xml:19:4'>core-runtime:2.1.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/annotation-experimental-1.0.0/AndroidManifest.xml:19:4'>annotation-experimental:1.0.0</a> manifest, line 19

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/constraintlayout-1.1.3/AndroidManifest.xml:4:4'>constraintlayout:1.1.3</a> manifest, line 4

Value provided by Gradle



<B>application</B>

Added from the <a href='<~>/app/src/main/AndroidManifest.xml:4:4'>navgraph.app main</a> manifest (this file), line 4

Merged from the <a href='<~>/lib/src/main/AndroidManifest.xml:3:0'>navgraph.lib</a> manifest, line 3

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/material-1.0.0/AndroidManifest.xml:21:4'>material:1.0.0</a> manifest, line 21

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/core-1.5.0/AndroidManifest.xml:23:4'>core:1.5.0</a> manifest, line 23

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/versionedparcelable-1.1.1/AndroidManifest.xml:23:4'>versionedparcelable:1.1.1</a> manifest, line 23

Merged from the <a href='<GRADLE>/caches/transforms-3/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/transformed/constraintlayout-1.1.3/AndroidManifest.xml:8:4'>constraintlayout:1.1.3</a> manifest, line 8



<B>activity#com.example.navgraph.MainActivity</B>

Added from the <a href='<~>/app/src/main/AndroidManifest.xml:9:8'>navgraph.app main</a> manifest (this file), line 9



<B>intent-filter#action:name:android.intent.action.MAIN+category:name:android.intent.category.LAUNCHER</B>

Added from the <a href='<~>/app/src/main/AndroidManifest.xml:11:12'>navgraph.app main</a> manifest (this file), line 11



<B>action#android.intent.action.MAIN</B>

Added from the <a href='<~>/app/src/main/AndroidManifest.xml:11:12'>navgraph.app main</a> manifest (this file), line 11



<B>category#android.intent.category.LAUNCHER</B>

Added from the <a href='<~>/app/src/main/AndroidManifest.xml:11:12'>navgraph.app main</a> manifest (this file), line 11



<B>intent-filter#action:name:android.intent.action.VIEW+category:name:android.intent.category.BROWSABLE+category:name:android.intent.category.DEFAULT+data:host:www.google.com+data:path:/+data:scheme:https</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>action#android.intent.action.VIEW</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>category#android.intent.category.DEFAULT</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>category#android.intent.category.BROWSABLE</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>data</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>data</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>data</B>

Added from the <a href='<~>/lib/src/main/res/navigation/lib_nav.xml:21:8'>lib_nav.xml</a> navigation file, line 21



<B>meta-data#libMetaData</B>

Added from the <a href='<~>/lib/src/main/AndroidManifest.xml:2:2'>navgraph.lib</a> manifest, line 2

          """.trimIndent())
      }
    }
  }

  private fun ProjectDumper.normalizeReportForTests(reportBuilder: HtmlBuilder) = reportBuilder.html
    .replace("<BR/><U><B>Merging Log</B></U>", "")
    .replace("<BR/>", "\n\n")
    .replace(Regex("<a href=\"file:(.*)\">")) {
      val fileAndPosition = it.groupValues[1]
      val (file, suffix) = splitFileAndSuffixPosition(fileAndPosition)
      "<a href='${toSystemIndependentPath(File(file).absolutePath).toPrintablePath()}$suffix'>"
    }
    .trimIndent()

  private fun splitFileAndSuffixPosition(fileAndPosition: String): Pair<String, String> {
    var suffixPosition = fileAndPosition.length
    var columns = 0
    while (suffixPosition > 0 && columns <= 2 && fileAndPosition[suffixPosition - 1].let { it.isDigit() || it == ':' }) {
      suffixPosition--
      if (fileAndPosition[suffixPosition] == ':') columns++
    }
    if (columns < 2) suffixPosition = fileAndPosition.length
    return fileAndPosition.substring(0, suffixPosition) to fileAndPosition.substring(suffixPosition, fileAndPosition.length)
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData"
  override fun getAdditionalRepos(): Collection<File> = listOf()
}