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

/**
 * Native method [ClassifierSet] that represents a leaf node in a heapprofd trace.
 */
class NativeAllocationMethodSet(allocationFunction: String) : ClassifierSet(allocationFunction) {
  public override fun createSubClassifier(): Classifier {
    // Do nothing, as this is a leaf node.
    return Classifier.Id
  }

  companion object {
    @JvmStatic
    fun createDefaultClassifier(): Classifier = NativeAllocationMethodClassifier.newInstance()
  }
}