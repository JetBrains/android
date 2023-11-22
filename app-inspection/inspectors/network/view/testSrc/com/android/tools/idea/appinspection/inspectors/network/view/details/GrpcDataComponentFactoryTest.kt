package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.REQUEST
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.RESPONSE
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.json.JsonFileType
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import io.ktor.utils.io.core.toByteArray
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test

private val XML =
  """
    <?xml version="1.0" encoding="utf-8"?>
    <tag/>
  """
    .trimIndent()

private const val JSON = """{"foo": 1}"""

/** Tests for [GrpcDataComponentFactory] */
@RunsInEdt
internal class GrpcDataComponentFactoryTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(projectRule, EdtRule(), disposableRule)

  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  @Test
  fun createBodyComponent_requestIsJson() {
    val factory = grpcDataComponentFactory(requestPayload = JSON)

    val component = factory.createBodyComponent(REQUEST)

    val editor = component.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(JsonFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_responseIsJson() {
    val factory = grpcDataComponentFactory(responsePayload = JSON)

    val component = factory.createBodyComponent(RESPONSE)

    val editor = component.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(JsonFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_requestIsXml() {
    val factory = grpcDataComponentFactory(requestPayload = XML)

    val component = factory.createBodyComponent(REQUEST)

    val editor = component.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(XmlFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_responseIsXml() {
    val factory = grpcDataComponentFactory(responsePayload = XML)

    val component = factory.createBodyComponent(RESPONSE)

    val editor = component.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(XmlFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_unknownRequestFormat() {
    val factory = grpcDataComponentFactory(requestPayload = """unknown""")

    val component = factory.createBodyComponent(REQUEST)

    assertThat(component.findDescendant<BinaryDataViewer> { true }).isNotNull()
  }

  @Test
  fun createBodyComponent_unknownResponseFormat() {
    val factory = grpcDataComponentFactory(responsePayload = """unknown""")

    val component = factory.createBodyComponent(RESPONSE)

    assertThat(component.findDescendant<BinaryDataViewer> { true }).isNotNull()
  }

  private fun grpcDataComponentFactory(
    requestPayload: String = "",
    responsePayload: String = "",
  ) =
    GrpcDataComponentFactory(
      project,
      disposable,
      GrpcData.createGrpcData(
        id = 0,
        requestPayload = ByteString.copyFrom(requestPayload.toByteArray()),
        responsePayload = ByteString.copyFrom(responsePayload.toByteArray()),
      )
    )
}
