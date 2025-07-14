/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.configurations

import com.intellij.psi.xml.XmlTag

/**
 * Represents a user defined configuration.
 *
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/user-configuration/user-configurations">WFF
 *   User Configurations</a>
 */
sealed class UserConfiguration(open val id: String, open val xmlTag: XmlTag)

data class ColorConfiguration(
  override val id: String,
  override val xmlTag: XmlTag,
  val colorIndices: IntRange,
) : UserConfiguration(id, xmlTag)

data class ListConfiguration(override val id: String, override val xmlTag: XmlTag) :
  UserConfiguration(id, xmlTag)

data class BooleanConfiguration(override val id: String, override val xmlTag: XmlTag) :
  UserConfiguration(id, xmlTag)

data class PhotosConfiguration(override val id: String, override val xmlTag: XmlTag) :
  UserConfiguration(id, xmlTag)

data class UnknownConfiguration(override val id: String, override val xmlTag: XmlTag) :
  UserConfiguration(id, xmlTag)
