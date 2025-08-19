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
import com.android.testutils.TestUtils
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.image.BufferedImage
import java.io.ObjectOutputStream
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
}
