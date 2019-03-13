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
package com.android.tools.idea.gradle.project.sync.issues

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface for objects that can either be in a "sealed" or "unsealed" state, its up to the implementer to decide what this state means
 * for them.
 */
interface Sealable {
  fun seal()
  fun unseal()
  fun checkSeal(): Boolean
}

/**
 * Base implementation of [Sealable], uses an [AtomicBoolean] to track whether the object is sealed or not.
 */
class BaseSealable: Sealable {
  private val sealed = AtomicBoolean(false)

  override fun seal() = sealed.set(true)
  override fun unseal() = sealed.set(false)
  override fun checkSeal(): Boolean = sealed.get()
}