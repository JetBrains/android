/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

enum PropertyDesignState {
  NOT_APPLICABLE,                 // This property does not offer a separate design property (or may be a design property itself that cannot be removed)
  IS_REMOVABLE_DESIGN_PROPERTY,   // This is a design property and it can be removed
  HAS_DESIGN_PROPERTY,            // This is a runtime property with an existing matching design property
  MISSING_DESIGN_PROPERTY         // This is a runtime property where no matching design property exists yet
}
