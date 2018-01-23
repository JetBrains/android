/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.model;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * An include path or collection of include paths categorized into a PackageType.
 */
abstract public class ClassifiedIncludeValue extends IncludeValue {

  @NotNull
  public abstract PackageType getPackageType();

  /**
   * The home folder for the packaging system that owns this include expression.
   * For example, this is the NDK root folder for NDK components.
   */
  @NotNull
  public abstract File getPackageFamilyBaseFolder();
}
