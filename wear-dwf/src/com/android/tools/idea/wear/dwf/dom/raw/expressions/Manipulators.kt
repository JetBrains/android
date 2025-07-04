/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.impl.source.DummyHolderFactory

class WFFExpressionConfigurationManipulator :
  AbstractElementManipulator<WFFExpressionConfiguration>() {
  override fun handleContentChange(
    element: WFFExpressionConfiguration,
    range: TextRange,
    newContent: String,
  ): WFFExpressionConfiguration? {

    val newConfiguration =
      element.node.text.replaceRange(IntRange(range.startOffset, range.endOffset - 1), newContent)
    val newElement = createConfigurationIdFromText(newConfiguration, element)
    return if (newElement != null) element.replace(newElement) as WFFExpressionConfiguration
    else element
  }

  private fun createConfigurationIdFromText(
    text: String,
    element: WFFExpressionConfiguration,
  ): WFFExpressionConfiguration? {
    val builder =
      PsiBuilderFactory.getInstance()
        .createBuilder(WFFExpressionParserDefinition(), WFFExpressionLexer(), text)
    val ast = WFFExpressionParser().parse(WFFExpressionTypes.CONFIGURATION, builder)
    val configuration = ast.psi as? WFFExpressionConfiguration ?: return null
    // Give the new PSI element a parent, otherwise it will be invalid.
    val holder = DummyHolderFactory.createHolder(element.manager, element.language, element)
    holder.treeElement.addChild(ast)
    return configuration
  }
}
