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

import com.android.SdkConstants
import com.android.tools.idea.nav.safeargs.index.NavActionData
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.android.tools.idea.nav.safeargs.psi.xml.findFirstMatchingElementByTraversingUp
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.augment.AndroidLightClassBase

/**
 * Inner class that is generated inside a Directions class, which helps build actions.
 *
 * See the docs on [LightDirectionsClass] which provide a concrete example of what this looks like.
 */
class LightActionBuilderClass(
  className: String,
  private val navInfo: NavInfo,
  private val directionsClass: LightDirectionsClass,
  private val action: NavActionData,
  private val backingResourceFile: XmlFile?,
) :
  AndroidLightClassBase(
    PsiManager.getInstance(navInfo.facet.module.project),
    setOf(PsiModifier.PUBLIC, PsiModifier.STATIC),
  ) {
  private val NAV_DIRECTIONS_FQCN = "androidx.navigation.NavDirections"
  private val name: String = className
  private val qualifiedName: String = "${directionsClass.qualifiedName}.$name"
  private val _constructors by lazy { computeConstructors() }
  private val _methods by lazy { computeMethods() }
  private val _fields by lazy { computeFields() }
  private val navDirectionsType by lazy {
    PsiType.getTypeByName(NAV_DIRECTIONS_FQCN, project, this.resolveScope)
  }
  private val navDirectionsClass by lazy {
    JavaPsiFacade.getInstance(project).findClass(NAV_DIRECTIONS_FQCN, this.resolveScope)
  }
  private val _navigationElement by lazy {
    (directionsClass.navigationElement as? XmlTag)?.findFirstMatchingElementByTraversingUp(
      SdkConstants.TAG_ACTION,
      action.id,
    )
  }

  override fun getName() = name

  override fun getQualifiedName() = qualifiedName

  override fun getContainingFile() = directionsClass.containingFile

  override fun getContainingClass() = directionsClass

  override fun getParent() = directionsClass

  override fun isValid() = true

  override fun getNavigationElement() = _navigationElement ?: directionsClass.navigationElement

  override fun getConstructors() = _constructors

  override fun getImplementsListTypes() = arrayOf(navDirectionsType)

  override fun getSuperTypes() = arrayOf(navDirectionsType)

  override fun getSupers() = navDirectionsClass?.let { arrayOf(it) } ?: emptyArray()

  override fun getMethods() = _methods

  override fun getAllMethods() = methods

  override fun findMethodsByName(name: String, checkBases: Boolean): Array<PsiMethod> {
    return allMethods.filter { method -> method.name == name }.toTypedArray()
  }

  private fun computeMethods(): Array<PsiMethod> {
    val thisType = PsiTypesUtil.getClassType(this)

    return action.arguments
      .flatMap { arg ->
        // Create a getter and setter per argument
        val argType = arg.parsePsiType(navInfo.packageName, this)
        val setter =
          createMethod(
              name = "set${arg.name.toUpperCamelCase()}",
              navigationElement = getFieldNavigationElementByName(arg.name),
              returnType = annotateNullability(thisType),
            )
            .addParameter(arg.name.toCamelCase(), argType)

        val getter =
          createMethod(
            name = "get${arg.name.toUpperCamelCase()}",
            navigationElement = getFieldNavigationElementByName(arg.name),
            returnType = annotateNullability(argType, arg.isNonNull()),
          )

        listOf(setter, getter)
      }
      .toTypedArray()
  }

  private fun computeConstructors(): Array<PsiMethod> {
    val privateConstructor =
      createConstructor().apply {
        action.arguments.forEach { arg ->
          if (arg.defaultValue == null) {
            val argType = arg.parsePsiType(navInfo.packageName, this)
            this.addParameter(arg.name.toCamelCase(), argType)
          }
        }
        this.setModifiers(PsiModifier.PRIVATE)
      }

    return arrayOf(privateConstructor)
  }

  private fun computeFields(): Array<PsiField> {
    val destinationId = action.resolveDestination() ?: return emptyArray()
    val targetDestinationTag = backingResourceFile?.findXmlTagById(destinationId)
    return action.arguments
      .asSequence()
      .map { arg ->
        // Since we support args overrides, we first try to locate argument tag within current
        // action. If not found,
        // we search in the target destination tag.
        val targetArgumentTag =
          _navigationElement?.findChildTagElementByNameAttr(SdkConstants.TAG_ARGUMENT, arg.name)
            ?: targetDestinationTag?.findChildTagElementByNameAttr(
              SdkConstants.TAG_ARGUMENT,
              arg.name,
            )
        createField(arg, navInfo.packageName, targetArgumentTag)
      }
      .toList()
      .toTypedArray()
  }

  private fun getFieldNavigationElementByName(name: String): PsiElement? {
    return _fields.firstOrNull { it.name == name }?.navigationElement
  }
}
