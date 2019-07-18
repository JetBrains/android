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
package com.android.tools.idea.res.binding

import com.android.ide.common.resources.DataBindingResourceType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * An interface for binding-related information that can be extracted from a layout xml file.
 *
 * TODO(b/136500593) - this class currently mixes PSI information and model data. We should
 *  separate the concerns into different classes to allow us to make performance improvements in
 *  the future.
 */
abstract class BindingLayoutInfo : ModificationTracker {
  /**
   * The different android layouts that we create [BindingLayoutInfo] for, depending on whether data binding or view binding is
   * switched on.
   *
   * [VIEW_BINDING_LAYOUT] bindings are generated for legacy views - those that are not data binding views.
   *
   * [DATA_BINDING_LAYOUT] bindings are generated for views using data binding. They start with `<layout>` and `<data>`
   * tags.
   *
   * When both are enabled, data binding layouts will be of type [DATA_BINDING_LAYOUT], the rest will be [VIEW_BINDING_LAYOUT].
   *
   * Note: This enum is used by DataBindingXmlIndex and is serialized and de-serialized. Please only append.
   */
  enum class LayoutType {
    VIEW_BINDING_LAYOUT,
    DATA_BINDING_LAYOUT
  }

  abstract val facet: AndroidFacet
  abstract val project: Project

  /**
   * The module which owns this layout file.
   *
   * It should never be `null` in practice, but it's not something we can guarantee, as the PSI
   * element that gets registered with this info could technically be created outside of a module.
   */
  abstract val module: Module?

  abstract val psiFile: PsiFile

  /**
   * The PSI for an "XyzBinding" class generated for this layout file. It is created
   * externally so it can potentially be `null` until it is set.
   *
   * NOTE: This is a code smell - it's a field that this class never uses, but rather it relies on
   * an external class to set it. Ideally, we can fix this, perhaps by removing the field
   * completely (and having somewhere else own it).
   *
   * See also: `DataBindingClassFactory.getOrCreatePsiClass`
   */
  var psiClass: PsiClass? = null

  /**
   * The PSI element representing this layout file, useful if a user wants to navigate
   * when their cursor is on an "XyzBinding" class name.
   */
  abstract val navigationElement: PsiElement

  /**
   * Given a resource type (e.g. variable or import), return all "name to resource item" mappings
   * (e.g. the PSI elements for all variables keyed by the variable name)
   */
  abstract fun getItems(type: DataBindingResourceType): Map<String, PsiDataBindingResourceItem>

  /**
   * Helper function for resolving an import by name, given that the import is specified in this
   * layout file.
   */
  fun resolveImport(nameOrAlias: String): String? = getItems(DataBindingResourceType.IMPORT)[nameOrAlias]?.typeDeclaration

  abstract val layoutType: LayoutType

  /**
   * Whether this info is extracted from a singleton layout file or represents a merged collection
   * of a layout file with multiple configurations.
   */
  abstract val isMerged: Boolean

  /**
   * Additional info if a target layout file has additional configurations (e.g. for other
   * resolutions or orientations), or `null` if none.
   */
  abstract val mergedInfo: BindingLayoutInfo?

  /**
   * The unqualified name of the binding class that should be generated for this info.
   */
  abstract val className: String

  /**
   * The package that the binding class generated for this info should belong to.
   */
  abstract val packageName: String

  val qualifiedName: String
    get() = "$packageName.$className"
}
