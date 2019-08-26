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

import com.android.SdkConstants
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet

/**
 * The class that holds the state of support for data binding in the project.
 */
enum class DataBindingMode constructor(
  /**
   * The default package name for data binding library
   */
  @JvmField
  val packageName: String,
  /**
   * The view stub proxy class in data binding
   */
  @JvmField
  val viewStubProxy: String,
  /**
   * The generated component class qualified name
   */
  @JvmField
  val dataBindingComponent: String,
  /**
   * The qualified name for the Bindable annotation
   */
  @JvmField
  val bindable: String,
  /**
   * The qualified name for the ViewDataBinding class
   */
  @JvmField
  val viewDataBinding: String,
  /**
   * The qualified name for the BindingAdapter annotation
   */
  @JvmField
  val bindingAdapter: String,
  /**
   * The qualified name for the BindingConversion annotation
   */
  @JvmField
  val bindingConversion: String,
  /**
   * The qualified name for the BindingMethods annotation
   */
  @JvmField
  val bindingMethods: String,
  /**
   * The qualified name for the InverseBindingAdapter annotation
   */
  @JvmField
  val inverseBindingAdapter: String,
  /**
   * The qualified name for the InverseBindingMethod annotation
   */
  @JvmField
  val inverseBindingMethod: String,
  /**
   * The qualified name for the InverseBindingMethods annotation
   */
  @JvmField
  val inverseBindingMethods: String,
  /**
   * The qualified name for the LiveData class
   */
  @JvmField
  val liveData: String,
  /**
   * The array of qualified names for ObservableField, or any of the primitive versions
   * such as ObservableBoolean and ObservableInt
   */
  @JvmField
  val observableFields: Array<String>) {

  /**
   * Project does not use data binding
   */
  NONE("", "", "", "", "", "", "", "", "", "", "", "", arrayOf()),
  /**
   * Project uses data binding in the androidx namespace
   */
  ANDROIDX(
    SdkConstants.DATA_BINDING_PKG.newName(),
    SdkConstants.CLASS_DATA_BINDING_VIEW_STUB_PROXY.newName(),
    SdkConstants.CLASS_DATA_BINDING_COMPONENT.newName(),
    SdkConstants.CLASS_DATA_BINDING_BINDABLE.newName(),
    SdkConstants.CLASS_DATA_BINDING_BASE_BINDING.newName(),
    SdkConstants.BINDING_ADAPTER_ANNOTATION.newName(),
    SdkConstants.BINDING_CONVERSION_ANNOTATION.newName(),
    SdkConstants.BINDING_METHODS_ANNOTATION.newName(),
    SdkConstants.INVERSE_BINDING_ADAPTER_ANNOTATION.newName(),
    SdkConstants.INVERSE_BINDING_METHOD_ANNOTATION.newName(),
    SdkConstants.INVERSE_BINDING_METHODS_ANNOTATION.newName(),
    SdkConstants.CLASS_LIVE_DATA.newName(),
    arrayOf(SdkConstants.CLASS_OBSERVABLE_BOOLEAN.newName(),
            SdkConstants.CLASS_OBSERVABLE_BYTE.newName(),
            SdkConstants.CLASS_OBSERVABLE_CHAR.newName(),
            SdkConstants.CLASS_OBSERVABLE_DOUBLE.newName(),
            SdkConstants.CLASS_OBSERVABLE_FIELD.newName(),
            SdkConstants.CLASS_OBSERVABLE_FLOAT.newName(),
            SdkConstants.CLASS_OBSERVABLE_INT.newName(),
            SdkConstants.CLASS_OBSERVABLE_LONG.newName(),
            SdkConstants.CLASS_OBSERVABLE_PARCELABLE.newName(),
            SdkConstants.CLASS_OBSERVABLE_SHORT.newName())),
  /**
   * Project uses data binding in pre-androidx namespace
   */
  SUPPORT(
    SdkConstants.DATA_BINDING_PKG.oldName(),
    SdkConstants.CLASS_DATA_BINDING_VIEW_STUB_PROXY.oldName(),
    SdkConstants.CLASS_DATA_BINDING_COMPONENT.oldName(),
    SdkConstants.CLASS_DATA_BINDING_BINDABLE.oldName(),
    SdkConstants.CLASS_DATA_BINDING_BASE_BINDING.oldName(),
    SdkConstants.BINDING_ADAPTER_ANNOTATION.oldName(),
    SdkConstants.BINDING_CONVERSION_ANNOTATION.oldName(),
    SdkConstants.BINDING_METHODS_ANNOTATION.oldName(),
    SdkConstants.INVERSE_BINDING_ADAPTER_ANNOTATION.oldName(),
    SdkConstants.INVERSE_BINDING_METHOD_ANNOTATION.oldName(),
    SdkConstants.INVERSE_BINDING_METHODS_ANNOTATION.oldName(),
    SdkConstants.CLASS_LIVE_DATA.oldName(),
    arrayOf(SdkConstants.CLASS_OBSERVABLE_BOOLEAN.oldName(),
            SdkConstants.CLASS_OBSERVABLE_BYTE.oldName(),
            SdkConstants.CLASS_OBSERVABLE_CHAR.oldName(),
            SdkConstants.CLASS_OBSERVABLE_DOUBLE.oldName(),
            SdkConstants.CLASS_OBSERVABLE_FIELD.oldName(),
            SdkConstants.CLASS_OBSERVABLE_FLOAT.oldName(),
            SdkConstants.CLASS_OBSERVABLE_INT.oldName(),
            SdkConstants.CLASS_OBSERVABLE_LONG.oldName(),
            SdkConstants.CLASS_OBSERVABLE_PARCELABLE.oldName(),
            SdkConstants.CLASS_OBSERVABLE_SHORT.oldName()));

  companion object {
    /**
     * Use the context of a target [PsiElement] to return the surrounding data binding mode
     * it exists within. This should always return a valid mode for an element that lives inside
     * a data binding expression, but this can return [NONE] otherwise.
     */
    @JvmStatic
    fun fromPsiElement(element: PsiElement) = AndroidFacet.getInstance(element)?.let { DataBindingUtil.getDataBindingMode(it) } ?: NONE
  }
}
