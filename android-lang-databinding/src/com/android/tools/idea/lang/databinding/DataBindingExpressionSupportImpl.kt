/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding

import com.android.tools.idea.lang.databinding.config.DbFile
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes
import com.android.tools.idea.lang.databinding.psi.PsiDbDefaults
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import java.util.regex.Pattern

class DataBindingExpressionSupportImpl : DataBindingExpressionSupport {
  override fun getBindingExprDefault(expr: String): String? {
    if (!expr.contains(DbTokenTypes.DEFAULT_KEYWORD.toString())) {
      // A fast check since many expressions would likely not have a default.
      return null
    }
    val defaultCheck = Pattern.compile(",\\s*default\\s*=\\s*")
    var index = 0
    val matcher = defaultCheck.matcher(expr)
    while (matcher.find()) {
      index = matcher.end()
    }
    var def = expr.substring(index, expr.length - 1).trim { it <= ' ' }  // remove the trailing "}"
    if (def.startsWith("\"") && def.endsWith("\"")) {
      def = def.substring(1, def.length - 1)       // Unquote the string.
    }
    return def
  }

  override fun getBindingExprDefault(psiAttribute: XmlAttribute): String? {
    val attrValue = psiAttribute.valueElement
    if (attrValue is PsiLanguageInjectionHost) {
      val injections = Ref.create<PsiElement>()
      InjectedLanguageManager.getInstance(psiAttribute.project).enumerate(attrValue) { injectedPsi, places ->
        if (injectedPsi is DbFile) {
          injections.set(injectedPsi)
        }
      }
      if (injections.get() != null) {
        val defaults = PsiTreeUtil.getChildOfType(injections.get(), PsiDbDefaults::class.java)
        if (defaults != null) {
          val constantValue = defaults.constantValue
          val stringLiteral = constantValue.node.findChildByType(DbTokenTypes.STRING_LITERAL)
          return if (stringLiteral == null) {
            constantValue.text
          }
          else {
            val text = stringLiteral.text
            return if (text.length > 1) {
              text.substring(1, text.length - 1)  // return unquoted string literal.
            }
            else {
              text
            }
          }
        }
      }
    }
    return null
  }

}