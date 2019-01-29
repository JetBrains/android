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
package com.android.tools.idea.common.property2.api

/**
 * Support for Help binding.
 *
 * A [PropertyItem] may optionally implement this interface and supply
 * actions that can be used to provide help for a property.
 */
interface HelpSupport {
  /**
   * Request help for the implied property.
   *
   * This will be invoked from the user pressing F1 in the property UI.
   */
  fun help()

  /**
   * Request secondary help for the implied property.
   *
   * This will be invoked from the user pressing shift-F1 in the property UI.
   */
  fun secondaryHelp()
}
