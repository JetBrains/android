/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lang.roomSql

import com.intellij.lang.injection.ConcatenationAwareInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.nullize

private const val QUERY_ANNOTATION_NAME = "android.arch.persistence.room.Query"
private const val SQLITE_DATABASE_CLASS_NAME = "android.database.sqlite.SQLiteDatabase"

/** Maps methods in SQLiteDatabase that take a query string to the index of the argument that is the query. */
private val SQLITE_DATABASE_METHODS =
    mapOf("execSQL" to 0, "compileStatement" to 0, "rawQuery" to 0, "rawQueryWithFactory" to 1, "validateSql" to 0)

class RoomSqlLanguageInjector : ConcatenationAwareInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, vararg operands: PsiElement) {
    if (!isSql(operands.first())) return

    val hosts = arrayListOf<PsiLanguageInjectionHost>()
    val prefixes = arrayListOf<String?>()
    val constantText = StringBuilder()

    for (element in operands) {
      when (element) {
        is PsiLanguageInjectionHost -> {
          hosts += element
          prefixes += constantText.toString().nullize()
          constantText.setLength(0)
        }
        is PsiExpression -> {
          val constant: String = JavaConstantExpressionEvaluator.computeConstantExpression(element, false)?.toString() ?: return
          constantText.append(constant)
        }
        else -> return
      }
    }

    with (registrar) {
      startInjecting(ROOM_SQL_LANGUAGE)

      for ((host, prefix) in hosts.zip(prefixes)) {
        addPlace(
            prefix,
            if (host === hosts.last()) constantText.toString().nullize() else null,
            host,
            TextRange(1, host.textLength - 1))
      }

      doneInjecting()
    }
  }

  private fun isSql(element: PsiElement) = insideRoomQuery(element) || insideExecSql(element)

  private fun insideExecSql(element: PsiElement): Boolean {
    // Check if element is inside a method invocation.
    val methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java) ?: return false
    val targetMethod = methodCall.methodExpression.resolve() as? PsiMethod ?: return false

    // Now check it's the right argument of the right method.
    val sqlArgumentIndex = SQLITE_DATABASE_METHODS[targetMethod.name] ?: return false
    return targetMethod.containingClass?.qualifiedName == SQLITE_DATABASE_CLASS_NAME
        && PsiTreeUtil.isAncestor(methodCall.argumentList.expressions[sqlArgumentIndex], element, false)
  }

  private fun insideRoomQuery(element: PsiElement): Boolean {
    val annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation::class.java) ?: return false
    return annotation.qualifiedName == QUERY_ANNOTATION_NAME
        && annotation.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) != null
  }
}
