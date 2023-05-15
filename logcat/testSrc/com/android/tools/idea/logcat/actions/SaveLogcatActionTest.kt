package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.FakeProjectApplicationIdsProvider
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.files.LogcatFileIo
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import com.jetbrains.rd.generator.nova.fail
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path

/**
 * Tests for [SaveLogcatAction]
 */
class SaveLogcatActionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val project get() = projectRule.project
  private val disposable get() = disposableRule.disposable
  private val fakeFileChooserFactory = FakeFileChooserFactory(disposable)
  private val fakeProjectApplicationIdsProvider by lazy { FakeProjectApplicationIdsProvider(project) }
  private val device = Device.createPhysical("device", true, "10", 30, "Google", "Pixel")
  private val fakeLogcatPresenter by lazy {
    FakeLogcatPresenter().also {
      Disposer.register(disposable, it)
    }
  }

  @get:Rule
  val rule = RuleChain(
    projectRule,
    disposableRule,
    ApplicationServiceRule(FileChooserFactory::class.java, fakeFileChooserFactory),
    ProjectServiceRule(projectRule, ProjectApplicationIdsProvider::class.java) { fakeProjectApplicationIdsProvider }
  )

  @Test
  fun update() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.messageBatches.add(listOf(logcatMessage(message = "message")))
    val event = createEvent(project, fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun update_noProject() {
    val action = SaveLogcatAction()
    val event = createEvent(null, fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun update_noLogcatPresenter() {
    val action = SaveLogcatAction()
    val event = createEvent(project, null)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun update_noDevice() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = null
    fakeLogcatPresenter.messageBatches.add(listOf(logcatMessage(message = "message")))
    val event = createEvent(project, fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun update_noMessages() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.messageBatches.clear()
    val event = createEvent(project, fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun actionPerformed() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.logcatFilter = "filter"
    fakeLogcatPresenter.messageBatches.add(listOf(logcatMessage(message = "message1"), logcatMessage(message = "message2")))
    fakeProjectApplicationIdsProvider.setApplicationIds("appId1", "appId2")
    val event = createEvent(project, fakeLogcatPresenter)

    action.actionPerformed(event)
    waitForCondition { FileEditorManager.getInstance(project).hasOpenFiles() }

    assertThat(fakeFileChooserFactory.files).hasSize(1)
    assertThat(fakeFileChooserFactory.files[0].name).endsWith("Google-Pixel-Android-10")
    assertThat(FileEditorManager.getInstance(project).openFiles[0].name).isEqualTo(fakeFileChooserFactory.files[0].name)
    val data = LogcatFileIo.readLogcat(fakeFileChooserFactory.files[0].toPath())
    assertThat(data.logcatMessages).containsExactly(
      logcatMessage(message = "message1"),
      logcatMessage(message = "message2")
    ).inOrder()
    assertThat(data.metadata?.device).isEqualTo(device)
    assertThat(data.metadata?.filter).isEqualTo("filter")
    assertThat(data.metadata?.projectApplicationIds).containsExactly("appId1", "appId2")
  }

  private fun createEvent(
    project: Project? = this.project,
    logcatPresenter: FakeLogcatPresenter? = fakeLogcatPresenter,
  ) =
    TestActionEvent.createTestEvent(MapDataContext().apply {
      put(CommonDataKeys.PROJECT, project)
      put(LOGCAT_PRESENTER_ACTION, logcatPresenter)
    })

  private class FakeFileChooserFactory(disposable: Disposable) : FileChooserFactoryImpl() {
    val files = mutableListOf<File>()

    init {
      Disposer.register(disposable) { files.forEach { it.deleteOnExit() } }
    }

    override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
      return object : FileSaverDialog {
        override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper = TODO("Not yet implemented")

        override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper {
          if (baseDir == null) {
            fail("baseDir cannot be null")
          }
          if (filename == null) {
            fail("filename cannot be null")
          }
          val file = FileUtil.createTempFile("", filename, true)
          val virtualFileWrapper = VirtualFileWrapper(file)
          files.add(virtualFileWrapper.file)
          return virtualFileWrapper
        }
      }
    }
  }
}
