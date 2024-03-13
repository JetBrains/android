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
import com.android.tools.idea.psi.light.NullabilityLightMethodBuilder
import com.android.utils.TraceUtils
import com.android.utils.TraceUtils.simpleId
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ObjectArrays
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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiIdentifier
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
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTag
import java.util.Objects
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
class LightBindingClass(psiManager: PsiManager, private val myConfig: LightBindingClassConfig) :
  AndroidLightClassBase(
    psiManager,
    ImmutableSet.of<String>(PsiModifier.PUBLIC, PsiModifier.FINAL),
  ) {
  private enum class NullabilityType {
    UNSPECIFIED,
    NONNULL,
    NULLABLE;

    val isNullable: Boolean
      get() = this == NULLABLE
  }

  private val myBackingFile: PsiJavaFile

  private var myPsiSupers: Array<PsiClass>? // Created lazily
  private var myPsiConstructors: Array<PsiMethod>? // Created lazily
  private var myPsiAllMethods: Array<PsiMethod> // Created lazily
  private var myPsiMethods: Array<PsiMethod> // Created lazily
  private var myPsiFields: Array<PsiField> // Created lazily
  private var myExtendsList: PsiReferenceList? = null // Created lazily
  private var myExtendsListTypes: Array<PsiClassType>? // Created lazily

  init {
    // Create a fake backing file to represent this binding class
    val fileFactory = PsiFileFactory.getInstance(project)
    myBackingFile =
      fileFactory.createFileFromText(
        myConfig.className + ".java",
        JavaFileType.INSTANCE,
        "// This class is generated on-the-fly by the IDE.",
      ) as PsiJavaFile
    myBackingFile.packageName = StringUtil.getPackageName(myConfig.qualifiedName)

    setModuleInfo(myConfig.facet.module, false)
  }

  private fun computeMethods(): Array<PsiMethod> {
    val methods: MutableList<PsiMethod> = ArrayList()

    createRootOverride(methods)

    for (variableTag in myConfig.variableTags) {
      createVariableMethods(variableTag, methods)
    }

    if (myConfig.shouldGenerateGettersAndStaticMethods()) {
      createStaticMethods(methods)
    }

    return methods.toArray(PsiMethod.EMPTY_ARRAY)
  }

  private fun computeFields(): Array<PsiField> {
    val scopedViewIds = myConfig.scopedViewIds
    if (scopedViewIds.isEmpty()) {
      return PsiField.EMPTY_ARRAY
    }
    var allEmpty = true
    for (viewIds in scopedViewIds.values) {
      if (!viewIds.isEmpty()) {
        allEmpty = false
        break
      }
    }
    if (allEmpty) {
      return PsiField.EMPTY_ARRAY
    }

    val computed: Array<PsiField>

    val numLayouts = scopedViewIds.keys.size
    if (numLayouts == 1) {
      // In the overwhelmingly common case, there's only a single layout, which means that all the
      // IDs are present in every layout (there's only the one!), so the fields generated for it
      // are always non-null.
      val viewIds = scopedViewIds.values.stream().findFirst().get()
      computed =
        viewIds
          .stream()
          .map<PsiField?> { viewId: ViewIdData ->
            var typeOverride: PsiType? = null
            var typeOverrideStr = viewId.typeOverride
            if (typeOverrideStr != null) {
              typeOverrideStr = getFqcn(typeOverrideStr)
              typeOverride = parsePsiType(typeOverrideStr, this)
            }
            createPsiField(viewId, true, typeOverride)
          }
          .filter { obj: PsiField? -> Objects.nonNull(obj) }
          .toArray<PsiField> { _Dummy_.__Array__() }
    } else { // Two or more layouts.
      // Generated fields are non-null only if their source IDs are defined consistently across all
      // layouts.
      val dedupedViewIds: MutableSet<ViewIdData> = HashSet() // Only create one field per ID
      val idCounts: MutableMap<String, Int> = HashMap()
      run {
        for (viewIds in scopedViewIds.values) {
          for (viewId in viewIds) {
            val count =
              idCounts.compute(viewId.id) { key: String?, value: Int? ->
                if ((value == null)) 1 else (value + 1)
              }!!
            if (count == 1) {
              // It doesn't matter which copy of the ID we keep, so just keep the first one
              dedupedViewIds.add(viewId)
            }
          }
        }
      }

      // If tags have inconsitent IDs, e.g. <TextView ...> in one configuration and <Button ...> in
      // another,
      // the databinding compiler reverts to View
      val inconsistentlyTypedIds: MutableSet<String> = HashSet()
      run {
        val idTypes: MutableMap<String, String> = HashMap()
        for (viewIds in scopedViewIds.values) {
          for ((id, viewName, _, viewTypeOverride) in viewIds) {
            var viewFqcn = getFqcn(viewName)
            if (viewTypeOverride != null) {
              viewFqcn = getFqcn(viewTypeOverride)
            }

            val previousViewName = idTypes[id]
            if (previousViewName == null) {
              idTypes[id] = viewFqcn
            } else {
              if (viewFqcn != previousViewName) {
                inconsistentlyTypedIds.add(id)
              }
            }
          }
        }
      }

      computed =
        dedupedViewIds
          .stream()
          .map<PsiField?> { viewId: ViewIdData ->
            var typeOverride: PsiType? = null
            var typeOverrideStr = viewId.typeOverride
            if (inconsistentlyTypedIds.contains(viewId.id)) {
              typeOverride = parsePsiType(SdkConstants.CLASS_VIEW, this)
            } else if (typeOverrideStr != null) {
              typeOverrideStr = getFqcn(typeOverrideStr!!)
              typeOverride = parsePsiType(typeOverrideStr!!, this)
            }
            createPsiField(viewId, idCounts[viewId.id] == numLayouts, typeOverride)
          }
          .filter { obj: PsiField? -> Objects.nonNull(obj) }
          .toArray<PsiField> { _Dummy_.__Array__() }
    }

    return computed
  }

  /** Creates a private no-argument constructor. */
  private fun createConstructor(): PsiMethod {
    val constructor = LightMethodBuilder(this, JavaLanguage.INSTANCE)
    constructor.setConstructor(true)
    constructor.addModifier(PsiModifier.PRIVATE)
    return constructor
  }

  override fun getQualifiedName(): String {
    return myConfig.qualifiedName
  }

  override fun getContainingClass(): PsiClass? {
    return null
  }

  override fun getFields(): Array<PsiField> {
    if (myPsiFields == null) {
      myPsiFields = computeFields()
    }
    return myPsiFields
  }

  override fun getAllFields(): Array<PsiField> {
    return fields
  }

  override fun getConstructors(): Array<PsiMethod> {
    if (myPsiConstructors == null) {
      myPsiConstructors = arrayOf(createConstructor())
    }
    return myPsiConstructors!!
  }

  override fun getMethods(): Array<PsiMethod> {
    if (myPsiMethods == null) {
      myPsiMethods = computeMethods()
    }
    return myPsiMethods
  }

  override fun getSupers(): Array<PsiClass> {
    if (myPsiSupers == null) {
      val superClass = superClass
      myPsiSupers =
        if (superClass != null) {
          arrayOf(superClass)
        } else {
          // superClass shouldn't be null but we handle just in case
          PsiClass.EMPTY_ARRAY
        }
    }
    return myPsiSupers!!
  }

  override fun getSuperClass(): PsiClass? {
    return JavaPsiFacade.getInstance(project).findClass(myConfig.superName, moduleScope)
  }

  override fun getExtendsList(): PsiReferenceList? {
    if (myExtendsList == null) {
      val factory = PsiElementFactory.getInstance(project)
      val referenceElementByType = factory.createReferenceElementByType(extendsListTypes[0])
      myExtendsList = factory.createReferenceList(arrayOf(referenceElementByType))
    }
    return myExtendsList
  }

  override fun getSuperTypes(): Array<PsiClassType> {
    return extendsListTypes
  }

  override fun getExtendsListTypes(): Array<PsiClassType> {
    if (myExtendsListTypes == null) {
      myExtendsListTypes = arrayOf(PsiType.getTypeByName(myConfig.superName, project, moduleScope))
    }
    return myExtendsListTypes!!
  }

  override fun getAllMethods(): Array<PsiMethod> {
    if (myPsiAllMethods == null) {
      val superClass = superClass
      myPsiAllMethods =
        if (superClass != null) {
          ObjectArrays.concat(superClass.allMethods, methods, PsiMethod::class.java)
        } else {
          // superClass shouldn't be null but we handle just in case
          methods
        }
    }

    return myPsiAllMethods
  }

  override fun findMethodsByName(name: @NonNls String?, checkBases: Boolean): Array<PsiMethod> {
    var matched: MutableList<PsiMethod?>? = null
    val methods = if (checkBases) allMethods else methods
    for (method in methods) {
      if (name == method.name) {
        if (matched == null) {
          matched = ArrayList()
        }
        matched.add(method)
      }
    }
    return if (matched == null) PsiMethod.EMPTY_ARRAY else matched.toArray(PsiMethod.EMPTY_ARRAY)
  }

  override fun processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement,
  ): Boolean {
    val continueProcessing = super.processDeclarations(processor, state, lastParent, place)
    if (!continueProcessing) {
      return false
    }
    val imports = myConfig.targetLayout.data.imports
    if (imports.isEmpty()) {
      return true
    }
    val classHint = processor.getHint(ElementClassHint.KEY)
    if (classHint != null && classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      val nameHint = processor.getHint(NameHint.KEY)
      val name = nameHint?.getName(state)
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
    }
    return true
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("qualified name", qualifiedName)
      .add("object id", TraceUtils.simpleId)
      .toString()
  }

  override fun equals(obj: Any?): Boolean {
    if (this === obj) {
      return true
    }

    if (obj !is LightBindingClass) {
      return false
    }

    return myConfig == obj.myConfig
  }

  override fun hashCode(): Int {
    return myConfig.hashCode()
  }

  private val moduleScope: GlobalSearchScope
    get() = myConfig.facet.getModuleSystem().getResolveScope(ScopeType.MAIN)

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
    val xmlFile = myConfig.targetLayout.toXmlFile() ?: return

    val xmlData = myConfig.targetLayout.data
    // For legacy reasons, data binding does not override getRoot with a more specialized return
    // type (e.g. FrameLayout instead of View). Only view binding does this at this time.
    if (
      xmlData.layoutType == BindingLayoutType.PLAIN_LAYOUT && myConfig.facet.isViewBindingEnabled()
    ) {
      val xmlRootTag =
        xmlFile.rootTag ?: return // Abort if we can't find an actual PSI tag we can navigate to

      // Note: We don't simply use xmlRootTag's name, since the final return type could be
      // different if root tag names are not consistent across layout configurations.
      val rootTag = myConfig.rootType

      val type = resolveViewPsiType(xmlData, rootTag, this)
      if (type != null) {
        val rootMethod: LightMethodBuilder = createPublicMethod("getRoot", type)
        outPsiMethods.add(
          LightDataBindingMethod(xmlRootTag, manager, rootMethod, this, JavaLanguage.INSTANCE)
        )
      }
    }
  }

  private fun createVariableMethods(
    variableTag: Pair<VariableData, XmlTag>,
    outPsiMethods: MutableList<PsiMethod>,
  ) {
    val psiManager = manager

    val variable = variableTag.first
    val xmlTag = variableTag.second

    val typeName = variable.type
    val variableType =
      DataBindingUtil.getQualifiedType(project, typeName, myConfig.targetLayout.data, true)
        ?: return
    val type = parsePsiType(variableType, xmlTag) ?: return

    val javaName = DataBindingUtil.convertVariableNameToJavaFieldName(variable.name)
    val capitalizedName = StringUtil.capitalize(javaName)
    val setter: LightMethodBuilder = createPublicMethod("set$capitalizedName", PsiTypes.voidType())
    setter.addParameter(javaName, type)
    if (myConfig.settersShouldBeAbstract()) {
      setter.addModifier("abstract")
    }
    outPsiMethods.add(
      LightDataBindingMethod(xmlTag, psiManager, setter, this, JavaLanguage.INSTANCE)
    )

    if (myConfig.shouldGenerateGettersAndStaticMethods()) {
      val getter: LightMethodBuilder = createPublicMethod("get$capitalizedName", type)
      outPsiMethods.add(
        LightDataBindingMethod(xmlTag, psiManager, getter, this, JavaLanguage.INSTANCE)
      )
    }
  }

  private fun createStaticMethods(outPsiMethods: MutableList<PsiMethod>) {
    val xmlFile = myConfig.targetLayout.toXmlFile() ?: return

    val project = project
    val moduleScope = moduleScope
    val bindingType = PsiElementFactory.getInstance(getProject()).createType(this)
    val viewGroupType = PsiType.getTypeByName(SdkConstants.CLASS_VIEWGROUP, project, moduleScope)
    val inflaterType =
      PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, project, moduleScope)
    val viewType = PsiType.getTypeByName(SdkConstants.CLASS_VIEW, project, moduleScope)
    val dataBindingComponentType = PsiType.getJavaLangObject(manager, moduleScope)

    val methods: MutableList<PsiMethod> = ArrayList()
    val xmlData = myConfig.targetLayout.data

    // Methods generated for data binding and view binding diverge a little
    if (xmlData.layoutType == BindingLayoutType.DATA_BINDING_LAYOUT) {
      val inflate4Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
      inflate4Params.addNullabilityParameter("inflater", inflaterType, true)
      inflate4Params.addNullabilityParameter("root", viewGroupType, false)
      inflate4Params.addParameter("attachToRoot", PsiTypes.booleanType())
      inflate4Params.addNullabilityParameter("bindingComponent", dataBindingComponentType, false)
      // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
      inflate4Params.isDeprecated = true

      val inflate3Params: NullabilityLightMethodBuilder =
        createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
      inflate3Params.addNullabilityParameter("inflater", inflaterType, true)
      inflate3Params.addNullabilityParameter("root", viewGroupType, false)
      inflate3Params.addParameter("attachToRoot", PsiTypes.booleanType())

      val inflate2Params = createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
      inflate2Params.addNullabilityParameter("inflater", inflaterType, true)
      inflate2Params.addNullabilityParameter("bindingComponent", dataBindingComponentType, false)
      // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
      inflate2Params.isDeprecated = true

      val inflate1Param: NullabilityLightMethodBuilder =
        createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
      inflate1Param.addNullabilityParameter("inflater", inflaterType, true)

      val bind: NullabilityLightMethodBuilder =
        createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL)
      bind.addNullabilityParameter("view", viewType, true)

      val bindWithComponent = createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL)
      bindWithComponent.addNullabilityParameter("view", viewType, true)
      bindWithComponent.addNullabilityParameter("bindingComponent", dataBindingComponentType, false)
      // Methods receiving DataBindingComponent are deprecated. see: b/116541301.
      bindWithComponent.isDeprecated = true

      methods.add(inflate1Param)
      methods.add(inflate2Params)
      methods.add(inflate3Params)
      methods.add(inflate4Params)
      methods.add(bind)
      methods.add(bindWithComponent)
    } else {
      // Expected: If not a data binding layout, this is a view binding layout
      assert(
        xmlData.layoutType == BindingLayoutType.PLAIN_LAYOUT &&
          myConfig.facet.isViewBindingEnabled()
      )
      // View Binding is a fresh start - don't show the deprecated methods for them
      if (xmlData.rootTag != SdkConstants.VIEW_MERGE) {
        val inflate3Params: NullabilityLightMethodBuilder =
          createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
        inflate3Params.addNullabilityParameter("inflater", inflaterType, true)
        inflate3Params.addNullabilityParameter("parent", viewGroupType, false)
        inflate3Params.addParameter("attachToParent", PsiTypes.booleanType())

        val inflate1Param: NullabilityLightMethodBuilder =
          createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
        inflate1Param.addNullabilityParameter("inflater", inflaterType, true)

        methods.add(inflate1Param)
        methods.add(inflate3Params)
      } else {
        // View Bindings with <merge> roots have a different set of inflate methods
        val inflate2Params: NullabilityLightMethodBuilder =
          createPublicStaticMethod("inflate", bindingType, NullabilityType.NONNULL)
        inflate2Params.addNullabilityParameter("inflater", inflaterType, true)
        inflate2Params.addNullabilityParameter("parent", viewGroupType, true)
        methods.add(inflate2Params)
      }

      val bind: NullabilityLightMethodBuilder =
        createPublicStaticMethod("bind", bindingType, NullabilityType.NONNULL)
      bind.addNullabilityParameter("view", viewType, true)
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
    val method = createPublicMethod(name, returnType, nullabilityType)
    method.addModifier("static")
    return method
  }

  private fun createPublicMethod(
    name: String,
    returnType: PsiType,
    nullabilityType: NullabilityType = NullabilityType.UNSPECIFIED,
  ): DeprecatableLightMethodBuilder {
    val method = DeprecatableLightMethodBuilder(manager, JavaLanguage.INSTANCE, name)
    method.setContainingClass(this)
    if (nullabilityType == NullabilityType.UNSPECIFIED) {
      method.setMethodReturnType(returnType)
    } else {
      method.setMethodReturnType(returnType, !nullabilityType.isNullable())
    }
    method.addModifier("public")
    return method
  }

  private fun createPsiField(
    viewIdData: ViewIdData,
    isNonNull: Boolean,
    typeOverride: PsiType?,
  ): PsiField? {
    val name = DataBindingUtil.convertAndroidIdToJavaFieldName(viewIdData.id)

    val type: PsiType?
    if (typeOverride == null) {
      type = resolveViewPsiType(myConfig.targetLayout.data, viewIdData, this)
      if (type == null) {
        return null
      }
    } else {
      type = typeOverride
    }

    val field: LightFieldBuilder =
      NullabilityLightFieldBuilder(
        PsiManager.getInstance(project),
        name,
        type,
        isNonNull,
        PsiModifier.PUBLIC,
        PsiModifier.FINAL,
      )
    return LightDataBindingField(myConfig.targetLayout, viewIdData, manager, field, this)
  }

  override fun isInterface(): Boolean {
    return false
  }

  override fun getNavigationElement(): PsiElement {
    val xmlFile = myConfig.targetLayout.toXmlFile() ?: return super.getNavigationElement()
    val xmlNavElement = xmlFile.navigationElement
    return xmlNavElement ?: xmlFile
  }

  override fun getName(): String {
    return myConfig.className
  }

  override fun getContainingFile(): PsiFile? {
    return myBackingFile
  }

  override fun isValid(): Boolean {
    // It is always valid. Not having this valid creates IDE errors because it is not always
    // resolved instantly.
    return true
  }

  /**
   * The light method class that represents the generated data binding methods for a layout file.
   */
  class LightDataBindingMethod(
    private val myNavigationElement: PsiElement,
    manager: PsiManager,
    method: PsiMethod,
    containingClass: PsiClass,
    language: Language,
  ) : LightMethod(manager, method, containingClass, language) {
    override fun getTextRange(): TextRange {
      return TextRange.EMPTY_RANGE
    }

    override fun getNavigationElement(): PsiElement {
      return myNavigationElement
    }

    override fun getNameIdentifier(): PsiIdentifier? {
      return LightIdentifier(manager, name)
    }
  }

  /** The light field class that represents the generated view fields for a layout file. */
  class LightDataBindingField(
    private val myLayout: BindingLayout,
    private val myViewIdData: ViewIdData,
    manager: PsiManager,
    field: PsiField,
    containingClass: PsiClass,
  ) : LightField(manager, field, containingClass) {
    private var myNavigationTag: XmlTag? = null

    private fun computeTag(): XmlTag? {
      val xmlFile = myLayout.toXmlFile() ?: return null
      val resultTag = Ref<XmlTag>()
      xmlFile.accept(
        object : XmlRecursiveElementWalkingVisitor() {
          override fun visitXmlTag(tag: XmlTag) {
            super.visitXmlTag(tag)
            val idValue = tag.getAttributeValue(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)
            if (idValue != null && myViewIdData.id == stripPrefixFromId(idValue)) {
              resultTag.set(tag)
              stopWalking()
            }
          }
        }
      )
      return resultTag.get()
    }

    override fun getContainingFile(): PsiFile? {
      return myLayout.toXmlFile()
    }

    override fun getTextRange(): TextRange {
      return TextRange.EMPTY_RANGE
    }

    override fun getNavigationElement(): PsiElement {
      if (myNavigationTag != null) {
        return myNavigationTag
      }
      myNavigationTag = computeTag()
      return if ((myNavigationTag != null)) myNavigationTag!! else super.getNavigationElement()
    }

    override fun setName(name: String): PsiElement {
      // This method is called by rename refactoring and has to succeed in order for the refactoring
      // to succeed.
      // There no need to change the name since once the refactoring is complete, this object will
      // be replaced
      // by a new one reflecting the changed source code.
      return this
    }
  }
}
