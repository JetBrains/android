/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi.java

import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.android.augment.AndroidLightClassBase

/**
 * Light class for Args.Builder classes generated from navigation xml files.
 *
 * For example, if you had the following "nav.xml":
 * ```
 *  <action id="@+id/sendMessage" destination="@+id/editorFragment">
 *    <argument name="message" argType="string" />
 *    <argument name="timeout" argType="integer" />
 *  </action>
 * ```
 *
 * This would generate a builder class like the following:
 * ```
 *  class EditorFragmentArgs {
 *    static class Builder {
 *      Builder(EditorFragmentArgs other);
 *      Builder(String message, int timeout);
 *      Builder setMessage(String message);
 *      String getMessage();
 *      Builder setTimeout(int timeout);
 *      int getTimeout();
 *      EditorFragmentArgs build();
 *    }
 *    ...
 *  }
 * ```
 *
 * See also: [LightArgsClass], which own this builder.
 */
class LightArgsBuilderClass(
  private val navInfo: NavInfo,
  private val argsClass: LightArgsClass,
) :
  AndroidLightClassBase(
    PsiManager.getInstance(navInfo.facet.module.project),
    setOf(PsiModifier.PUBLIC, PsiModifier.STATIC)
  ) {
  companion object {
    const val BUILDER_NAME = "Builder"
  }

  private val name: String = BUILDER_NAME
  private val qualifiedName: String = "${argsClass.qualifiedName}.$BUILDER_NAME"
  private val _constructors by lazy { computeConstructors() }
  private val _methods by lazy { computeMethods() }

  override fun getName() = name

  override fun getQualifiedName() = qualifiedName

  override fun getContainingFile() = argsClass.containingFile

  override fun getContainingClass() = argsClass

  override fun getParent() = argsClass

  override fun isValid() = true

  override fun getNavigationElement() = argsClass.navigationElement

  override fun getConstructors() = _constructors

  override fun getMethods() = _methods

  override fun getAllMethods() = methods

  override fun findMethodsByName(name: String, checkBases: Boolean): Array<PsiMethod> {
    return allMethods.filter { method -> method.name == name }.toTypedArray()
  }

  private fun computeConstructors(): Array<PsiMethod> {
    val copyConstructor =
      createConstructor().addParameter("original", PsiTypesUtil.getClassType(argsClass))

    val argsConstructor =
      createConstructor().apply {
        argsClass.destination.arguments.forEach { arg ->
          if (arg.defaultValue == null) {
            this.addParameter(arg.name.toCamelCase(), arg.parsePsiType(navInfo.packageName, this))
          }
        }
      }

    return arrayOf(copyConstructor, argsConstructor)
  }

  private fun computeMethods(): Array<PsiMethod> {
    val thisType = PsiTypesUtil.getClassType(this)

    // Create a getter and setter per argument
    val argMethods: Array<PsiMethod> =
      containingClass.destination.arguments
        .flatMap { arg ->
          val argType = arg.parsePsiType(navInfo.packageName, this)
          val setter =
            createMethod(
                name = "set${arg.name.toUpperCamelCase()}",
                navigationElement = containingClass.getFieldNavigationElementByName(arg.name),
                returnType = annotateNullability(thisType)
              )
              .addParameter(arg.name.toCamelCase(), argType)

          val getter =
            createMethod(
              name = "get${arg.name.toUpperCamelCase()}",
              navigationElement = containingClass.getFieldNavigationElementByName(arg.name),
              returnType = annotateNullability(argType, arg.isNonNull())
            )

          listOf(setter, getter)
        }
        .toTypedArray()

    val build =
      createMethod(
        name = "build",
        returnType = annotateNullability(PsiTypesUtil.getClassType(argsClass))
      )
    return argMethods + build
  }
}
