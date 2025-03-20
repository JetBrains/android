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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.android.tools.idea.protobuf.ByteString
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XCollection
import javax.swing.JTable
import kotlin.properties.Delegates
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import studio.network.inspection.NetworkInspectorProtocol.InterceptRule
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type
import studio.network.inspection.NetworkInspectorProtocol.Transformation

class RuleData(
  // must be a public var for PersistentStateComponent to set it
  var id: Int,
  name: String,
  isActive: Boolean,
) {

  companion object {
    private var count = 0

    fun newId(): Int {
      count += 1
      return count
    }

    fun getLatestId() = count
  }

  @Suppress("unused") // invoked via reflection by PersistentStateComponent
  private constructor() : this(0, "", false)

  @get:Transient
  var ruleDataListener: RuleDataListener = RuleDataAdapter()
    set(value) {
      field = value
      val closure = { ruleDataListener.onRuleDataChanged(this) }
      criteria.listener = closure
      statusCodeRuleData.listener = closure
      headerRuleTableModel.listener = closure
      bodyRuleTableModel.listener = closure
    }

  class CriteriaData {
    @get:Transient var listener: (() -> Unit)? = null

    private fun <T> delegate(initialValue: T) =
      Delegates.observable(initialValue) { _, _, _ -> listener?.invoke() }

    var protocol: Protocol by delegate(Protocol.HTTPS)
    var host: String by delegate("")
    var port: String by delegate("")
    var path: String by delegate("")
    var query: String by delegate("")
    var method: Method by delegate(Method.GET)

    val url: String
      get() =
        "$protocol://${host.ifBlank { "<Any>" }}${port.withPrefixIfNotEmpty(':')}$path${query.withPrefixIfNotEmpty('?')}"

    fun toProto(variables: List<RuleVariable>): InterceptCriteria =
      InterceptCriteria.newBuilder()
        .apply {
          protocol = this@CriteriaData.protocol.proto
          host = variables.applyTo(this@CriteriaData.host)
          port = variables.applyTo(this@CriteriaData.port)
          path = variables.applyTo(this@CriteriaData.path)
          query = variables.applyTo(this@CriteriaData.query)
          method = this@CriteriaData.method.proto
        }
        .build()

    private fun String.withPrefixIfNotEmpty(prefix: Char) = if (isBlank()) "" else prefix + this

    fun copyFrom(other: CriteriaData) {
      protocol = other.protocol
      host = other.host
      port = other.port
      path = other.path
      query = other.query
      method = other.method
    }
  }

  interface TransformationRuleData {
    fun toProto(variables: List<RuleVariable>): Transformation
  }

  class StatusCodeRuleData(findCode: String?, isActive: Boolean?, newCode: String?) :
    TransformationRuleData {
    @Suppress("unused") // invoked via reflection by PersistentStateComponent
    private constructor() : this(null, null, null)

    @get:Transient var listener: (() -> Unit)? = null

    private fun <T> delegate(initialValue: T) =
      Delegates.observable(initialValue) { _, _, _ -> listener?.invoke() }

    var findCode: String by delegate(findCode ?: "")
    var newCode: String by delegate(newCode ?: "")
    var isActive: Boolean by delegate(isActive == true)

    override fun toProto(variables: List<RuleVariable>): Transformation =
      Transformation.newBuilder()
        .apply {
          statusCodeReplacedBuilder.apply {
            targetCodeBuilder.apply {
              type = Type.PLAIN
              text = variables.applyTo(findCode)
            }
            newCode = variables.applyTo(this@StatusCodeRuleData.newCode)
          }
        }
        .build()

    fun copyFrom(other: StatusCodeRuleData) {
      isActive = other.isActive
      findCode = other.findCode
      newCode = other.newCode
    }
  }

  class HeaderAddedRuleData(name: String?, value: String?) : TransformationRuleData {
    var name = name ?: ""
    var value = value ?: ""

    @Suppress("unused") // invoked via reflection by PersistentStateComponent
    private constructor() : this(null, null)

    override fun toProto(variables: List<RuleVariable>): Transformation =
      Transformation.newBuilder()
        .apply {
          headerAddedBuilder.apply {
            name = variables.applyTo(this@HeaderAddedRuleData.name)
            value = variables.applyTo(this@HeaderAddedRuleData.value)
          }
        }
        .build()
  }

  class HeaderReplacedRuleData(
    // These must be public vars for PersistentStateComponent to set them.
    var findName: String?,
    var isFindNameRegex: Boolean,
    var findValue: String?,
    var isFindValueRegex: Boolean,
    var newName: String?,
    var newValue: String?,
  ) : TransformationRuleData {

    @Suppress("unused") // invoked via reflection by PersistentStateComponent
    private constructor() : this(null, false, null, false, null, null)

    override fun toProto(variables: List<RuleVariable>): Transformation =
      Transformation.newBuilder()
        .apply {
          headerReplacedBuilder.apply {
            if (findName != null) {
              targetNameBuilder.apply {
                text = variables.applyTo(findName)
                type = matchingTextTypeFrom(isFindNameRegex)
              }
            }
            if (findValue != null) {
              targetValueBuilder.apply {
                text = variables.applyTo(findValue)
                type = matchingTextTypeFrom(isFindValueRegex)
              }
            }
            if (this@HeaderReplacedRuleData.newName != null) {
              newName = variables.applyTo(this@HeaderReplacedRuleData.newName)
            }
            if (this@HeaderReplacedRuleData.newValue != null) {
              newValue = variables.applyTo(this@HeaderReplacedRuleData.newValue)
            }
          }
        }
        .build()
  }

  class HeaderRulesTableModel : ListTableModel<TransformationRuleData>() {
    @get:Transient var listener: (() -> Unit)? = null

    init {
      columnInfos =
        arrayOf(
          object : ColumnInfo<TransformationRuleData, String>("Type") {
            override fun getWidth(table: JTable) = JBUIScale.scale(40)

            override fun getRenderer(item: TransformationRuleData) = MyRenderer

            override fun valueOf(item: TransformationRuleData) =
              when (item) {
                is HeaderAddedRuleData -> "Add"
                is HeaderReplacedRuleData -> "Edit"
                else -> ""
              }
          },
          object : ColumnInfo<TransformationRuleData, Pair<String?, String?>>("Name") {
            override fun getRenderer(item: TransformationRuleData) = MyRenderer

            override fun valueOf(item: TransformationRuleData): Pair<String?, String?> {
              return when (item) {
                is HeaderAddedRuleData -> item.name to null
                is HeaderReplacedRuleData -> item.findName to item.newName
                else -> throw UnsupportedOperationException("Unknown item $item")
              }
            }
          },
          object : ColumnInfo<TransformationRuleData, Pair<String?, String?>>("Value") {
            override fun getRenderer(item: TransformationRuleData) = MyRenderer

            override fun valueOf(item: TransformationRuleData): Pair<String?, String?> {
              return when (item) {
                is HeaderAddedRuleData -> item.value to null
                is HeaderReplacedRuleData -> item.findValue to item.newValue
                else -> throw UnsupportedOperationException("Unknown item $item")
              }
            }
          },
        )
      addTableModelListener { listener?.invoke() }
    }

    // For PersistentStateComponent
    @XCollection(elementTypes = [HeaderAddedRuleData::class, HeaderReplacedRuleData::class])
    override fun getItems(): MutableList<TransformationRuleData> {
      return super.getItems()
    }

    // For PersistentStateComponent
    @XCollection(elementTypes = [HeaderAddedRuleData::class, HeaderReplacedRuleData::class])
    override fun setItems(items: MutableList<TransformationRuleData>) {
      super.setItems(items)
    }

    fun copyFrom(other: HeaderRulesTableModel) {
      items = other.items.toMutableList()
    }
  }

  class BodyReplacedRuleData(
    // This must be a public var for PersistentStateComponent to set it.
    var body: String
  ) : TransformationRuleData {
    @Suppress("unused") // invoked via reflection by PersistentStateComponent
    private constructor() : this("")

    override fun toProto(variables: List<RuleVariable>): Transformation =
      Transformation.newBuilder()
        .apply {
          bodyReplacedBuilder.apply {
            body =
              ByteString.copyFrom(variables.applyTo(this@BodyReplacedRuleData.body)!!.toByteArray())
          }
        }
        .build()
  }

  class BodyModifiedRuleData(
    // These must be public vars for PersistentStateComponent to set them.
    var targetText: String,
    var isRegex: Boolean,
    var newText: String,
  ) : TransformationRuleData {
    @Suppress("unused") // invoked via reflection by PersistentStateComponent
    private constructor() : this("", false, "")

    override fun toProto(variables: List<RuleVariable>): Transformation =
      Transformation.newBuilder()
        .apply {
          bodyModifiedBuilder.apply {
            targetTextBuilder.apply {
              text = variables.applyTo(this@BodyModifiedRuleData.targetText)
              type = matchingTextTypeFrom(isRegex)
            }
            newText = variables.applyTo(this@BodyModifiedRuleData.newText)
          }
        }
        .build()
  }

  class BodyRulesTableModel : ListTableModel<TransformationRuleData>() {
    @get:Transient var listener: (() -> Unit)? = null

    init {
      columnInfos =
        arrayOf(
          object : ColumnInfo<TransformationRuleData, String>("Type") {
            override fun getWidth(table: JTable) = JBUIScale.scale(45)

            override fun getRenderer(item: TransformationRuleData) = MyRenderer

            override fun valueOf(item: TransformationRuleData) =
              when (item) {
                is BodyReplacedRuleData -> "Replace"
                is BodyModifiedRuleData -> "Edit"
                else -> ""
              }
          },
          object : ColumnInfo<TransformationRuleData, String>("Find") {
            override fun valueOf(item: TransformationRuleData): String {
              return when (item) {
                is BodyReplacedRuleData -> ""
                is BodyModifiedRuleData -> item.targetText
                else -> ""
              }
            }
          },
          object : ColumnInfo<TransformationRuleData, String>("Replace With") {
            override fun valueOf(item: TransformationRuleData): String {
              return when (item) {
                is BodyReplacedRuleData -> item.body
                is BodyModifiedRuleData -> item.newText
                else -> ""
              }
            }
          },
        )
      addTableModelListener { listener?.invoke() }
    }

    // For PersistentStateComponent
    @XCollection(elementTypes = [BodyModifiedRuleData::class, BodyReplacedRuleData::class])
    override fun getItems(): MutableList<TransformationRuleData> = super.getItems()

    // For PersistentStateComponent
    @XCollection(elementTypes = [BodyModifiedRuleData::class, BodyReplacedRuleData::class])
    override fun setItems(items: MutableList<TransformationRuleData>) = super.setItems(items)

    fun copyFrom(other: BodyRulesTableModel) {
      items = other.items.toMutableList()
    }
  }

  var name: String by
    Delegates.observable(name) { _, _, _ -> ruleDataListener.onRuleNameChanged(this) }
  var isActive: Boolean by
    Delegates.observable(isActive) { _, _, _ -> ruleDataListener.onRuleIsActiveChanged(this) }

  // These must be public vars for PersistentStateComponent to set them.
  var criteria = CriteriaData()
  var statusCodeRuleData = StatusCodeRuleData("", false, "")
  var headerRuleTableModel = HeaderRulesTableModel()
  var bodyRuleTableModel = BodyRulesTableModel()

  fun toProto(variables: List<RuleVariable>): InterceptRule =
    InterceptRule.newBuilder()
      .apply {
        enabled = isActive
        criteria = this@RuleData.criteria.toProto(variables)
        if (statusCodeRuleData.isActive) {
          addTransformation(statusCodeRuleData.toProto(variables))
        }
        addAllTransformation(headerRuleTableModel.items.map { it.toProto(variables) })
        addAllTransformation(bodyRuleTableModel.items.map { it.toProto(variables) })
      }
      .build()

  fun copyFrom(other: RuleData) {
    name = other.name
    isActive = other.isActive

    criteria.copyFrom(other.criteria)
    statusCodeRuleData.copyFrom(other.statusCodeRuleData)
    headerRuleTableModel.copyFrom(other.headerRuleTableModel)
    bodyRuleTableModel.copyFrom(other.bodyRuleTableModel)
  }
}

fun matchingTextTypeFrom(isRegex: Boolean): Type = if (isRegex) Type.REGEX else Type.PLAIN

private object MyRenderer : ColoredTableCellRenderer() {
  @Suppress("unused") // a `ColoredTableCellRenderer` must implement `readResolve`
  private fun readResolve(): Any = MyRenderer

  override fun customizeCellRenderer(
    table: JTable,
    item: Any?,
    selected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ) {
    clear()
    border = JBUI.Borders.empty()
    when (item) {
      is Pair<*, *> -> {
        if (item.first == null && item.second == null) {
          append("Unchanged", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        } else {
          (item.first as? String)?.let { append(it) }
            ?: append("Any", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          (item.second as? String)?.let {
            append("  ➔  ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
            append(it)
          }
        }
      }
      is String -> append(item)
      else -> Unit
    }
  }
}
