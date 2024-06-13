/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.psiclass

import com.android.SdkConstants
import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.idea.databinding.BindingLayout
import com.android.tools.idea.databinding.index.BindingLayoutType
import com.android.tools.idea.databinding.index.VariableData
import com.android.tools.idea.databinding.index.ViewIdData
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil.getFqcn
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil.parsePsiType
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil.resolveViewPsiType
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.psi.light.DeprecatableLightMethodBuilder
import com.android.tools.idea.psi.light.NullabilityLightFieldBuilder
import com.android.utils.TraceUtils
import com.android.utils.TraceUtils.simpleId
import com.google.common.base.MoreObjects
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.ResolveState
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.impl.light.LightField
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.annotations.NonNls

/**
 * In-memory PSI for classes generated from a layout file (or a list of related layout files from
 * different configurations)
 *
 * See also: https://developer.android.com/topic/libraries/data-binding/expressions#binding_data
 *
 * In the case of common, single-config layouts, only a single "Binding" class will be generated.
 * However, if there are multi-config layouts, e.g. "layout" and "layout-land", a base "Binding"
 * class as well as layout-specific implementations, e.g. "BindingImpl", "BindingLandImpl", will be
 * generated.
 */
class LightBindingClass(psiManager: PsiManager, private val config: LightBindingClassConfig) :
  AndroidLightClassBase(psiManager, setOf(PsiModifier.PUBLIC, PsiModifier.FINAL)) {

  private enum class NullabilityType {
    UNSPECIFIED,
    NONNULL,
    NULLABLE;

    val isNullable: Boolean
      get() = this == NULLABLE
  }

  // Create a fake backing file to represent this binding class
  private val backingFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText(
        "${config.className}.java",
        JavaFileType.INSTANCE,
        "// This class is generated on-the-fly by the IDE.",
      )
      .apply {
        (this@apply as PsiJavaFile).packageName = StringUtil.getPackageName(config.qualifiedName)
      }

  private val psiSupers: Array<PsiClass> by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      superClass?.let { arrayOf(it) } ?: PsiClass.EMPTY_ARRAY
    }

  private val psiConstructors: Array<PsiMethod> by
    lazy(LazyThreadSafetyMode.PUBLICATION) { arrayOf(createConstructor()) }

  private val psiAllMethods: Array<PsiMethod> by
    lazy(LazyThreadSafetyMode.PUBLICATION) { (superClass?.allMethods ?: arrayOf()) + methods }

  private val psiMethods: Array<PsiMethod> by
    lazy(LazyThreadSafetyMode.PUBLICATION, ::computeMethods)

  private val psiFields: Array<PsiField> by lazy(LazyThreadSafetyMode.PUBLICATION, ::computeFields)

  private val psiExtendsList: PsiReferenceList by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      val factory = PsiElementFactory.getInstance(project)
      val referenceElementByType = factory.createReferenceElementByType(extendsListTypes[0])
      factory.createReferenceList(arrayOf(referenceElementByType))
    }

  private val psiExtendsListTypes: Array<PsiClassType> by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      arrayOf(PsiType.getTypeByName(config.superName, project, moduleScope))
    }

  init {
    setModuleInfo(config.facet.module, false)
  }

  private fun computeMethods(): Array<PsiMethod> {
    val methods: MutableList<PsiMethod> = mutableListOf()

    createRootOverride(methods)

    for (variableTag in config.variableTags) {
      createVariableMethods(variableTag.first, variableTag.second, methods)
    }

    if (config.shouldGenerateGettersAndStaticMethods()) {
      createStaticMethods(methods)
    }

    return methods.toTypedArray()
  }

  private fun computeFields(): Array<PsiField> {
    val scopedViewIds = config.scopedViewIds
    if (scopedViewIds.isEmpty() || scopedViewIds.values.all(Collection<ViewIdData>::isEmpty)) {
      return PsiField.EMPTY_ARRAY
    }

    val numLayouts = scopedViewIds.keys.size
    if (numLayouts == 1) {
      // In the overwhelmingly common case, there's only a single layout, which means that all the
      // IDs are present in every layout (there's only the one!), so the fields generated for it
      // are always non-null.
      val viewIds = scopedViewIds.values.single()
      return viewIds
        .mapNotNull { viewId: ViewIdData ->
          val typeOverride =
            viewId.typeOverride?.let { typeOverrideStr ->
              parsePsiType(getFqcn(typeOverrideStr), this)
            }
          createPsiField(viewId, true, typeOverride)
        }
        .toTypedArray()
    }

    // Two or more layouts.
    // Generated fields are non-null only if their source IDs are defined consistently across all
    // layouts.
    val dedupedViewIds: MutableSet<ViewIdData> = mutableSetOf() // Only create one field per ID
    val idCounts: MutableMap<String, Int> = mutableMapOf()

    for (viewId in scopedViewIds.values.flatten()) {
      val count = idCounts.compute(viewId.id) { _, value -> if (value == null) 1 else (value + 1) }
      if (count == 1) {
        // It doesn't matter which copy of the ID we keep, so just keep the first one
        dedupedViewIds.add(viewId)
      }
    }

    // If tags have inconsistent IDs, e.g. <TextView ...> in one configuration and <Button ...> in
    // another, the data binding compiler reverts to View
    val inconsistentlyTypedIds: MutableSet<String> = mutableSetOf()
    val idTypes: MutableMap<String, String> = mutableMapOf()
    for ((id, viewName, _, viewTypeOverride) in scopedViewIds.values.flatten()) {
      val viewFqcn = getFqcn(viewTypeOverride ?: viewName)

      val previousViewName = idTypes[id]
      if (previousViewName == null) {
        idTypes[id] = viewFqcn
      } else if (viewFqcn != previousViewName) {
        inconsistentlyTypedIds.add(id)
      }
    }

    return dedupedViewIds
      .mapNotNull { viewId: ViewIdData ->
        val typeOverride =
          if (inconsistentlyTypedIds.contains(viewId.id)) {
            parsePsiType(SdkConstants.CLASS_VIEW, this)
          } else {
            viewId.typeOverride?.let { parsePsiType(getFqcn(it), this) }
          }
        createPsiField(viewId, idCounts[viewId.id] == numLayouts, typeOverride)
      }
      .toTypedArray()
  }

  /** Creates a private no-argument constructor. */
  private fun createConstructor() =
    LightMethodBuilder(this, JavaLanguage.INSTANCE).apply {
      setConstructor(true)
      addModifier(PsiModifier.PRIVATE)
    }

  override fun getQualifiedName() = config.qualifiedName

  override fun getContainingClass(): PsiClass? = null

  override fun getFields() = psiFields

  override fun getAllFields() = getFields()

  override fun getConstructors() = psiConstructors

  override fun getMethods() = psiMethods

  override fun getSupers() = psiSupers

  override fun getSuperClass() =
    JavaPsiFacade.getInstance(project).findClass(config.superName, moduleScope)

  override fun getExtendsList() = psiExtendsList

  override fun getSuperTypes() = getExtendsListTypes()

  override fun getExtendsListTypes() = psiExtendsListTypes

  override fun getAllMethods() = psiAllMethods

  override fun findMethodsByName(name: @NonNls String?, checkBases: Boolean): Array<PsiMethod> {
    val methods = if (checkBases) allMethods else methods
    val matched = methods.filter { it.name == name }
    return if (matched.isEmpty()) PsiMethod.EMPTY_ARRAY else matched.toTypedArray()
  }

  override fun processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement,
  ): Boolean {
    if (!super.processDeclarations(processor, state, lastParent, place)) return false

    val imports = config.targetLayout.data.imports
    if (imports.isEmpty()) return true

    if (
      processor
        .getHint(ElementClassHint.KEY)
        ?.shouldProcess(ElementClassHint.DeclarationKind.CLASS) != true
    )
      return true

    val name = processor.getHint(NameHint.KEY)?.getName(state)
    for ((qName, alias) in imports) {
      if (alias != null) {
        continue // Aliases are pre-resolved.
      }
      if (name != null && !qName.endsWith(name)) {
        continue
      }

      val aClass = JavaPsiFacade.getInstance(project).findClass(qName, moduleScope)
      if (aClass != null && !processor.execute(aClass, state)) {
        return false // Found it.
      }
    }

    return true
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("qualified name", qualifiedName)
      .add("object id", TraceUtils.simpleId)
      .toString()
  }

  override fun equals(other: Any?) =
    this === other || config == (other as? LightBindingClass)?.config

  override fun hashCode() = config.hashCode()

  private val moduleScope: GlobalSearchScope
    get() = config.facet.getModuleSystem().getResolveScope(ScopeType.MAIN)

  /**
   * If applicable, create a `getRoot` method that overrides / specializes the one in the base
   * class.
   *
   * For example, "View getRoot()" in the base interface could be returned as "LinearLayout
   * getRoot()" in this binding class.
   *
   * If this binding is for a layout with multiple configurations that define inconsistent root
   * tags, then "View" will be returned.
   */
  private fun createRootOverride(outPsiMethods: MutableList<PsiMethod>) {
    val xmlFile = config.targetLayout.toXmlFile() ?: return
    val xmlData = config.targetLayout.data

    // For legacy reasons, data binding does not override getRoot with a more specialized return
    // type (e.g. FrameLayout instead of View). Only view binding does this at this time.
    if (
      xmlData.layoutType != BindingLayoutType.PLAIN_LAYOUT || !config.facet.isViewBindingEnabled()
    )
      return

    // Abort if we can't find an actual PSI tag we can navigate to
    val xmlRootTag = xmlFile.rootTag ?: return

    // Note: We don't simply use xmlRootTag's name, since the final return type could be
    // different if root tag names are not consistent across layout configurations.
    val rootTag = config.rootType

    val type = resolveViewPsiType(xmlData, rootTag, this) ?: return
    val rootMethod: LightMethodBuilder = createPublicMethod("getRoot", type)
    outPsiMethods.add(
      LightDataBindingMethod(xmlRootTag, manager, rootMethod, this, JavaLanguage.INSTANCE)
    )
  }

  private fun createVariableMethods(
    variable: VariableData,
    xmlTag: XmlTag,
    outPsiMethods: MutableList<PsiMethod>,
  ) {
    val psiManager = manager

    val typeName = variable.type
    val variableType =
      DataBindingUtil.getQualifiedType(project, typeName, config.targetLayout.data, true) ?: return
    val type = parsePsiType(variableType, xmlTag) ?: return

    val javaName = DataBindingUtil.convertVariableNameToJavaFieldName(variable.name)
    val capitalizedName = StringUtil.capitalize(javaName)
    val setter =
      createPublicMethod("set$capitalizedName", PsiTypes.voidType()).apply {
        addParameter(javaName, type)
        if (config.settersShouldBeAbstract()) addModifier("abstract")
      }
    outPsiMethods.add(
      LightDataBindingMethod(xmlTag, psiManager, setter, this, JavaLanguage.INSTANCE)
    )

    if (config.shouldGenerateGettersAndStaticMethods()) {
      val getter = createPublicMethod("get$capitalizedName", type)
      outPsiMethods.add(
        LightDataBindingMethod(xmlTag, psiManager, getter, this, JavaLanguage.INSTANCE)
      )
    }
  }

  private fun createStaticMethods(outPsiMethods: MutableList<PsiMethod>) {
    val xmlFile = config.targetLayout.toXmlFile() ?: return

    val project = project
    val moduleScope = moduleScope
    val bindingType = PsiElementFactory.getInstance(getProject()).createType(this)
    val viewGroupType = PsiType.getTypeByName(SdkConstants.CLASS_VIEWGROUP, project, moduleScope)
    val inflaterType =
      PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, project, moduleScope)
    val viewType = PsiType.getTypeByName(SdkConstants.CLASS_VIEW, project, moduleScope)
    val dataBindingComponentType = PsiType.getJavaLangObject(manager, moduleScope)

    val methods: MutableList<PsiMethod> = mutableListOf()
    val xmlData = config.targetLayout.data

    // Methods generated for data binding and view binding diverge a little
    if (xmlData.layoutType == BindingLayoutType.DATA_BINDING_LAYOUT) {
      val inflate4Params =
        createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("inflater", inflaterType, true)
          addNullabilityParameter("root", viewGroupType, false)
          addParameter("attachToRoot", PsiTypes.booleanType())
          addNullabilityParameter("bindingComponent", dataBindingComponentType, false)
          // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
          isDeprecated = true
        }

      val inflate3Params =
        createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("inflater", inflaterType, true)
          addNullabilityParameter("root", viewGroupType, false)
          addParameter("attachToRoot", PsiTypes.booleanType())
        }

      val inflate2Params =
        createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("inflater", inflaterType, true)
          addNullabilityParameter("bindingComponent", dataBindingComponentType, false)
          // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
          isDeprecated = true
        }

      val inflate1Param =
        createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("inflater", inflaterType, true)
        }

      val bind =
        createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("view", viewType, true)
        }

      val bindWithComponent =
        createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("view", viewType, true)
          addNullabilityParameter("bindingComponent", dataBindingComponentType, false)
          // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
          isDeprecated = true
        }

      methods.add(inflate1Param)
      methods.add(inflate2Params)
      methods.add(inflate3Params)
      methods.add(inflate4Params)
      methods.add(bind)
      methods.add(bindWithComponent)
    } else {
      // Expected: If not a data binding layout, this is a view binding layout
      assert(
        xmlData.layoutType == BindingLayoutType.PLAIN_LAYOUT && config.facet.isViewBindingEnabled()
      )
      // View Binding is a fresh start - don't show the deprecated methods for them
      if (xmlData.rootTag != SdkConstants.VIEW_MERGE) {
        val inflate3Params =
          createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
            addNullabilityParameter("inflater", inflaterType, true)
            addNullabilityParameter("parent", viewGroupType, false)
            addParameter("attachToParent", PsiTypes.booleanType())
          }

        val inflate1Param =
          createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
            addNullabilityParameter("inflater", inflaterType, true)
          }

        methods.add(inflate1Param)
        methods.add(inflate3Params)
      } else {
        // View Bindings with <merge> roots have a different set of inflate methods
        val inflate2Params =
          createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL).apply {
            addNullabilityParameter("inflater", inflaterType, true)
            addNullabilityParameter("parent", viewGroupType, true)
          }
        methods.add(inflate2Params)
      }

      val bind =
        createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL).apply {
          addNullabilityParameter("view", viewType, true)
        }
      methods.add(bind)
    }

    val psiManager = manager
    for (method in methods) {
      outPsiMethods.add(
        LightDataBindingMethod(xmlFile, psiManager, method, this, JavaLanguage.INSTANCE)
      )
    }
  }

  private fun createPublicStaticMethod(
    name: String,
    returnType: PsiType,
    nullabilityType: NullabilityType,
  ): DeprecatableLightMethodBuilder {
    return createPublicMethod(name, returnType, nullabilityType).apply { addModifier("static") }
  }

  private fun createPublicMethod(
    name: String,
    returnType: PsiType,
    nullabilityType: NullabilityType = NullabilityType.UNSPECIFIED,
  ): DeprecatableLightMethodBuilder {
    return DeprecatableLightMethodBuilder(manager, JavaLanguage.INSTANCE, name).apply {
      setContainingClass(this@LightBindingClass)

      if (nullabilityType == NullabilityType.UNSPECIFIED) {
        setMethodReturnType(returnType)
      } else {
        setMethodReturnType(returnType, !nullabilityType.isNullable)
      }

      addModifier("public")
    }
  }

  private fun createPsiField(
    viewIdData: ViewIdData,
    isNonNull: Boolean,
    typeOverride: PsiType?,
  ): PsiField? {
    val name = DataBindingUtil.convertAndroidIdToJavaFieldName(viewIdData.id)

    val type =
      typeOverride ?: resolveViewPsiType(config.targetLayout.data, viewIdData, this) ?: return null

    val field =
      NullabilityLightFieldBuilder(
        PsiManager.getInstance(project),
        name,
        type,
        isNonNull,
        PsiModifier.PUBLIC,
        PsiModifier.FINAL,
      )
    return LightDataBindingField(config.targetLayout, viewIdData, manager, field, this)
  }

  override fun isInterface() = false

  override fun getNavigationElement(): PsiElement {
    val xmlFile = config.targetLayout.toXmlFile() ?: return super.getNavigationElement()
    return xmlFile.navigationElement ?: xmlFile
  }

  override fun getName() = config.className

  override fun getContainingFile() = backingFile

  override fun isValid() =
    // It is always valid. Not having this valid creates IDE errors because it is not always
    // resolved instantly.
    true

  /**
   * The light method class that represents the generated data binding methods for a layout file.
   */
  class LightDataBindingMethod(
    private val navigationElement: PsiElement,
    manager: PsiManager,
    method: PsiMethod,
    containingClass: PsiClass,
    language: Language,
  ) : LightMethod(manager, method, containingClass, language) {

    override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE

    override fun getNavigationElement() = navigationElement

    override fun getNameIdentifier() = LightIdentifier(manager, name)
  }

  /** The light field class that represents the generated view fields for a layout file. */
  class LightDataBindingField(
    private val layout: BindingLayout,
    private val viewIdData: ViewIdData,
    manager: PsiManager,
    field: PsiField,
    containingClass: PsiClass,
  ) : LightField(manager, field, containingClass) {

    private var navigationTag: XmlTag? = null

    private fun computeTag(): XmlTag? {
      val xmlFile = layout.toXmlFile() ?: return null
      val resultTag = Ref<XmlTag>()
      xmlFile.accept(
        object : XmlRecursiveElementWalkingVisitor() {
          override fun visitXmlTag(tag: XmlTag) {
            super.visitXmlTag(tag)
            val idValue = tag.getAttributeValue(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)
            if (idValue != null && viewIdData.id == stripPrefixFromId(idValue)) {
              resultTag.set(tag)
              stopWalking()
            }
          }
        }
      )
      return resultTag.get()
    }

    override fun getContainingFile() = layout.toXmlFile()

    override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE

    override fun getNavigationElement(): PsiElement {
      if (navigationTag == null) navigationTag = computeTag()
      return navigationTag ?: super.getNavigationElement()
    }

    // This method is called by rename refactoring and has to succeed in order for the refactoring
    // to succeed. There no need to change the name since once the refactoring is complete, this
    // object will be replaced by a new one reflecting the changed source code.
    override fun setName(name: String): PsiElement = this
  }
}
