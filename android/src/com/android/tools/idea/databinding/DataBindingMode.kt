/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding

import com.android.AndroidXConstants
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet

/** The class that holds the state of support for data binding in the project. */
enum class DataBindingMode
constructor(
  /** The default package name for data binding library */
  @JvmField val packageName: String,
  /** The view stub proxy class in data binding */
  @JvmField val viewStubProxy: String,
  /** The generated component class qualified name */
  @JvmField val dataBindingComponent: String,
  /** The qualified name for the Bindable annotation */
  @JvmField val bindable: String,
  /** The qualified name for the ViewDataBinding class */
  @JvmField val viewDataBinding: String,
  /** The qualified name for the BindingAdapter annotation */
  @JvmField val bindingAdapter: String,
  /** The qualified name for the BindingConversion annotation */
  @JvmField val bindingConversion: String,
  /** The qualified name for the BindingMethods annotation */
  @JvmField val bindingMethods: String,
  /** The qualified name for the InverseBindingAdapter annotation */
  @JvmField val inverseBindingAdapter: String,
  /** The qualified name for the InverseBindingMethod annotation */
  @JvmField val inverseBindingMethod: String,
  /** The qualified name for the InverseBindingMethods annotation */
  @JvmField val inverseBindingMethods: String,
  /** The qualified name for the InverseBindingMethods annotation */
  @JvmField val inverseMethod: String,
  /** The qualified name for the LiveData class */
  @JvmField val liveData: String,
  /**
   * The array of qualified names for ObservableField, or any of the primitive versions such as
   * ObservableBoolean and ObservableInt
   */
  @JvmField val observableFields: Array<String>,
) {

  /** Project does not use data binding */
  NONE("", "", "", "", "", "", "", "", "", "", "", "", "", arrayOf()),
  /** Project uses data binding in the androidx namespace */
  ANDROIDX(
    AndroidXConstants.DATA_BINDING_PKG.newName(),
    AndroidXConstants.CLASS_DATA_BINDING_VIEW_STUB_PROXY.newName(),
    AndroidXConstants.CLASS_DATA_BINDING_COMPONENT.newName(),
    AndroidXConstants.CLASS_DATA_BINDING_BINDABLE.newName(),
    AndroidXConstants.CLASS_DATA_BINDING_BASE_BINDING.newName(),
    AndroidXConstants.BINDING_ADAPTER_ANNOTATION.newName(),
    AndroidXConstants.BINDING_CONVERSION_ANNOTATION.newName(),
    AndroidXConstants.BINDING_METHODS_ANNOTATION.newName(),
    AndroidXConstants.INVERSE_BINDING_ADAPTER_ANNOTATION.newName(),
    AndroidXConstants.INVERSE_BINDING_METHOD_ANNOTATION.newName(),
    AndroidXConstants.INVERSE_BINDING_METHODS_ANNOTATION.newName(),
    AndroidXConstants.INVERSE_METHOD_ANNOTATION.newName(),
    AndroidXConstants.CLASS_LIVE_DATA.newName(),
    arrayOf(
      AndroidXConstants.CLASS_OBSERVABLE_BOOLEAN.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_BYTE.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_CHAR.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_DOUBLE.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_FIELD.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_FLOAT.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_INT.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_LONG.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_PARCELABLE.newName(),
      AndroidXConstants.CLASS_OBSERVABLE_SHORT.newName(),
    ),
  ),
  /** Project uses data binding in pre-androidx namespace */
  SUPPORT(
    AndroidXConstants.DATA_BINDING_PKG.oldName(),
    AndroidXConstants.CLASS_DATA_BINDING_VIEW_STUB_PROXY.oldName(),
    AndroidXConstants.CLASS_DATA_BINDING_COMPONENT.oldName(),
    AndroidXConstants.CLASS_DATA_BINDING_BINDABLE.oldName(),
    AndroidXConstants.CLASS_DATA_BINDING_BASE_BINDING.oldName(),
    AndroidXConstants.BINDING_ADAPTER_ANNOTATION.oldName(),
    AndroidXConstants.BINDING_CONVERSION_ANNOTATION.oldName(),
    AndroidXConstants.BINDING_METHODS_ANNOTATION.oldName(),
    AndroidXConstants.INVERSE_BINDING_ADAPTER_ANNOTATION.oldName(),
    AndroidXConstants.INVERSE_BINDING_METHOD_ANNOTATION.oldName(),
    AndroidXConstants.INVERSE_BINDING_METHODS_ANNOTATION.oldName(),
    AndroidXConstants.INVERSE_METHOD_ANNOTATION.oldName(),
    AndroidXConstants.CLASS_LIVE_DATA.oldName(),
    arrayOf(
      AndroidXConstants.CLASS_OBSERVABLE_BOOLEAN.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_BYTE.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_CHAR.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_DOUBLE.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_FIELD.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_FLOAT.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_INT.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_LONG.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_PARCELABLE.oldName(),
      AndroidXConstants.CLASS_OBSERVABLE_SHORT.oldName(),
    ),
  );

  companion object {
    /**
     * Use the context of a target [PsiElement] to return the surrounding data binding mode it
     * exists within. This should always return a valid mode for an element that lives inside a data
     * binding expression, but this can return [NONE] otherwise.
     */
    @JvmStatic
    fun fromPsiElement(element: PsiElement) =
      AndroidFacet.getInstance(element)?.let { DataBindingUtil.getDataBindingMode(it) } ?: NONE
  }
}
