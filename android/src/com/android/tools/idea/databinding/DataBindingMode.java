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
package com.android.tools.idea.databinding;

import com.android.SdkConstants;
import org.jetbrains.annotations.NotNull;

/**
 * The class that holds the state of support for data binding in the project.
 */
public enum DataBindingMode {
  /**
   * Project does not use data binding
   */
  NONE("", "", "", "", "", ""),
  /**
   * Project uses data binding in the androidx namespace
   */
  ANDROIDX(
    SdkConstants.DATA_BINDING_PKG.newName(),
    SdkConstants.CLASS_DATA_BINDING_VIEW_STUB_PROXY.newName(),
    SdkConstants.CLASS_DATA_BINDING_COMPONENT.newName(),
    SdkConstants.CLASS_DATA_BINDING_BINDABLE.newName(),
    SdkConstants.CLASS_DATA_BINDING_BASE_BINDING.newName(),
    SdkConstants.BINDING_ADAPTER_ANNOTATION.newName()),
  /**
   * Project uses data binding in pre-androidx namespace
   */
  SUPPORT(
    SdkConstants.DATA_BINDING_PKG.oldName(),
    SdkConstants.CLASS_DATA_BINDING_VIEW_STUB_PROXY.oldName(),
    SdkConstants.CLASS_DATA_BINDING_COMPONENT.oldName(),
    SdkConstants.CLASS_DATA_BINDING_BINDABLE.oldName(),
    SdkConstants.CLASS_DATA_BINDING_BASE_BINDING.oldName(),
    SdkConstants.BINDING_ADAPTER_ANNOTATION.oldName());
  /**
   * The default package name for data binding library
   */
  @NotNull
  public final String packageName;
  /**
   * The view stub proxy class in data binding
   */
  @NotNull
  public final String viewStubProxy;
  /**
   * The generated component class qualified name
   */
  @NotNull
  public final String dataBindingComponent;
  /**
   * The qualified name for the Bindable annotation
   */
  @NotNull
  public final String bindable;
  /**
   * The qualified name for the ViewDataBinding class
   */
  @NotNull
  public final String viewDataBinding;
  /**
   * The qualified name for the BindingAdapter annotation
   */
  @NotNull
  public final String bindingAdapter;

  DataBindingMode(
    @NotNull String packageName,
    @NotNull String viewStubProxy,
    @NotNull String dataBindingComponent,
    @NotNull String bindable,
    @NotNull String viewDataBinding,
    @NotNull String bindingAdapter) {
    this.packageName = packageName;
    this.viewStubProxy = viewStubProxy;
    this.dataBindingComponent = dataBindingComponent;
    this.bindable = bindable;
    this.viewDataBinding = viewDataBinding;
    this.bindingAdapter = bindingAdapter;
  }
}
