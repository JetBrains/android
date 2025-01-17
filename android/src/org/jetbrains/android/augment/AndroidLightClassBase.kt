package org.jetbrains.android.augment

import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.base.MoreObjects
import com.intellij.lang.java.JavaLanguage
import com.intellij.navigation.ItemPresentationProviders
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.HierarchicalMethodSignature
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.ResolveState
import com.intellij.psi.SyntheticElement
import com.intellij.psi.impl.InheritanceImplUtil
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightEmptyImplementsList
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightTypeParameterListBuilder
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.customLibrary
import org.jetbrains.kotlin.idea.base.projectStructure.customSdk
import org.jetbrains.kotlin.idea.base.projectStructure.customSourceRootType

abstract class AndroidLightClassBase
protected constructor(psiManager: PsiManager, modifiers: Iterable<String>) :
  LightElement(psiManager, JavaLanguage.INSTANCE), PsiClass, SyntheticElement {
  private val psiModifierList: LightModifierList =
    LightModifierList(psiManager).apply {
      for (modifier in modifiers) {
        addModifier(modifier)
      }
    }

  /**
   * Sets the forced [ModuleInfo] of the containing [PsiFile] to point to the given [Module], so
   * that the Kotlin IDE plugin knows how to handle this light class.
   */
  protected fun setModuleInfo(module: Module, isTest: Boolean) {
    putUserData(ModuleUtilCore.KEY_MODULE, module)

    // Some scenarios move up to the file level and then attempt to get the module from the file.
    val containingFile = getContainingFile() ?: return
    containingFile.putUserData(ModuleUtilCore.KEY_MODULE, module)
    KotlinRegistrationHelper.setModuleInfo(containingFile, isTest)
  }

  /**
   * Sets the forced [ModuleInfo] of the containing [PsiFile] to point to the given [Library], so
   * that the Kotlin IDE plugin knows how to handle this light class.
   */
  protected fun setModuleInfo(library: Library) {
    putUserData(LIBRARY, library)

    val containingFile = getContainingFile() ?: return
    KotlinRegistrationHelper.setModuleInfo(containingFile, library)
  }

  /**
   * Sets the forced [ModuleInfo] of the containing [PsiFile] to point to the given [Sdk], so that
   * the Kotlin IDE plugin knows how to handle this light class.
   */
  protected fun setModuleInfo(sdk: Sdk) {
    val containingFile = getContainingFile() ?: return
    KotlinRegistrationHelper.setModelInfo(containingFile, sdk)
  }

  override fun checkAdd(element: PsiElement) {
    throw IncorrectOperationException("Cannot add elements to R class")
  }

  override fun add(element: PsiElement): PsiElement? {
    throw IncorrectOperationException()
  }

  override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement? {
    throw IncorrectOperationException()
  }

  override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement? {
    throw IncorrectOperationException()
  }

  override fun isInterface() = false

  override fun isAnnotationType() = false

  override fun isEnum() = false

  override fun getExtendsList(): PsiReferenceList = LightEmptyImplementsList(myManager)

  override fun getImplementsList(): PsiReferenceList = LightEmptyImplementsList(myManager)

  override fun getExtendsListTypes(): Array<PsiClassType> = PsiClassType.EMPTY_ARRAY

  override fun getImplementsListTypes(): Array<PsiClassType> = PsiClassType.EMPTY_ARRAY

  override fun getSuperClass(): PsiClass? = null

  override fun getInterfaces(): Array<PsiClass> = PsiClass.EMPTY_ARRAY

  override fun getSupers(): Array<PsiClass> = PsiClass.EMPTY_ARRAY

  override fun getSuperTypes(): Array<PsiClassType> = PsiClassType.EMPTY_ARRAY

  override fun getFields(): Array<PsiField> = PsiField.EMPTY_ARRAY

  override fun getMethods(): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun getConstructors(): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun getInnerClasses(): Array<PsiClass> = PsiClass.EMPTY_ARRAY

  override fun getInitializers(): Array<PsiClassInitializer> = PsiClassInitializer.EMPTY_ARRAY

  override fun getAllFields(): Array<PsiField> = getFields()

  override fun getAllMethods(): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun getAllInnerClasses(): Array<PsiClass> = getInnerClasses()

  override fun findFieldByName(name: @NonNls String, checkBases: Boolean): PsiField? =
    fields.firstOrNull { it.name == name }

  override fun findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod? =
    null

  override fun findMethodsBySignature(
    patternMethod: PsiMethod,
    checkBases: Boolean,
  ): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

  override fun findMethodsByName(name: @NonNls String, checkBases: Boolean): Array<PsiMethod> =
    methods.filter { it.name == name }.toTypedArray()

  override fun findMethodsAndTheirSubstitutorsByName(
    name: @NonNls String,
    checkBases: Boolean,
  ): MutableList<Pair<PsiMethod, PsiSubstitutor>> = mutableListOf()

  override fun getAllMethodsAndTheirSubstitutors(): MutableList<Pair<PsiMethod?, PsiSubstitutor>> =
    mutableListOf()

  override fun findInnerClassByName(name: @NonNls String, checkBases: Boolean): PsiClass? =
    innerClasses.firstOrNull { it.name == name }

  override fun getLBrace(): PsiElement? = null

  override fun getRBrace(): PsiElement? = null

  override fun getNameIdentifier(): PsiIdentifier? = null

  override fun getScope(): PsiElement? = null

  override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean) =
    InheritanceImplUtil.isInheritor(this, baseClass, checkDeep)

  override fun isInheritorDeep(baseClass: PsiClass, classToByPass: PsiClass?) =
    InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass)

  override fun getVisibleSignatures(): MutableCollection<HierarchicalMethodSignature> =
    mutableListOf()

  override fun setName(name: @NonNls String): PsiElement {
    throw IncorrectOperationException("Cannot change the name of $qualifiedName class")
  }

  override fun getDocComment(): PsiDocComment? = null

  override fun isDeprecated() = false

  override fun hasTypeParameters() = false

  override fun getTypeParameterList(): PsiTypeParameterList =
    LightTypeParameterListBuilder(myManager, language)

  override fun getTypeParameters(): Array<PsiTypeParameter> = PsiTypeParameter.EMPTY_ARRAY

  final override fun getModifierList(): PsiModifierList = psiModifierList

  override fun hasModifierProperty(@PsiModifier.ModifierConstant name: @NonNls String) =
    psiModifierList.hasModifierProperty(name)

  override fun isVisibilitySupported() = true

  override fun getElementIcon(@Iconable.IconFlags flags: Int): Icon =
    PsiClassImplUtil.getClassIcon(flags, this)

  override fun isEquivalentTo(another: PsiElement?) =
    PsiClassImplUtil.isClassEquivalentTo(this, another)

  override fun getUseScope(): SearchScope {
    // For the common case of a public light class, getMemberUseScope below cannot determine the
    // owning module and falls back to using the entire project. Here we compute a more accurate
    // scope, see ResolveScopeManagerImpl#getUseScope.
    val modifierList = psiModifierList
    if (PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PUBLIC) {
      val module = ModuleUtilCore.findModuleForPsiElement(this) // see setModuleInfo.
      if (module != null) {
        return if (this.scopeType == ScopeType.MAIN) {
          GlobalSearchScope.moduleWithDependentsScope(module)
        } else {
          GlobalSearchScope.moduleTestsWithDependentsScope(module)
        }
      }

      val library = getUserData<Library>(LIBRARY)
      if (library != null) {
        val root = ArrayUtil.getFirstElement<VirtualFile?>(library.getFiles(OrderRootType.CLASSES))
        if (root != null) {
          return LibraryScopeCache.getInstance(getProject()).getLibraryUseScope(root)
        }
      }
    }

    return PsiImplUtil.getMemberUseScope(this)
  }

  override fun getPresentation() = ItemPresentationProviders.getItemPresentation(this)

  override fun getContainingFile() = containingClass?.containingFile

  override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE

  override fun processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement,
  ) =
    PsiClassImplUtil.processDeclarationsInClass(
      this,
      processor,
      state,
      null,
      lastParent,
      place,
      PsiUtil.getLanguageLevel(place),
      false,
    )

  override fun toString(): String {
    return MoreObjects.toStringHelper(this).addValue(qualifiedName).toString()
  }

  /**
   * Encapsulates calls to Kotlin IDE plugin to prevent [NoClassDefFoundError] when Kotlin is not
   * installed.
   */
  private object KotlinRegistrationHelper {
    fun setModuleInfo(file: PsiFile, isTest: Boolean) {
      file.customSourceRootType =
        if (isTest) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
    }

    fun setModuleInfo(file: PsiFile, library: Library) {
      file.customLibrary = library
    }

    fun setModelInfo(file: PsiFile, sdk: Sdk) {
      file.customSdk = sdk
    }
  }

  protected open val scopeType: ScopeType
    get() = ScopeType.MAIN

  override fun getResolveScope(): GlobalSearchScope {
    // Some light classes come from libraries not modules. In that case the module can't be found,
    // so fall back to the super class's logic.
    val module = ModuleUtilCore.findModuleForPsiElement(this) ?: return super.getResolveScope()

    val scopeType = this.scopeType
    val moduleResolveScope = module.getModuleSystem().getResolveScope(scopeType)
    val result = Ref.create<GlobalSearchScope>(moduleResolveScope)
    KotlinResolveScopeEnlarger.EP_NAME.forEachExtensionSafe { ext: KotlinResolveScopeEnlarger ->
      ext.getAdditionalResolveScope(module, scopeType.isForTest)?.let {
        result.set(result.get().union(it))
      }
    }

    return result.get()
  }

  companion object {
    private val LIBRARY =
      Key.create<Library>(AndroidLightClassBase::class.java.getName() + ".LIBRARY")
  }
}
