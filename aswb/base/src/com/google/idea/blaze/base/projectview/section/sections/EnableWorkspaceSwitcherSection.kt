/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview.section.sections

import com.google.idea.blaze.base.projectview.parser.ParseContext
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser
import com.google.idea.blaze.base.projectview.section.ScalarSection
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser
import com.google.idea.blaze.base.projectview.section.SectionKey
import com.google.idea.blaze.base.projectview.section.SectionParser
import com.intellij.openapi.diagnostic.Logger

/**
 * Project view section class representing the `enable_workspace_switcher: [true|false]` setting.
 *
 * Governs whether workspace switcher redirection is enabled for the project.
 */
class EnableWorkspaceSwitcherSection private constructor() {
  companion object {
    private val LOG = Logger.getInstance(EnableWorkspaceSwitcherSection::class.java)

    @JvmField val KEY: SectionKey<Boolean, ScalarSection<Boolean>> = SectionKey.of("enable_workspace_switcher")

    @JvmField val PARSER: SectionParser = BooleanParser()
  }

  private class BooleanParser : ScalarSectionParser<Boolean>(KEY, ':') {
    override fun parseItem(parser: ProjectViewParser, parseContext: ParseContext, text: String): Boolean? {
      return when (text.lowercase()) {
        "true" -> true
        "false" -> false
        else -> {
          LOG.info("enable_workspace_switcher has an invalid value: $text")
          null
        }
      }
    }

    override fun printItem(sb: StringBuilder, item: Boolean) {
      sb.append(item)
    }

    override fun getItemType(): ItemType {
      return ItemType.Other
    }

    override fun quickDocs(): String {
      return "Enables dynamic workspace switching by initializing a virtual root mapping natively under your project directory."
    }
  }
}
