/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.SimpleConnectedSocket
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.deployer.AdbInstaller
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.utils.editor.commitToPsi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class PrecompileTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)

    LiveEditApplicationConfiguration.getInstance().leTriggerMode = LiveEditService.Companion.LiveEditTriggerMode.AUTOMATIC
    LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT

    // Project system in the test project is not configured to support this.
    StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR.override(false)
  }

  // Uses the actual test project with "real" Live Edit;
  @Test
  fun realLiveEditWithPrecompile() {
    val packageName = "my.app"
    val device: IDevice = MockitoKt.mock()
    val client: Client = MockitoKt.mock()
    val clientData: ClientData = MockitoKt.mock()

    whenever(client.device).thenReturn(device)
    whenever(client.clientData).thenReturn(clientData)

    whenever(clientData.packageName).thenReturn(packageName)
    whenever(clientData.pid).thenReturn(100)

    whenever(device.clients).thenReturn(arrayOf(client))
    whenever(device.serialNumber).thenReturn("1")
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isOnline).thenReturn(true)

    whenever(device.supportsFeature(IDevice.Feature.REAL_PKG_NAME)).thenReturn(true)

    val socket = FakeSocket()
    whenever(device.rawExec2(Mockito.eq(AdbInstaller.INSTALLER_PATH), Mockito.any())).thenReturn(socket)

    val service = LiveEditService.getInstance(projectRule.project)
    service.getDeployMonitor().notifyAppDeploy(packageName, device, LiveEditApp(emptySet(), 24)) { true }

    val file = projectRule.fixture.configureByText("File.kt", "fun foo() { }")
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      val document = PsiDocumentManager.getInstance(projectRule.project).getDocument(file) ?: fail("Document not found")
      document.replaceString(0, document.textLength, "fun foo() { val x = 1 }")
      document.commitToPsi(projectRule.project)
    }

    if (!socket.latch.await(5000, TimeUnit.SECONDS)) {
      fail("Timed out waiting for Live Edit to complete")
    }

    val irClass = service.getDeployMonitor().irClassCache?.get("FileKt")
    assertNotNull(irClass)
    assertEquals("FileKt", irClass.name)
    assertEquals(1, irClass.methods.size)
    assertNotNull(irClass.methods.firstOrNull { it.name == "foo" && it.desc == "()V" })
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR.clearOverride()
  }

  private class FakeSocket: SimpleConnectedSocket {
    val latch = CountDownLatch(1)
    override fun close() {
    }

    override fun read(dst: ByteBuffer, timeoutMs: Long): Int {
      return dst.capacity()
    }

    override fun write(dst: ByteBuffer, timeoutMs: Long): Int {
      latch.countDown()
      return dst.capacity()
    }

    override fun isOpen(): Boolean {
      return true
    }
  }
}