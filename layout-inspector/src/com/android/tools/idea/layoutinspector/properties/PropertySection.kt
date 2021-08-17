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
package com.android.tools.idea.layoutinspector.properties

/**
 * The attributes are grouped in sections in the properties panel.
 */
enum class PropertySection {
  DEFAULT,    // Use this value if an item is not in any of the groups mentioned below
  DECLARED,   // This attribute was specified by the user in a layout file in the application
  LAYOUT,     // This attribute is a layout attribute i.e. defined by the parent view
  DIMENSION,  // This attribute is intended for the Dimension section only
  VIEW,       // This attribute is intended for the SelectedView section only
  PARAMETERS, // This attribute is a parameter of a composable
  MERGED,     // This attribute is a merged semantic attribute of a composable
  UNMERGED,   // This attribute is an unmerged semantic attribute from a semantic modifier of a composable
}
