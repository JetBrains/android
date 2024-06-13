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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.tree.EditorTreeSettings
import com.android.tools.idea.layoutinspector.ui.EditorRenderSettings
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.image.BufferedImage
import java.io.ObjectOutputStream
import java.nio.file.Files

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

@RunsInEdt
class LayoutInspectorFileEditorTest {
  val projectRule = AndroidProjectRule.inMemory()
  val disposableRule = DisposableRule()

  @get:Rule
  val chain =
    RuleChain.outerRule(PortableUiFontRule())
      .around(projectRule)
      .around(disposableRule)
      .around(EdtRule())!!

  @Test
  fun editorShowsVersionError() {
    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()
    val file = createInMemoryFileSystemAndFolder("").resolve("myFile.li")
    val fakeVersion = mock<ProtocolVersion>()
    whenever(fakeVersion.value).thenReturn("99")
    ObjectOutputStream(Files.newOutputStream(file)).use {
      it.writeUTF(LayoutInspectorCaptureOptions(fakeVersion, "myTitle").toString())
    }
    val editor = LayoutInspectorFileEditor(projectRule.project, file)
    Disposer.register(disposableRule.disposable, editor)

    editor.component.apply {
      setSize(400, 100)
      doLayout()
      paint(graphics)
    }
    ImageDiffUtil.assertImageSimilarPerPlatform(
      TestUtils.resolveWorkspacePathUnchecked(TEST_DATA_PATH),
      "snapshotVersionError",
      generatedImage,
      0.01,
    )
  }

  @Test
  fun editorCreatesCorrectSettings() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    AndroidModel.set(facet, TestAndroidModel("com.google.samples.apps.sunflower"))
    val editor =
      LayoutInspectorFileEditor(
        projectRule.project,
        TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/snapshot.li"),
      )
    Disposer.register(disposableRule.disposable, editor)
    val editorComponent = editor.component

    val inspector =
      DataManager.getDataProvider(
          editorComponent.flatten(false).first { it is WorkBench<*> } as WorkBench<*>
        )
        ?.getData(LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector

    val settings = inspector.renderLogic.renderSettings
    assertThat(settings).isInstanceOf(EditorRenderSettings::class.java)

    assertThat(inspector.treeSettings).isInstanceOf(EditorTreeSettings::class.java)
    assertThat(inspector.currentClient.capabilities)
      .containsExactly(Capability.SUPPORTS_SYSTEM_NODES)
    assertThat(inspector.inspectorModel.resourceLookup.hasResolver).isTrue()
  }

  @Test
  fun editorCreatesCorrectSettingsForCompose() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    AndroidModel.set(facet, TestAndroidModel("com.example.mysemantics"))
    val editor =
      LayoutInspectorFileEditor(
        projectRule.project,
        TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/compose-snapshot.li"),
      )
    Disposer.register(disposableRule.disposable, editor)
    val editorComponent = editor.component

    val inspector =
      DataManager.getDataProvider(
          editorComponent.flatten(false).first { it is WorkBench<*> } as WorkBench<*>
        )
        ?.getData(LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector

    val settings = inspector.renderLogic.renderSettings
    assertThat(settings).isInstanceOf(EditorRenderSettings::class.java)

    assertThat(inspector.treeSettings).isInstanceOf(EditorTreeSettings::class.java)
    assertThat(inspector.currentClient.capabilities)
      .containsExactly(
        Capability.SUPPORTS_SYSTEM_NODES,
        Capability.SUPPORTS_COMPOSE,
        Capability.SUPPORTS_SEMANTICS,
      )
    assertThat(inspector.inspectorModel.resourceLookup.hasResolver).isTrue()
  }
}
