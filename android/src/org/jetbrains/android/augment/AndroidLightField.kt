package org.jetbrains.android.augment

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
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.PsiVariableEx
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class AndroidLightField(
  @Volatile private var _name: String,
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
    val modifiers = mutableListOf(PsiModifier.PUBLIC, PsiModifier.STATIC)
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
    val baseIcon = ElementPresentationUtil.createLayeredIcon(PlatformIcons.FIELD_ICON, this, false)
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon)
  }
}

