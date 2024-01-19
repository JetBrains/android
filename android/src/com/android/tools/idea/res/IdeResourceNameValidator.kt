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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.FileResourceNameValidator
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ValueResourceNameValidator
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.intellij.openapi.ui.InputValidatorEx

private sealed class InputType {
  data class ValueName(val type: ResourceType) : InputType()

  data class FileName(val type: ResourceFolderType, val implicitExtension: String?) : InputType()
}

/**
 * Implementation of [InputValidatorEx] that delegates to validation code in sdk-common. On top of
 * that it can also check that the given resource name is not defined yet.
 *
 * @see ValueResourceNameValidator
 * @see FileResourceNameValidator
 */
class IdeResourceNameValidator
private constructor(
  /** Describes the kind of input that is to be validated. */
  private val inputType: InputType,
  /** Set of existing NORMALIZED names to check for conflicts with. */
  private val existing: Set<String>? = null,
) : InputValidatorEx {

  companion object {

    /**
     * Creates an [IdeResourceNameValidator] that checks if the input is a valid resource name of a
     * given type.
     *
     * If [existing] is non-null, the returned validator will also not accept a name that's already
     * defined.
     *
     * @see ValueResourceNameValidator
     */
    @JvmStatic
    @JvmOverloads
    fun forResourceName(
      type: ResourceType,
      existing: ResourceRepository? = null,
    ): IdeResourceNameValidator {
      val resourceNames = getExistingNames(existing, type)
      return IdeResourceNameValidator(
        InputType.ValueName(type),
        resourceNames.mapTo(HashSet(), ValueResourceNameValidator::normalizeName),
      )
    }

    /**
     * Returns a set of strings representing the names of resources already present in the given
     * [ResourceRepository].
     */
    private fun getExistingNames(existing: ResourceRepository?, type: ResourceType): Set<String> =
      existing?.getResources(ResourceNamespace.TODO(), type)?.keySet() ?: emptySet()

    /**
     * Creates an [IdeResourceNameValidator] that checks if the input is a valid filename in a given
     * folder type. Note that there are no restrictions on filenames in [ResourceFolderType.VALUES].
     *
     * If [implicitExtension] is defined, it is assumed that the input will be used to create a file
     * with the given extension. If the input ends with [implicitExtension], this suffix will be
     * ignored when validating. Except for this special case, the returned validator will reject
     * dots in the input, including any other extension suffixes.
     *
     * Returned validator will accept less inputs than one returned by [#forResourceName], as the
     * rules are more strict.
     *
     * @see FileResourceNameValidator
     */
    @JvmStatic
    @JvmOverloads
    fun forFilename(
      type: ResourceFolderType,
      implicitExtension: String? = null,
      existing: ResourceRepository? = null,
    ): IdeResourceNameValidator {
      require(implicitExtension == null || implicitExtension[0] == '.')
      val resourceType = ResourceType.fromFolderName(type.getName())
      val existingNames =
        if (resourceType != null) getExistingNames(existing, resourceType) else null
      return IdeResourceNameValidator(InputType.FileName(type, implicitExtension), existingNames)
    }
  }

  override fun canClose(inputString: String): Boolean = checkInput(inputString)

  fun doesResourceExist(name: String): Boolean =
    existing?.contains(ValueResourceNameValidator.normalizeName(name)) ?: false

  override fun getErrorText(inputString: String?): String? {
    if (inputString.isNullOrBlank()) {
      return "Enter a new name"
    }

    // Check if the name is OK from aapt point of view.
    val aaptError =
      when (inputType) {
        is InputType.ValueName ->
          ValueResourceNameValidator.getErrorText(inputString, inputType.type)
        is InputType.FileName -> {
          val inputWithoutExtension =
            if (inputType.implicitExtension != null)
              inputString.removeSuffix(inputType.implicitExtension)
            else inputString
          FileResourceNameValidator.getErrorTextForNameWithoutExtension(
            inputWithoutExtension,
            inputType.type,
          )
        }
      }

    return when {
      aaptError != null -> aaptError
      doesResourceExist(inputString) ->
        "${ValueResourceNameValidator.normalizeName(inputString)} already exists"
      else -> null
    }
  }
}
