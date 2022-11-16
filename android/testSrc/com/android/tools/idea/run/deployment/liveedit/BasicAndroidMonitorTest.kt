/*
* Copyright (C) 2022 The Android Open Source Project
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
import com.android.ddmlib.internal.ClientImpl
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.idea.editors.literals.EditEvent
import com.android.tools.idea.editors.literals.EditState
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.util.ThreeState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`

@RunWith(JUnit4::class)
class BasicAndroidMonitorTest {

  private lateinit var project: Project
  private lateinit var monitor: AndroidLiveEditDeployMonitor
  private lateinit var service: LiveEditService
  private lateinit var editList: AndroidLiveEditDeployMonitor.EditsListener
  private lateinit var client: ClientImpl

  private var clients: Array<Client> = arrayOf<Client>()

  private val appId = "com.test";

  private val gradleSyncString = "Gradle sync needs to be performed. Sync and rerun the app."

  @Mock
  private val mySyncState: GradleSyncState = MockitoKt.mock()

  @Mock
  private val device: IDevice = MockitoKt.mock()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    Logger.getInstance(AndroidLiveEditDeployMonitor::class.java).setLevel(LogLevel.ALL)
    project = projectRule.project
    client = MockitoKt.mock()

    project.replaceService(GradleSyncState::class.java, mySyncState, projectRule.testRootDisposable)

    val clientData: ClientData = MockitoKt.mock()
    `when`(client.clientData).thenReturn(clientData)
    `when`(clientData.packageName).thenReturn(appId)

    clients = clients.plus(client)
    service = LiveEditService(project)
    monitor = AndroidLiveEditDeployMonitor(service, project)

    `when`(device.serialNumber).thenReturn("1")
    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    `when`(device.isOnline).thenReturn(true)
    `when`(device.clients).thenReturn(clients)
    `when`(mySyncState.lastSyncFinishedTimeStamp).thenReturn(1)

    editList = monitor.EditsListener()

    LiveEditApplicationConfiguration.getInstance().leTriggerMode = LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_AUTOMATIC
    LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT

    val callback = monitor.getCallback(appId, device)

    callback.call()

    Disposer.register(project, service)
  }

  @Test
  fun upToDateTest(){
    val status = service.editStatus(device)

    assertThat(status.editState).isEqualTo(EditState.UP_TO_DATE)
  }

  @Test
  fun gradleSyncTest(){
    val editEvent = MockitoKt.mock<EditEvent>()
    `when`(mySyncState.isSyncNeeded()).thenReturn(ThreeState.YES)

    editList.onLiteralsChanged(editEvent)

    val status = service.editStatus(device)

    assertThat(status.editState).isEqualTo(EditState.ERROR)
    assertThat(status.message).contains(gradleSyncString)
  }

  @Test
  fun gradeTimeSyncTest(){
    val editEvent = MockitoKt.mock<EditEvent>()
    `when`(mySyncState.isSyncNeeded()).thenReturn(ThreeState.NO)


    editList.onLiteralsChanged(editEvent)

    val status = service.editStatus(device)

    assertThat(status.editState).isEqualTo(EditState.UP_TO_DATE)

    `when`(mySyncState.lastSyncFinishedTimeStamp).thenReturn(2)

    editList.onLiteralsChanged(editEvent)

    val status2 = service.editStatus(device)

    assertThat(status2.editState).isEqualTo(EditState.ERROR)
    assertThat(status2.message).contains(gradleSyncString)
  }

  @After
  fun dispose(){
    Disposer.dispose(service)
    editList.dispose()
  }
}