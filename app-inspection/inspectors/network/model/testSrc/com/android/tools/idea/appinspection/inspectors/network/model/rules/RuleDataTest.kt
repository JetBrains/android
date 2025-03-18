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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData.BodyReplacedRuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData.HeaderAddedRuleData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleDataTest {
  @Test
  fun basicRuleConstruction() {
    val rule = RuleData(1, "rule1", true)
    assertThat(rule.name).isEqualTo("rule1")

    assertThat(rule.id).isEqualTo(1)
    assertThat(rule.isActive).isTrue()
    assertThat(rule.bodyRuleTableModel.items).isEmpty()
    assertThat(rule.headerRuleTableModel.items).isEmpty()
    assertThat(rule.statusCodeRuleData.isActive).isFalse()
    assertThat(rule.criteria.protocol).isEqualTo(Protocol.HTTPS)
    assertThat(rule.criteria.host).isEmpty()
    assertThat(rule.criteria.path).isEmpty()
    assertThat(rule.criteria.method).isEqualTo(Method.GET)
    assertThat(rule.criteria.port).isEmpty()
    assertThat(rule.criteria.query).isEmpty()
    assertThat(rule.criteria.url).isEqualTo("https://<Any>")

    rule.criteria.protocol = Protocol.HTTP
    rule.criteria.host = "google.com"
    rule.criteria.path = "/blah"
    rule.criteria.port = "232"
    rule.criteria.query = "query=answer"
    assertThat(rule.criteria.url).isEqualTo("http://google.com:232/blah?query=answer")
  }

  @Test
  fun criteriaVariables() {
    val variables = buildList {
      add(RuleVariable("HOST", "www.google.com"))
      add(RuleVariable("PORT", "80"))
      add(RuleVariable("PATH", "/path"))
      add(RuleVariable("QUERY", "?name=foo"))
    }

    val rule = RuleData(1, "rule1", true)
    rule.criteria.host = "\${HOST}"
    rule.criteria.port = "\${PORT}"
    rule.criteria.path = "\${PATH}"
    rule.criteria.query = "\${QUERY}"

    val criteria = rule.toProto(variables).criteria
    assertThat(criteria.host).isEqualTo("www.google.com")
    assertThat(criteria.port).isEqualTo("80")
    assertThat(criteria.path).isEqualTo("/path")
    assertThat(criteria.query).isEqualTo("?name=foo")
  }

  @Test
  fun statusCodeVariable() {
    val variables = buildList {
      add(RuleVariable("OLD", "200"))
      add(RuleVariable("NEW", "404"))
    }
    val rule = RuleData(1, "rule1", true)
    rule.statusCodeRuleData.isActive = true
    rule.statusCodeRuleData.findCode = "\${OLD}"
    rule.statusCodeRuleData.newCode = "\${NEW}"

    val transformation = rule.toProto(variables).transformationList[0]

    assertThat(transformation.statusCodeReplaced.targetCode.text).isEqualTo("200")
    assertThat(transformation.statusCodeReplaced.newCode).isEqualTo("404")
  }

  @Test
  fun headerVariable() {
    val variables = buildList {
      add(RuleVariable("HEADER", "header"))
      add(RuleVariable("VALUE", "value"))
      add(RuleVariable("OLD_HEADER", "old-header"))
      add(RuleVariable("OLD_VALUE", "old-value"))
      add(RuleVariable("NEW_HEADER", "new-header"))
      add(RuleVariable("NEW_VALUE", "new-value"))
    }
    val rule = RuleData(1, "rule1", true)
    rule.headerRuleTableModel.items =
      buildList {
          add(HeaderAddedRuleData("\${HEADER}", "\${VALUE}"))
          add(
            RuleData.HeaderReplacedRuleData(
              "\${OLD_HEADER}",
              isFindNameRegex = false,
              "\${OLD_VALUE}",
              isFindValueRegex = false,
              "\${NEW_HEADER}",
              "\${NEW_VALUE}",
            )
          )
        }
        .toMutableList()

    val transformations = rule.toProto(variables).transformationList
    val added = transformations[0].headerAdded
    val replace = transformations[1].headerReplaced

    assertThat(added.name).isEqualTo("header")
    assertThat(added.value).isEqualTo("value")
    assertThat(replace.targetName.text).isEqualTo("old-header")
    assertThat(replace.targetValue.text).isEqualTo("old-value")
    assertThat(replace.newName).isEqualTo("new-header")
    assertThat(replace.newValue).isEqualTo("new-value")
  }

  @Test
  fun bodyVariable() {
    val variables = buildList {
      add(RuleVariable("BODY", "body"))
      add(RuleVariable("OLD_TEXT", "old-text"))
      add(RuleVariable("NEW_TEXT", "new-text"))
    }
    val rule = RuleData(1, "rule1", true)
    rule.bodyRuleTableModel.items =
      buildList {
          add(BodyReplacedRuleData("\${BODY}"))
          add(RuleData.BodyModifiedRuleData("\${OLD_TEXT}", isRegex = false, "\${NEW_TEXT}"))
        }
        .toMutableList()

    val transformations = rule.toProto(variables).transformationList
    val replaced = transformations[0].bodyReplaced
    val modified = transformations[1].bodyModified

    assertThat(replaced.body.toStringUtf8()).isEqualTo("body")
    assertThat(modified.targetText.text).isEqualTo("old-text")
    assertThat(modified.newText).isEqualTo("new-text")
  }
}
