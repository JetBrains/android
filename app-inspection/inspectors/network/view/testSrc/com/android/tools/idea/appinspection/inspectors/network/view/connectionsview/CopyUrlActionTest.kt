package com.android.tools.idea.appinspection.inspectors.network.view.connectionsview

import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.android.tools.idea.testing.FakeClipboard
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import java.awt.datatransfer.DataFlavor.stringFlavor
import org.junit.Rule
import org.junit.Test

/** Tests for [CopyUrlAction] */
class CopyUrlActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val fakeClipboard = FakeClipboard()

  @Test
  fun actionPerformed_httpData() {
    val action = CopyUrlAction(HttpData.createHttpData(0, url = "foo")) { fakeClipboard }

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getContents(null).getTransferData(stringFlavor)).isEqualTo("foo")
  }

  @Test
  fun actionPerformed_grpcData() {
    val action =
      CopyUrlAction(
        GrpcData.createGrpcData(0, address = "address", service = "service", method = "method")
      ) {
        fakeClipboard
      }

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getContents(null).getTransferData(stringFlavor))
      .isEqualTo("grpc://address/service/method")
  }
}
