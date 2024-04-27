package com.android.tools.idea.execution.common

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentDescriptor
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

class UtilsKtTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun getProcessHandlersForDevices() {
    val settings = RunManager.getInstance(projectRule.project).createConfiguration("app", SimpleConfigurationType::class.java)

    val device1 = mock<IDevice>()
    val device2 = mock<IDevice>()

    // Run on device1
    val processHandler = AndroidProcessHandler("app")
    AndroidSessionInfo.create(processHandler, listOf(device1), "app")
    val descriptor = RunContentDescriptor(null, processHandler, JPanel(), "title", null)

    val executionManager = mock<ExecutionManagerImpl>()
    whenever(executionManager.getRunningDescriptors(any())).thenReturn(listOf(descriptor))

    projectRule.replaceProjectService(ExecutionManager::class.java, executionManager)

    // Different device
    assertThat(settings.getProcessHandlersForDevices(projectRule.project, listOf(device2))).isEmpty()

    // Same device
    assertThat(settings.getProcessHandlersForDevices(projectRule.project, listOf(device1))).isNotEmpty()
  }
}