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
package org.jetbrains.android.augment

import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.flattenResourceName
import com.google.common.base.MoreObjects
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType

/**
 * Subclass of [AndroidLightField] to store extra information specific to styleable attribute fields.
 */
class AndroidStyleableAttrLightField(
  _styleableAttrFieldUrl: StyleableAttrFieldUrl,
  myContext: PsiClass,
  fieldModifier: FieldModifier,
  myConstantValue: Any?
) : AndroidLightField(_styleableAttrFieldUrl.toFieldName(), myContext, PsiType.INT, fieldModifier, myConstantValue) {

  var styleableAttrFieldUrl = _styleableAttrFieldUrl
    set(value) {
      field = value
      _name = value.toFieldName()
    }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("styleable", styleableAttrFieldUrl.styleable)
      .add("attr", styleableAttrFieldUrl.attr)
      .toString()
  }
}

data class StyleableAttrFieldUrl(val styleable: ResourceReference, val attr: ResourceReference) {
  fun toFieldName(): String {
    val packageName = attr.namespace.packageName
    return if (styleable.namespace == attr.namespace || packageName.isNullOrEmpty()) {
      "${flattenResourceName(styleable.name)}_${flattenResourceName(attr.name)}"
    } else {
      "${flattenResourceName(styleable.name)}_${flattenResourceName(packageName)}_${flattenResourceName(attr.name)}"
    }
  }
}