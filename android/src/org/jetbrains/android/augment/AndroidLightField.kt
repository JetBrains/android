// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.augment

import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.resourceNameToFieldName
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.google.common.base.MoreObjects
import com.intellij.lang.java.JavaLanguage
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.PsiVariableEx
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.ui.IconManager
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

open class AndroidLightField(
  @Volatile protected var _name: String,
  private val myContext: PsiClass,
  private val myType: PsiType,
  fieldModifier: FieldModifier,
  private val myConstantValue: Any?
) : LightElement(myContext.manager, JavaLanguage.INSTANCE), PsiField, PsiVariableEx, NavigationItem {
  /**
   * Possible modifiers for the generated fields. R classes for non-namespaced apps use final fields, all other R classes don't.
   */
  enum class FieldModifier {
    FINAL,
    NON_FINAL
  }

  @Volatile private var _initializer: PsiExpression? = null
  private val _modifierList: LightModifierList

  init {
    // Declared abstract to exclude from the implementation search.
    val modifiers = mutableListOf(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.ABSTRACT)
    if (fieldModifier == FieldModifier.FINAL) {
      modifiers += PsiModifier.FINAL
    }
    _modifierList = LightModifierList(manager, language, *modifiers.toTypedArray())
  }

  override fun isEquivalentTo(another: PsiElement) = PsiClassImplUtil.isFieldEquivalentTo(this, another)
  override fun getParent() = myContext
  override fun getContainingFile(): PsiFile? = myContext.containingFile
  override fun computeConstantValue(visitedVars: Set<PsiVariable>) = computeConstantValue()
  override fun computeConstantValue() = myConstantValue
  override fun getContainingClass() = myContext
  override fun toString() = "AndroidLightField:$_name"
  override fun getType() = myType
  override fun getModifierList() = _modifierList
  override fun hasModifierProperty(@NonNls name: String) = _modifierList.hasModifierProperty(name)
  override fun getInitializer() = _initializer
  override fun setInitializer(initializer: PsiExpression?) { _initializer = initializer }
  override fun getNameIdentifier() = LightIdentifier(manager, _name)
  override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE
  override fun getTypeElement(): PsiTypeElement? = null
  override fun hasInitializer() = false
  override fun isVisibilitySupported() = true
  override fun normalizeDeclaration() {}
  override fun getDocComment(): PsiDocComment? = null
  override fun isDeprecated() = false
  override fun getName() = _name

  override fun setName(name: String): PsiElement {
    _name = name
    return this
  }

  public override fun getElementIcon(flags: Int): Icon? {
    val baseIcon = IconManager.getInstance().createLayeredIcon(this, IconManager.getInstance().getPlatformIcon(
      com.intellij.ui.PlatformIcons.Field), ElementPresentationUtil.getFlags(this, false))
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon)
  }
}

class ResourceLightField(
  resourceName: String,
  myContext: PsiClass,
  myType: PsiType,
  fieldModifier: FieldModifier,
  myConstantValue: Any?,
  val resourceVisibility: ResourceVisibility
) : AndroidLightField(resourceName, myContext, myType, fieldModifier, myConstantValue) {

  override fun getNameIdentifier(): LightIdentifier = LightIdentifier(manager, resourceNameToFieldName(_name))
  override fun getName(): String = resourceNameToFieldName(_name)
  override fun toString(): String = "ResourceLightField:$_name"

  val resourceName: String get() = super._name
  val resourceType: ResourceType get() = ResourceType.fromClassName(containingClass.name!!)!!
}

class ManifestLightField(
  name: String,
  myContext: PsiClass,
  myType: PsiType,
  fieldModifier: FieldModifier,
  myConstantValue: Any?) : AndroidLightField(name, myContext, myType, fieldModifier, myConstantValue) {
  override fun toString(): String = "ManifestLightField:$_name"
}

/**
 * Subclass of [AndroidLightField] to store extra information specific to styleable attribute fields.
 */
class StyleableAttrLightField(
  val styleableAttrFieldUrl: StyleableAttrFieldUrl,
  myContext: PsiClass,
  fieldModifier: FieldModifier,
  myConstantValue: Any?
) : AndroidLightField(styleableAttrFieldUrl.toFieldName(), myContext, PsiTypes.intType(), fieldModifier, myConstantValue) {

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("styleable", styleableAttrFieldUrl.styleable)
      .add("attr", styleableAttrFieldUrl.attr)
      .toString()
  }
}

data class StyleableAttrFieldUrl(val styleable: ResourceReference, val attr: ResourceReference) {
  fun toFieldName(): String {
    val packageName = attr.namespace.packageName
    return if (styleable.namespace == attr.namespace || packageName.isNullOrEmpty()) {
      "${resourceNameToFieldName(styleable.name)}_${resourceNameToFieldName(attr.name)}"
    } else {
      "${resourceNameToFieldName(styleable.name)}_${resourceNameToFieldName(packageName)}_${resourceNameToFieldName(attr.name)}"
    }
  }
}
