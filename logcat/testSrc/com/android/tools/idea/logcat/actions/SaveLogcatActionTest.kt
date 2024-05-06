package com.android.tools.idea.logcat.actions

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.FakeProjectApplicationIdsProvider
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.files.LogcatFileIo
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.NotificationRule
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.io.File
import java.nio.file.Path

/** Tests for [SaveLogcatAction] */
class SaveLogcatActionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryDirectoryRule = TemporaryDirectoryRule()
  private val notificationRule = NotificationRule(projectRule)
  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  private val fakeFileChooserFactory = FakeFileChooserFactory()
  private val fakeProjectApplicationIdsProvider by lazy {
    FakeProjectApplicationIdsProvider(project)
  }
  private val device = Device.createPhysical("device", true, "10", 30, "Google", "Pixel")
  private val fakeLogcatPresenter by lazy {
    FakeLogcatPresenter().also { Disposer.register(disposable, it) }
  }

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      disposableRule,
      temporaryDirectoryRule,
      notificationRule,
      ApplicationServiceRule(FileChooserFactory::class.java, fakeFileChooserFactory),
      ProjectServiceRule(projectRule, ProjectApplicationIdsProvider::class.java) {
        fakeProjectApplicationIdsProvider
      }
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
  fun actionPerformed_savesFile() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.logcatFilter = "filter"
    fakeLogcatPresenter.messageBatches.add(
      listOf(logcatMessage(message = "message1"), logcatMessage(message = "message2"))
    )
    fakeProjectApplicationIdsProvider.setApplicationIds("appId1", "appId2")
    val event = createEvent(project, fakeLogcatPresenter)

    action.actionPerformed(event)
    waitForCondition { notificationRule.notifications.isNotEmpty() }

    assertThat(fakeFileChooserFactory.files).hasSize(1)
    assertThat(fakeFileChooserFactory.files[0].name).contains("_Google-Pixel-Android-10_")
    val data = LogcatFileIo().readLogcat(fakeFileChooserFactory.files[0].toPath())
    assertThat(data.logcatMessages)
      .containsExactly(logcatMessage(message = "message1"), logcatMessage(message = "message2"))
      .inOrder()
    assertThat(data.metadata?.device).isEqualTo(device)
    assertThat(data.metadata?.filter).isEqualTo("filter")
    assertThat(data.metadata?.projectApplicationIds).containsExactly("appId1", "appId2")
  }

  @Test
  fun actionPerformed_showsNotification() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.logcatFilter = "filter"
    fakeLogcatPresenter.messageBatches.add(
      listOf(logcatMessage(message = "message1"), logcatMessage(message = "message2"))
    )
    fakeProjectApplicationIdsProvider.setApplicationIds("appId1", "appId2")
    val event = createEvent(project, fakeLogcatPresenter)

    action.actionPerformed(event)
    waitForCondition { notificationRule.notifications.isNotEmpty() }

    assertThat(notificationRule.notifications).hasSize(1)
    val notification = notificationRule.notifications.first()

    assertThat(notification.groupId).isEqualTo("Logcat")
    assertThat(notification.type).isEqualTo(NotificationType.INFORMATION)
    assertThat(notification.content).isEqualTo("Log exported successfully")
    assertThat(notification.actions.map { it.templateText })
      .containsExactly(
        "Open in Editor",
        RevealFileAction.getActionName(),
        "Open in Logcat",
      )
  }

  @Test
  fun actionPerformed_openInEditor() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.logcatFilter = "filter"
    fakeLogcatPresenter.messageBatches.add(
      listOf(logcatMessage(message = "message1"), logcatMessage(message = "message2"))
    )
    fakeProjectApplicationIdsProvider.setApplicationIds("appId1", "appId2")
    val event = createEvent(project, fakeLogcatPresenter)
    action.actionPerformed(event)
    waitForCondition { notificationRule.notifications.isNotEmpty() }
    assertThat(notificationRule.notifications).hasSize(1)
    val openInEditorAction =
      notificationRule.notifications.first().actions.find { it.templateText == "Open in Editor" }
        ?: throw AssertionError("Expected action not found")

    runInEdtAndWait { openInEditorAction.actionPerformed(event) }

    assertThat(FileEditorManager.getInstance(project).openFiles[0].name)
      .isEqualTo(fakeFileChooserFactory.files[0].name)
  }

  @Test
  fun actionPerformed_openInLogcat() {
    val action = SaveLogcatAction()
    fakeLogcatPresenter.device = device
    fakeLogcatPresenter.logcatFilter = "filter"
    fakeLogcatPresenter.messageBatches.add(
      listOf(logcatMessage(message = "message1"), logcatMessage(message = "message2"))
    )
    fakeProjectApplicationIdsProvider.setApplicationIds("appId1", "appId2")
    val event = createEvent(project, fakeLogcatPresenter)
    action.actionPerformed(event)
    waitForCondition { notificationRule.notifications.isNotEmpty() }
    assertThat(notificationRule.notifications).hasSize(1)
    val openInLogcatAction =
      notificationRule.notifications.first().actions.find { it.templateText == "Open in Logcat" }
        ?: throw AssertionError("Expected action not found")
    val mockShowLogcatListener = mock<ShowLogcatListener>()
    project.messageBus.connect().subscribe(ShowLogcatListener.TOPIC, mockShowLogcatListener)

    runInEdtAndWait { openInLogcatAction.actionPerformed(event) }

    verify(mockShowLogcatListener)
      .showLogcatFile(fakeFileChooserFactory.files[0].toPath(), "Google Pixel Android 10")
  }

  private fun createEvent(
    project: Project? = this.project,
    logcatPresenter: FakeLogcatPresenter? = fakeLogcatPresenter,
  ) =
    TestActionEvent.createTestEvent(
      MapDataContext().apply {
        put(CommonDataKeys.PROJECT, project)
        put(LOGCAT_PRESENTER_ACTION, logcatPresenter)
      }
    )

  private inner class FakeFileChooserFactory : FileChooserFactoryImpl() {
    val files = mutableListOf<File>()

    override fun createSaveFileDialog(
      descriptor: FileSaverDescriptor,
      project: Project?
    ): FileSaverDialog {
      return object : FileSaverDialog {
        override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper =
          TODO("Not yet implemented")

        override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper {
          if (baseDir == null) {
            throw AssertionError("baseDir cannot be null")
          }
          if (filename == null) {
            throw AssertionError("filename cannot be null")
          }
          val file = temporaryDirectoryRule.newPath(filename).toFile()
          val virtualFileWrapper = VirtualFileWrapper(file)
          files.add(virtualFileWrapper.file)
          return virtualFileWrapper
        }
      }
    }
  }
}
