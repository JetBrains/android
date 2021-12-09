/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.adtui.model.filter.Filter
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.InstanceObject

/**
 * Classifies [InstanceObject]s based on their [Class].
 */
class ClassSet(val classEntry: ClassDb.ClassEntry) : ClassifierSet(classEntry.simpleClassName) {

  // Do nothing, as this is a leaf node (presently).
  public override fun createSubClassifier(): Classifier = Classifier.Id

  override fun applyFilter(filter: Filter, hasMatchedAncestor: Boolean, filterChanged: Boolean) {
    if (filterChanged || needsRefiltering) {
      isMatched = matches(filter)
      filterMatchCount = if (isMatched) 1 else 0
      myIsFiltered =!isMatched && !hasMatchedAncestor
      needsRefiltering = false
    }
  }

  override fun matches(filter: Filter): Boolean = filter.matches(classEntry.className)

  companion object {
    @JvmField
    val EMPTY_SET = ClassSet(ClassDb.ClassEntry(ClassDb.INVALID_CLASS_ID.toLong(), ClassDb.INVALID_CLASS_ID.toLong(), "null"))

    @JvmStatic
    fun createDefaultClassifier(): Classifier = classClassifier()
    private fun classClassifier() = Classifier.of(InstanceObject::getClassEntry, ::ClassSet)
  }
}