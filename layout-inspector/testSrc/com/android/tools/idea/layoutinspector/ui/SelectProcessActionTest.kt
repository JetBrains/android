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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.ProjectFacetManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.DisposableRule
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class SelectProcessActionTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val disposableRule = DisposableRule()

  var androidModel: AndroidModel? = null

  @Before
  fun setUp() {
    androidModel = mock()
    ProjectFacetManager.getInstance(projectRule.project).getFacets(AndroidFacet.ID)[0].putUserData(AndroidModel.KEY, androidModel)
  }

  @Test
  fun testConnected() {
    doReturn(setOf("process3", "process4")).`when`(androidModel)?.allApplicationIds
    val inspector: LayoutInspector = mock()
    val inspectorModel = model(project = projectRule.project) {}
    `when`(inspector.layoutInspectorModel).thenReturn(inspectorModel)

    val client: InspectorClient = mock()
    val stream1 = Common.Stream.newBuilder().apply {
      setDevice(Common.Device.newBuilder().apply {
        serial = "serial123"
        model = "myModel123"
        manufacturer = "myManufacturer"
        isEmulator = true
        featureLevel = 29
      })
    }.build()
    `when`(client.getProcesses(stream1)).thenReturn(buildProcesses(3))

    val stream2 = Common.Stream.newBuilder().apply {
      setDevice(Common.Device.newBuilder().apply {
        serial = "serial321"
        model = "myOldModel"
        manufacturer = "myManufacturer"
        isEmulator = false
        featureLevel = 19
      })
    }.build()
    `when`(client.getProcesses(stream2)).thenReturn(buildProcesses(4))

    `when`(client.getStreams()).thenReturn(sequenceOf(stream1, stream2))

    `when`(inspector.allClients).thenReturn(listOf(client))

    val inspectorClient = inspector.allClients[0]
    val stream = inspectorClient.getStreams().elementAt(1)
    val process = inspectorClient.getProcesses(stream).elementAt(2)
    `when`(inspectorClient.selectedProcess).thenReturn(process)
    `when`(inspectorClient.selectedStream).thenReturn(stream)

    val selectProcessAction = SelectProcessAction(inspector)
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children.size).isEqualTo(3)
    assertThat(children[0].templateText).isEqualTo("myModel123")
    assertThat(children[1].templateText).isEqualTo("myManufacturer myOldModel (Unsupported for API < 23)")
    assertThat(children[2].templateText).isEqualTo("Stop inspector")

    val event = mock<AnActionEvent>()
    val presentation = mock<Presentation>()
    `when`(event.presentation).thenReturn(presentation)
    `when`(inspector.currentClient).thenReturn(inspectorClient)

    children[1].update(event)
    verify(presentation).isEnabled = false

    `when`(inspectorClient.isConnected).thenReturn(false)
    children[2].update(event)
    verify(presentation, times(2)).isEnabled = false

    `when`(inspectorClient.isConnected).thenReturn(true)
    children[2].update(event)
    verify(presentation).isEnabled = true

    val subChildren0 = (children[0] as ActionGroup).getChildren(null)
    subChildren0.forEach { it.update(mockEvent()) }
    assertThat(subChildren0.size).isEqualTo(4)
    assertThat(subChildren0[0].templateText).isEqualTo("process3 (300)")
    assertThat((subChildren0[0] as ToggleAction).isSelected(mock())).isEqualTo(false)
    assertThat(subChildren0[1]).isEqualTo(Separator.getInstance())
    assertThat(subChildren0[2].templateText).isEqualTo("process1 (100)")
    assertThat((subChildren0[2] as ToggleAction).isSelected(mock())).isEqualTo(false)
    assertThat(subChildren0[3].templateText).isEqualTo("process2 (200)")
    assertThat((subChildren0[3] as ToggleAction).isSelected(mock())).isEqualTo(false)
  }

  private fun buildProcesses(processCount: Int): Sequence<Common.Process> = (1..processCount).map {
    Common.Process.newBuilder().apply {
      name = "process$it"
      pid = it * 100
    }.build()
  }.asSequence()

  private fun mockEvent(): AnActionEvent = mock<AnActionEvent>().also { `when`(it.presentation).thenReturn(mock())}

  @Test
  fun testNoProcesses() {
    val inspector: LayoutInspector = mock()
    val inspectorModel = model(project = projectRule.project) {}
    `when`(inspector.layoutInspectorModel).thenReturn(inspectorModel)
    `when`(inspector.allClients).thenReturn(listOf())

    val selectProcessAction = SelectProcessAction(inspector)
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children.size).isEqualTo(1)
    assertThat(children[0].templateText).isEqualTo("No devices detected")
  }

  @Test
  fun testConnectionError() {
    val notificationService = projectRule.mockProjectService(AndroidNotification::class.java)
    val client: InspectorClient = mock()
    val process: Common.Process = mock()
    val stream: Common.Stream = mock()
    `when`(client.attach(stream, process)).thenThrow(StatusRuntimeException(Status.CANCELLED))
    val connectAction = SelectProcessAction.ConnectAction(process, stream, client, projectRule.project)
    connectAction.connect()
    verify(notificationService, timeout(1000)).showBalloon(eq("Connection Failed"), any(), eq(NotificationType.WARNING))
  }
}