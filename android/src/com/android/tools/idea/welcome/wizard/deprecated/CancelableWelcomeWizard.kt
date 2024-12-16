/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated

import com.android.annotations.concurrency.UiThread

/**
 * Represents a welcome wizard that can be canceled. It allows the wizard to be cancelled
 * programmatically if needed, for instance when the IDE frame is closed while it's active.
 */
interface CancelableWelcomeWizard {

  /**
   * Checks if the wizard is currently active (hasn't already been cancelled or finished).
   *
   * @return {@code true} if the wizard is active, {@code false} otherwise.
   */
  @get:UiThread val isActive: Boolean

  /** Cancels the wizard, as if the user had explicitly closed it. */
  @UiThread fun cancel()
}
