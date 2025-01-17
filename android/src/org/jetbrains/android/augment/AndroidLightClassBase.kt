package org.jetbrains.android.augment

import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.base.MoreObjects
import com.intellij.lang.java.JavaLanguage
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProviders
import com.intellij.openapi.extensions.ExtensionPointName.forEachExtensionSafe
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
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.customLibrary
import org.jetbrains.kotlin.idea.base.projectStructure.customSdk
import org.jetbrains.kotlin.idea.base.projectStructure.customSourceRootType
import java.util.function.Consumer
import javax.swing.Icon

abstract class AndroidLightClassBase protected constructor(psiManager: PsiManager, modifiers: MutableCollection<String>) :
  LightElement(psiManager, JavaLanguage.INSTANCE), PsiClass, SyntheticElement {
  private val myPsiModifierList: LightModifierList

  init {
    myPsiModifierList = LightModifierList(psiManager)
    for (modifier in modifiers) {
      myPsiModifierList.addModifier(modifier)
    }
  }

  /**
   * Sets the forced [ModuleInfo] of the containing [PsiFile] to point to the given [Module], so that the Kotlin IDE
   * plugin knows how to handle this light class.
   */
  protected fun setModuleInfo(module: Module, isTest: Boolean) {
    this.putUserData<Module?>(ModuleUtilCore.KEY_MODULE, module)
    // Some scenarios move up to the file level and then attempt to get the module from the file.
    val containingFile = getContainingFile()
    if (containingFile != null) {
      containingFile.putUserData<Module?>(ModuleUtilCore.KEY_MODULE, module)
      KotlinRegistrationHelper.setModuleInfo(containingFile, isTest)
    }
  }

  /**
   * Sets the forced [ModuleInfo] of the containing [PsiFile] to point to the given [Library], so that the Kotlin IDE
   * plugin knows how to handle this light class.
   */
  protected fun setModuleInfo(library: Library) {
    putUserData<Library?>(LIBRARY, library)

    val containingFile = getContainingFile()
    if (containingFile != null) {
      KotlinRegistrationHelper.setModuleInfo(containingFile, library)
    }
  }

  /**
   * Sets the forced [ModuleInfo] of the containing [PsiFile] to point to the given [Sdk], so that the Kotlin IDE
   * plugin knows how to handle this light class.
   */
  protected fun setModuleInfo(sdk: Sdk) {
    val containingFile = getContainingFile()
    if (containingFile != null) {
      KotlinRegistrationHelper.setModelInfo(containingFile, sdk)
    }
  }

  @Throws(IncorrectOperationException::class)
  override fun checkAdd(element: PsiElement) {
    throw IncorrectOperationException("Cannot add elements to R class")
  }

  @Throws(IncorrectOperationException::class)
  override fun add(element: PsiElement): PsiElement? {
    throw IncorrectOperationException()
  }

  @Throws(IncorrectOperationException::class)
  override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement? {
    throw IncorrectOperationException()
  }

  @Throws(IncorrectOperationException::class)
  override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement? {
    throw IncorrectOperationException()
  }

  override fun isInterface(): Boolean {
    return false
  }

  override fun isAnnotationType(): Boolean {
    return false
  }

  override fun isEnum(): Boolean {
    return false
  }

  override fun getExtendsList(): PsiReferenceList? {
    return LightEmptyImplementsList(myManager)
  }

  override fun getImplementsList(): PsiReferenceList? {
    return LightEmptyImplementsList(myManager)
  }

  override fun getExtendsListTypes(): Array<PsiClassType?> {
    return PsiClassType.EMPTY_ARRAY
  }

  override fun getImplementsListTypes(): Array<PsiClassType?> {
    return PsiClassType.EMPTY_ARRAY
  }

  override fun getSuperClass(): PsiClass? {
    return null
  }

  override fun getInterfaces(): Array<PsiClass?> {
    return PsiClass.EMPTY_ARRAY
  }

  override fun getSupers(): Array<PsiClass?> {
    return PsiClass.EMPTY_ARRAY
  }

  override fun getSuperTypes(): Array<PsiClassType?> {
    return PsiClassType.EMPTY_ARRAY
  }

  override fun getFields(): Array<PsiField> {
    return PsiField.EMPTY_ARRAY
  }

  override fun getMethods(): Array<PsiMethod?> {
    return PsiMethod.EMPTY_ARRAY
  }

  override fun getConstructors(): Array<PsiMethod?> {
    return PsiMethod.EMPTY_ARRAY
  }

  override fun getInnerClasses(): Array<PsiClass?> {
    return PsiClass.EMPTY_ARRAY
  }

  override fun getInitializers(): Array<PsiClassInitializer?> {
    return PsiClassInitializer.EMPTY_ARRAY
  }

  override fun getAllFields(): Array<PsiField> {
    return getFields()
  }

  override fun getAllMethods(): Array<PsiMethod?> {
    return PsiMethod.EMPTY_ARRAY
  }

  override fun getAllInnerClasses(): Array<PsiClass?> {
    return getInnerClasses()
  }

  override fun findFieldByName(name: @NonNls String, checkBases: Boolean): PsiField? {
    val fields = getFields()
    for (field in fields) {
      if (name == field.getName()) return field
    }
    return null
  }

  override fun findMethodBySignature(patternMethod: PsiMethod?, checkBases: Boolean): PsiMethod? {
    return null
  }

  override fun findMethodsBySignature(patternMethod: PsiMethod?, checkBases: Boolean): Array<PsiMethod?> {
    return PsiMethod.EMPTY_ARRAY
  }

  override fun findMethodsByName(name: @NonNls String?, checkBases: Boolean): Array<PsiMethod?> {
    val methods: MutableList<PsiMethod?> = ArrayList<PsiMethod?>()
    for (method in getMethods()) {
      if (method!!.getName() == name) {
        methods.add(method)
      }
    }
    return if (methods.isEmpty()) PsiMethod.EMPTY_ARRAY else methods.toArray<PsiMethod?>(PsiMethod.EMPTY_ARRAY)
  }

  override fun findMethodsAndTheirSubstitutorsByName(
    name: @NonNls String?,
    checkBases: Boolean
  ): MutableList<Pair<PsiMethod?, PsiSubstitutor?>?> {
    return mutableListOf<Pair<PsiMethod?, PsiSubstitutor?>?>()
  }

  override fun getAllMethodsAndTheirSubstitutors(): MutableList<Pair<PsiMethod?, PsiSubstitutor?>?> {
    return mutableListOf<Pair<PsiMethod?, PsiSubstitutor?>?>()
  }

  override fun findInnerClassByName(name: @NonNls String, checkBases: Boolean): PsiClass? {
    for (aClass in getInnerClasses()) {
      if (name == aClass!!.getName()) {
        return aClass
      }
    }
    return null
  }

  override fun getLBrace(): PsiElement? {
    return null
  }

  override fun getRBrace(): PsiElement? {
    return null
  }

  override fun getNameIdentifier(): PsiIdentifier? {
    return null
  }

  override fun getScope(): PsiElement? {
    return null
  }

  override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep)
  }

  override fun isInheritorDeep(baseClass: PsiClass, classToByPass: PsiClass?): Boolean {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass)
  }

  override fun getVisibleSignatures(): MutableCollection<HierarchicalMethodSignature?> {
    return mutableListOf<HierarchicalMethodSignature?>()
  }

  @Throws(IncorrectOperationException::class)
  override fun setName(name: @NonNls String): PsiElement? {
    throw IncorrectOperationException("Cannot change the name of " + getQualifiedName() + " class")
  }

  override fun getDocComment(): PsiDocComment? {
    return null
  }

  override fun isDeprecated(): Boolean {
    return false
  }

  override fun hasTypeParameters(): Boolean {
    return false
  }

  override fun getTypeParameterList(): PsiTypeParameterList? {
    return LightTypeParameterListBuilder(myManager, getLanguage())
  }

  override fun getTypeParameters(): Array<PsiTypeParameter?> {
    return PsiTypeParameter.EMPTY_ARRAY
  }

  override fun getModifierList(): PsiModifierList {
    return myPsiModifierList
  }

  override fun hasModifierProperty(@PsiModifier.ModifierConstant name: @NonNls String): Boolean {
    val list = getModifierList()
    return list != null && list.hasModifierProperty(name)
  }

  override fun isVisibilitySupported(): Boolean {
    return true
  }

  override fun getElementIcon(@Iconable.IconFlags flags: Int): Icon? {
    return PsiClassImplUtil.getClassIcon(flags, this)
  }

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return PsiClassImplUtil.isClassEquivalentTo(this, another)
  }

  override fun getUseScope(): SearchScope {
    // For the common case of a public light class, getMemberUseScope below cannot determine the owning module and falls back to using the
    // entire project. Here we compute a more accurate scope, see ResolveScopeManagerImpl#getUseScope.
    val modifierList = getModifierList()
    if (modifierList != null) {
      if (PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PUBLIC) {
        val module = ModuleUtilCore.findModuleForPsiElement(this) // see setModuleInfo.
        if (module != null) {
          if (this.scopeType == ScopeType.MAIN) {
            return GlobalSearchScope.moduleWithDependentsScope(module)
          } else {
            return GlobalSearchScope.moduleTestsWithDependentsScope(module)
          }
        }

        val library = getUserData<Library?>(LIBRARY)
        if (library != null) {
          val root = ArrayUtil.getFirstElement<VirtualFile?>(library.getFiles(OrderRootType.CLASSES))
          if (root != null) {
            return LibraryScopeCache.getInstance(getProject()).getLibraryUseScope(root)
          }
        }
      }
    }

    return PsiImplUtil.getMemberUseScope(this)
  }

  override fun getPresentation(): ItemPresentation? {
    return ItemPresentationProviders.getItemPresentation(this)
  }

  override fun getContainingFile(): PsiFile? {
    val containingClass = getContainingClass()
    return if (containingClass == null) null else containingClass.getContainingFile()
  }

  override fun getTextRange(): TextRange? {
    return TextRange.EMPTY_RANGE
  }

  override fun processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
  ): Boolean {
    return PsiClassImplUtil.processDeclarationsInClass(
      this,
      processor,
      state,
      null,
      lastParent,
      place,
      PsiUtil.getLanguageLevel(place),
      false
    )
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this).addValue(getQualifiedName()).toString()
  }

  /**
   * Encapsulates calls to Kotlin IDE plugin to prevent [NoClassDefFoundError] when Kotlin is not installed.
   */
  private object KotlinRegistrationHelper {
    fun setModuleInfo(file: PsiFile, isTest: Boolean) {
      file.customSourceRootType = if (isTest) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
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
    val module = ModuleUtilCore.findModuleForPsiElement(this)
    if (module == null) {
      // Some light classes come from libraries not modules.
      return super.getResolveScope()
    }

    val scopeType = this.scopeType
    val moduleResolveScope = module.getModuleSystem().getResolveScope(scopeType)
    val result = Ref.create<GlobalSearchScope?>(moduleResolveScope)
    KotlinResolveScopeEnlarger.Companion.getEP_NAME().forEachExtensionSafe(Consumer { enlarger: KotlinResolveScopeEnlarger? ->
      val additionalResolveScope = enlarger!!.getAdditionalResolveScope(module, scopeType.isForTest)
      if (additionalResolveScope != null) {
        result.set(result.get().union(additionalResolveScope))
      }
    })
    return result.get()
  }

  companion object {
    private val LIBRARY = Key.create<Library?>(AndroidLightClassBase::class.java.getName() + ".LIBRARY")
  }
}
