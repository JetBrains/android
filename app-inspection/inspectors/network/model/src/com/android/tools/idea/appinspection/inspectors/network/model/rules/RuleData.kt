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
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import studio.network.inspection.NetworkInspectorProtocol.InterceptRule
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type
import studio.network.inspection.NetworkInspectorProtocol.Transformation
import kotlin.reflect.KProperty

class RuleData(
  val id: Int,
  name: String,
  isActive: Boolean,
  val ruleDataListener: RuleDataListener = RuleDataAdapter()
) {
  companion object {
    private var count = 0

    fun newId(): Int {
      count += 1
      return count
    }
  }

  /**
   * An inner Delegate class to help define variables that need to notify
   * [ruleDataListener] with value changes.
   */
  inner class Delegate<T>(private var value: T, private val onSet: (RuleData) -> Unit) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
      return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      this.value = value
      onSet(this@RuleData)
    }
  }

  inner class CriteriaData(
    var protocol: String = "https",
    host: String = "",
    port: String = "",
    path: String = "",
    query: String = "",
    method: String = ""
  ) {
    var host: String by Delegate(host, ruleDataListener::onRuleDataChanged)
    var port: String by Delegate(port, ruleDataListener::onRuleDataChanged)
    var path: String by Delegate(path, ruleDataListener::onRuleDataChanged)
    var query: String by Delegate(query, ruleDataListener::onRuleDataChanged)
    var method: String by Delegate(method, ruleDataListener::onRuleDataChanged)

    fun toProto(): InterceptCriteria = InterceptCriteria.newBuilder().apply {
      protocol = this@CriteriaData.protocol
      host = this@CriteriaData.host
      port = this@CriteriaData.port
      path = this@CriteriaData.path
      query = this@CriteriaData.query
      method = this@CriteriaData.method
    }.build()
  }

  interface TransformationRuleData {
    fun toProto(): Transformation
  }

  class HeaderAddedRuleData(val name: String, val value: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      headerAddedBuilder.apply {
        name = this@HeaderAddedRuleData.name
        value = this@HeaderAddedRuleData.value
      }
    }.build()
  }

  class HeaderReplacedRuleData(
    val findName: String,
    val isFindNameRegex: Boolean,
    val findValue: String,
    val isFindValueRegex: Boolean,
    val newName: String,
    val newValue: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      headerReplacedBuilder.apply {
        targetNameBuilder.apply {
          text = findName
          type = matchingTextTypeFrom(isFindNameRegex)
        }
        targetValueBuilder.apply {
          text = findValue
          type = matchingTextTypeFrom(isFindValueRegex)
        }
        newName = this@HeaderReplacedRuleData.newName
        newValue = this@HeaderReplacedRuleData.newValue
      }
    }.build()
  }

  inner class HeaderRulesTableModel : ListTableModel<TransformationRuleData>() {
    init {
      columnInfos = arrayOf(
        object : ColumnInfo<TransformationRuleData, String>("Name") {
          override fun valueOf(item: TransformationRuleData): String {
            return when (item) {
              is HeaderAddedRuleData -> item.name
              is HeaderReplacedRuleData -> item.findName
              else -> ""
            }
          }
        },
        object : ColumnInfo<TransformationRuleData, String>("Value") {
          override fun valueOf(item: TransformationRuleData): String {
            return when (item) {
              is HeaderAddedRuleData -> item.value
              is HeaderReplacedRuleData -> item.findValue
              else -> ""
            }
          }
        })
      addTableModelListener {
        ruleDataListener.onRuleDataChanged(this@RuleData)
      }
    }
  }

  class BodyReplacedRuleData(val body: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      bodyReplacedBuilder.apply {
        body = ByteString.copyFrom(this@BodyReplacedRuleData.body.toByteArray())
      }
    }.build()
  }

  class BodyModifiedRuleData(val targetText: String, val isRegex: Boolean, val newText: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      bodyModifiedBuilder.apply {
        targetTextBuilder.apply {
          text = this@BodyModifiedRuleData.targetText
          type = matchingTextTypeFrom(isRegex)
        }
        newText = this@BodyModifiedRuleData.newText
      }
    }.build()
  }

  inner class BodyRulesTableModel : ListTableModel<TransformationRuleData>() {
    init {
      columnInfos = arrayOf(
        object : ColumnInfo<TransformationRuleData, String>("Action") {
          override fun valueOf(item: TransformationRuleData): String {
            return when (item) {
              is BodyReplacedRuleData -> "Replace All"
              is BodyModifiedRuleData -> "Replace \"${item.targetText}\""
              else -> ""
            }
          }
        },
        object : ColumnInfo<TransformationRuleData, String>("Body") {
          override fun valueOf(item: TransformationRuleData): String {
            return when (item) {
              is BodyReplacedRuleData -> item.body
              is BodyModifiedRuleData -> item.newText
              else -> ""
            }
          }
        })
      addTableModelListener {
        ruleDataListener.onRuleDataChanged(this@RuleData)
      }
    }
  }

  var name: String by Delegate(name, ruleDataListener::onRuleNameChanged)
  var isActive: Boolean by Delegate(isActive, ruleDataListener::onRuleIsActiveChanged)

  val criteria = CriteriaData()
  val headerRuleTableModel = HeaderRulesTableModel()
  val bodyRuleTableModel = BodyRulesTableModel()

  fun toProto(): InterceptRule = InterceptRule.newBuilder().apply {
    criteria = this@RuleData.criteria.toProto()
    addAllTransformation(headerRuleTableModel.items.map { it.toProto() })
    addAllTransformation(bodyRuleTableModel.items.map { it.toProto() })
  }.build()
}

fun matchingTextTypeFrom(isRegex: Boolean): Type = if (isRegex) Type.REGEX else Type.PLAIN
