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
package com.android.tools.idea.ui

import org.junit.rules.ExternalResource
import org.junit.Rule

/**
 * [Rule] which overrides the [AbstractDialogWrapper.factory] with a factory of [FakeDialogWrapper] instances,
 * then restores the original factory after the test is over.
 */
class FakeDialogWrapperRule : ExternalResource() {
  private var previousFactory: DialogWrapperFactory? = null

  override fun before() {
    previousFactory = FakeDialogWrapper.setUpFactory()
  }

  override fun after() {
    previousFactory?.let { FakeDialogWrapper.restoreFactory(it) }
  }
}