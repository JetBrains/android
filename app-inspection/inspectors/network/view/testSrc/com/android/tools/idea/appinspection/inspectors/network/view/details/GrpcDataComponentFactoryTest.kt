package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.REQUEST
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.RESPONSE
import com.android.tools.idea.appinspection.inspectors.network.view.details.GrpcDataComponentFactory.ProtoFileFinder
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.json.JsonFileType
import com.intellij.mock.MockDocument
import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.mock.MockFileTypeManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import io.ktor.utils.io.core.toByteArray
import javax.swing.Icon
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

private val PROTO =
  """
  # proto-message: Foo
  foo: 1
"""
    .trimIndent()

private val PROTO_FILE =
  LightVirtualFile(
    "foo.proto",
    """
      message Foo {
      }
    """
      .trimIndent(),
  )

/** Tests for [GrpcDataComponentFactory] */
@RunsInEdt
internal class GrpcDataComponentFactoryTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ApplicationServiceRule(FileDocumentManager::class.java, FakeFileDocumentManager()),
      ApplicationServiceRule(
        FileTypeManager::class.java,
        MockFileTypeManager(FakeProtoTextFileType),
      ),
      EdtRule(),
      disposableRule,
    )

  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  @Test
  fun createBodyComponent_requestIsJson() {
    val factory = grpcDataComponentFactory(requestPayload = JSON)

    val component = factory.createBodyComponent(REQUEST)

    val editor = component?.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(JsonFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_responseIsJson() {
    val factory = grpcDataComponentFactory(responsePayload = JSON)

    val component = factory.createBodyComponent(RESPONSE)

    val editor = component?.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(JsonFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_requestIsXml() {
    val factory = grpcDataComponentFactory(requestPayload = XML)

    val component = factory.createBodyComponent(REQUEST)

    val editor = component?.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(XmlFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_responseIsXml() {
    val factory = grpcDataComponentFactory(responsePayload = XML)

    val component = factory.createBodyComponent(RESPONSE)

    val editor = component?.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(XmlFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_responseIsProto_withoutProtoFile() {
    val factory = grpcDataComponentFactory(responsePayloadText = PROTO)

    val component = factory.createBodyComponent(RESPONSE)

    val editor = component?.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(FakeProtoTextFileType)
    assertThat(editor.document.text)
      .isEqualTo(
        """
          # proto-file: ???
          # proto-message: Foo
          foo: 1
        """
          .trimIndent()
      )
  }

  @Test
  fun createBodyComponent_responseIsProto_withProtoFile() {
    val factory =
      grpcDataComponentFactory(
        responsePayloadText = PROTO,
        protoFileFinder = { listOf(PROTO_FILE) },
      )

    val component = factory.createBodyComponent(RESPONSE)

    val editor = component?.findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(FakeProtoTextFileType)
    assertThat(editor.document.text)
      .isEqualTo(
        """
          # proto-file: foo.proto
          # proto-message: Foo
          foo: 1
        """
          .trimIndent()
      )
  }

  @Test
  fun createBodyComponent_requestIsProto_switchingPanel() {
    val factory = grpcDataComponentFactory(requestPayloadText = PROTO)

    val component = factory.createBodyComponent(REQUEST)

    val switchingPanel = component?.findDescendant<SwitchingPanel> { true } ?: fail()
    assertThat(switchingPanel.getComponent(0).findDescendant<EditorComponentImpl> { true })
      .isNotNull()
    assertThat(switchingPanel.getComponent(1)).isInstanceOf(BinaryDataViewer::class.java)
  }

  @Test
  fun createBodyComponent_responseIsProto_switchingPanel() {
    val factory = grpcDataComponentFactory(responsePayloadText = PROTO)

    val component = factory.createBodyComponent(RESPONSE)

    val switchingPanel = component?.findDescendant<SwitchingPanel> { true } ?: fail()
    assertThat(switchingPanel.getComponent(0).findDescendant<EditorComponentImpl> { true })
      .isNotNull()
    assertThat(switchingPanel.getComponent(1)).isInstanceOf(BinaryDataViewer::class.java)
  }

  @Test
  fun createBodyComponent_unknownRequestFormat() {
    val factory = grpcDataComponentFactory(requestPayload = """unknown""")

    val component = factory.createBodyComponent(REQUEST)

    val switchingPanel = component?.findDescendant<SwitchingPanel> { true } ?: fail()
    assertThat(switchingPanel.getComponent(0)).isInstanceOf(BinaryDataViewer::class.java)
    val editor =
      switchingPanel.getComponent(1).findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(PlainTextFileType.INSTANCE)
  }

  @Test
  fun createBodyComponent_unknownResponseFormat() {
    val factory = grpcDataComponentFactory(responsePayload = """unknown""")

    val component = factory.createBodyComponent(RESPONSE)

    val switchingPanel = component?.findDescendant<SwitchingPanel> { true } ?: fail()
    assertThat(switchingPanel.getComponent(0)).isInstanceOf(BinaryDataViewer::class.java)
    val editor =
      switchingPanel.getComponent(1).findDescendant<EditorComponentImpl> { true }?.editor ?: fail()
    assertThat(editor.virtualFile.fileType).isEqualTo(PlainTextFileType.INSTANCE)
  }

  private fun grpcDataComponentFactory(
    requestPayloadText: String = "",
    requestPayload: String = requestPayloadText,
    responsePayloadText: String = "",
    responsePayload: String = responsePayloadText,
    protoFileFinder: ProtoFileFinder = ProtoFileFinder { emptyList() },
  ) =
    GrpcDataComponentFactory(
      project,
      disposable,
      GrpcData.createGrpcData(
        id = 0,
        requestPayload = ByteString.copyFrom(requestPayload.toByteArray()),
        requestPayloadText = requestPayloadText,
        responsePayload = ByteString.copyFrom(responsePayload.toByteArray()),
        responsePayloadText = responsePayloadText,
      ),
      protoFileFinder = protoFileFinder,
    )

  private class FakeFileDocumentManager : MockFileDocumentManagerImpl(null, { MockDocument() }) {
    override fun getDocument(file: VirtualFile): Document {
      return DocumentImpl(file.readText())
    }
  }

  private object FakeProtoTextFileType : FileType {
    override fun getName() = "ProtoText"

    override fun getDescription() = "Proto text"

    override fun getDefaultExtension() = "textproto"

    override fun getIcon(): Icon? = null

    override fun isBinary() = false
  }
}
