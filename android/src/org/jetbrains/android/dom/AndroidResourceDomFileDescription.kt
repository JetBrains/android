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

package org.jetbrains.android.dom

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomFileDescription
import org.jetbrains.annotations.NonNls
import java.util.EnumSet

/**
 * Common supertype for [DomFileDescription]s of Android resource files.
 */
sealed class AndroidResourceDomFileDescription<T : DomElement>(
  rootElementClass: Class<T>,
  rootTagName: String,
  resourceFolderTypes: EnumSet<ResourceFolderType>
) : DomFileDescription<T>(rootElementClass, rootTagName) {

  constructor(
    rootElementClass: Class<T>,
    @NonNls rootTagName: String,
    resourceFolderType: ResourceFolderType
  ) : this(rootElementClass, rootTagName, EnumSet.of<ResourceFolderType>(resourceFolderType))

  private val resourceFolderTypes: EnumSet<ResourceFolderType> = EnumSet.copyOf(resourceFolderTypes)

  override fun isMyFile(file: XmlFile, module: Module?): Boolean {
    for (folderType in resourceFolderTypes) {
      if (isFileInResourceFolderType(file, folderType)) {
        return true
      }
    }

    return false
  }

  override fun initializeFileDescription() {
    registerNamespacePolicy(SdkConstants.ANDROID_NS_NAME, SdkConstants.ANDROID_URI)
    registerNamespacePolicy(SdkConstants.APP_PREFIX, SdkConstants.AUTO_URI)
    registerNamespacePolicy(SdkConstants.TOOLS_PREFIX, SdkConstants.TOOLS_URI)
  }

  companion object {
    @JvmStatic
    fun isFileInResourceFolderType(file: XmlFile, folderType: ResourceFolderType): Boolean {
      return FileDescriptionUtils.isResourceOfTypeWithRootTag(file, folderType, emptySet())
    }
  }
}

/**
 * Common supertype for all DOM descriptions which are uniquely identified by the root tag name and the [ResourceFolderType].
 *
 * IntelliJ uses the root tag name to quickly find potential matches and only later falls back to descriptions that accept other root
 * tag names.
 *
 * Note that although [com.intellij.util.xml.DomManager] will not call [isMyFile] on a file with a wrong root tag name, we ourselves
 * sometimes do it, e.g. in [org.jetbrains.android.formatter.AndroidXmlFormattingModelBuilder.getContextSpecificSettings].
 */
abstract class SingleRootResourceDomFileDescription<T : DomElement>(
  rootElementClass: Class<T>,
  tagName: String,
  resourceFolderTypes: EnumSet<ResourceFolderType>
) : AndroidResourceDomFileDescription<T>(rootElementClass, tagName, resourceFolderTypes) {

  constructor(
    rootElementClass: Class<T>,
    rootTagName: String,
    resourceFolderType: ResourceFolderType
  ) : this(rootElementClass, rootTagName, EnumSet.of<ResourceFolderType>(resourceFolderType))

  final override fun acceptsOtherRootTagNames() = false
  final override fun isMyFile(file: XmlFile, module: Module?) = super.isMyFile(file, module) && myRootTagName == file.rootTag?.name
}

/**
 * Base class for creating [DomFileDescription] classes describing Android XML resources with a fixed number of
 * possible root tags. Subclasses should provide no-arguments constructor and call "super" with required parameter values there.
 */
abstract class MultipleKnownRootsResourceDomFileDescription<T : DomElement>(
  rootElementClass: Class<T>,
  resourceFolderTypes: EnumSet<ResourceFolderType>,
  private val tagNames: Set<String>
) : AndroidResourceDomFileDescription<T>(rootElementClass, tagNames.first(), resourceFolderTypes) {

  constructor(
    rootElementClass: Class<T>,
    resourceFolderType: ResourceFolderType,
    tagNames: Set<String>
  ) : this(rootElementClass, EnumSet.of<ResourceFolderType>(resourceFolderType), tagNames)

  constructor(
    rootElementClass: Class<T>,
    resourceFolderType: ResourceFolderType,
    vararg tagNames: String
  ) : this(rootElementClass, resourceFolderType, java.util.Set.of(*tagNames))

  final override fun acceptsOtherRootTagNames() = true
  final override fun isMyFile(file: XmlFile, module: Module?) = super.isMyFile(file, module) && tagNames.contains(file.rootTag?.name)
}

/**
 * Base class for DOM descriptions that apply to all files in a given [ResourceFolderType].
 */
abstract class ResourceFolderTypeDomFileDescription<T : DomElement>(
  rootElementClass: Class<T>,
  resourceFolderTypes: ResourceFolderType,
  defaultTagName: String
) : AndroidResourceDomFileDescription<T>(rootElementClass, defaultTagName, resourceFolderTypes) {

  final override fun acceptsOtherRootTagNames() = true
  final override fun isMyFile(file: XmlFile, module: Module?) = super.isMyFile(file, module)
}

/**
 * Base class for DOM descriptions which need to apply custom logic to determine if they are applicable to the given resource file.
 */
abstract class CustomLogicResourceDomFileDescription<T : DomElement>(
  rootElementClass: Class<T>,
  resourceFolderTypes: EnumSet<ResourceFolderType>,
  defaultTagName: String
) : AndroidResourceDomFileDescription<T>(rootElementClass, defaultTagName, resourceFolderTypes) {

  constructor(
    rootElementClass: Class<T>,
    resourceFolderType: ResourceFolderType,
    exampleTagName: String
  ) : this(rootElementClass, EnumSet.of(resourceFolderType), exampleTagName)

  final override fun acceptsOtherRootTagNames() = true
  final override fun isMyFile(file: XmlFile, module: Module?) = super.isMyFile(file, module) && checkFile(file, module)

  /**
   * Custom logic for checking if a file in one of the recognized [ResourceFolderType]s is covered by this description.
   */
  abstract fun checkFile(file: XmlFile, module: Module?): Boolean
}
