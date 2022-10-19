/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property.testutil

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.LayoutTestCase.getDesignerPluginHome
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutAttributesModel
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionSelection
import com.android.tools.idea.uibuilder.property.NlPropertiesModelTest
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.surface.AccessoryPanel
import com.android.tools.idea.util.androidFacet
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.truth.Truth
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.text.LineColumn
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.android.ComponentStack
import org.junit.rules.ExternalResource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.times

const val DEFAULT_LAYOUT_FILE = "layout.xml"
const val DEFAULT_SCENE_FILE = "scene.xml"

class MotionAttributeRule(
  private val projectRule: AndroidProjectRule,
  private val motionLayoutFilename: String = DEFAULT_LAYOUT_FILE,
  private val motionSceneFilename: String = DEFAULT_SCENE_FILE
) : ExternalResource() {
  private var componentStack: ComponentStack? = null
  private var nlModel: SyncNlModel? = null
  private var sceneFile: XmlFile? = null
  private var selectionFactory: MotionSelectionFactory? = null
  private var timeline: FakeMotionAccessoryPanel? = null
  private var model: MotionLayoutAttributesModel? = null
  private var fileManager: FileEditorManager? = null
  private var matchCount: Int = 0

  fun selectConstraintSet(id: String) {
    select(selectionFactory!!.createConstraintSet(id))
  }

  fun selectConstraint(setId: String, id: String) {
    select(selectionFactory!!.createConstraint(setId, id))
  }

  fun selectTransition(start: String, end: String) {
    select(selectionFactory!!.createTransition(start, end))
  }

  fun selectKeyFrame(start: String, end: String, keyType: String, framePosition: Int, target: String) {
    select(selectionFactory!!.createKeyFrame(start, end, keyType, framePosition, target))
  }

  fun property(namespace: String, name: String, subTag: String = ""): NlPropertyItem {
    return model!!.allProperties!![subTag]!![namespace, name]
  }

  fun update() {
    selectionFactory = MotionSelectionFactory(nlModel!!, sceneFile!!)
  }

  val properties: Map<String, PropertiesTable<NlPropertyItem>>
    get() = model!!.allProperties!!

  val attributesModel: MotionLayoutAttributesModel
    get() = model!!

  val selection: MotionSelection
    get() = timeline!!.selection

  fun enableFileOpenCaptures() {
    fileManager = Mockito.mock(FileEditorManagerEx::class.java)
    whenever(fileManager!!.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))
    whenever(fileManager!!.selectedEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    whenever(fileManager!!.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    @Suppress("UnstableApiUsage")
    whenever(fileManager!!.openFilesWithRemotes).thenReturn(emptyList())
    whenever(fileManager!!.allEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    componentStack!!.registerServiceInstance(FileEditorManager::class.java, fileManager!!)
  }

  fun checkEditor(fileName: String, lineNumber: Int, text: String) {
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.verify(fileManager!!, times(++matchCount)).openEditor(file.capture(), ArgumentMatchers.eq(true))
    val descriptor = file.value
    val line = findLineAtOffset(descriptor.file, descriptor.offset)
    Truth.assertThat(descriptor.file.name).isEqualTo(fileName)
    Truth.assertThat(line.second).isEqualTo(text)
    Truth.assertThat(line.first.line + 1).isEqualTo(lineNumber)
  }

  fun sceneFileLines(range: IntRange): String {
    val text = sceneFile!!.text
    val start = StringUtil.lineColToOffset(text, range.first - 1, 1)
    val end = StringUtil.lineColToOffset(text, range.last, 1)
    return text.substring(start, end).trimIndent().trimEnd()
  }

  val lastUndoDescription: String?
    get() {
      val manager = UndoManager.getInstance(model!!.project)
      if (!manager.isUndoAvailable(null)) {
        return null
      }
      return manager.getUndoActionNameAndDescription(null).second
    }

  override fun before() {
    componentStack = ComponentStack(projectRule.project)
    projectRule.fixture.testDataPath = getDesignerPluginHome() + "/testData/motion"
    val facet = projectRule.module.androidFacet!!
    projectRule.fixture.copyFileToProject("attrs.xml", "res/values/attrs.xml")
    projectRule.fixture.copyFileToProject("MotionLayout.kt", "src/MotionLayout.kt")
    val layout = projectRule.fixture.copyFileToProject(motionLayoutFilename, "res/layout/$motionLayoutFilename")
    val layoutFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, layout) as XmlFile
    val queue = MergingUpdateQueue("MQ", 100, true, null, projectRule.fixture.projectDisposable)
    queue.isPassThrough = true
    val scene = projectRule.fixture.copyFileToProject(motionSceneFilename, "res/xml/$motionSceneFilename")
    sceneFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, scene) as XmlFile
    timeline = FakeMotionAccessoryPanel()
    runInEdtAndWait {
      nlModel = createNlModel(layoutFile, timeline!!)
      selectionFactory = MotionSelectionFactory(nlModel!!, sceneFile!!)
      model = MotionLayoutAttributesModel(projectRule.fixture.projectDisposable, facet, queue)
      model!!.surface = nlModel!!.surface
    }
  }

  override fun after() {
    componentStack!!.restore()
    componentStack = null
    fileManager = null
    selectionFactory = null
    timeline = null
    model = null
  }

  private fun createNlModel(layout: XmlFile, timeline: AccessoryPanelInterface): SyncNlModel {
    val facet = projectRule.module.androidFacet!!
    val model = NlModelBuilderUtil.model(facet, projectRule.fixture, "layout", "layout.xml", ComponentDescriptorUtil.component(layout)).build()
    val surface = model.surface
    val panel = Mockito.mock(AccessoryPanel::class.java)
    whenever(surface.accessoryPanel).thenReturn(panel)
    whenever(panel.currentPanel).thenReturn(timeline)
    return model
  }

  private fun select(selection: MotionSelection) {
    timeline!!.select(selection)
    runInEdtAndWait { NlPropertiesModelTest.waitUntilLastSelectionUpdateCompleted(model!!) }
  }

  private fun findLineAtOffset(file: VirtualFile, offset: Int): Pair<LineColumn, String> {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return Pair(line, lineText.trim())
  }
}
