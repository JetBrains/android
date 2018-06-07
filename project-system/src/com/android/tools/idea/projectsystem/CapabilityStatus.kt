/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import org.jetbrains.annotations.Nls

/**
 * Holds the status of a project system capability
 */
sealed class CapabilityStatus {
  abstract fun isSupported(): Boolean
}

/**
 * Indicates that the capability is supported by the project system
 */
class CapabilitySupported : CapabilityStatus() {
  override fun isSupported(): Boolean = true
}

/**
 * Indicates that the capability is not supported by the project system. Includes a user-readable message
 * explaining that the capability is unsupported.
 */
open class CapabilityNotSupported(@Nls val message: String = "The build system for this project does not support this feature",
                                  @Nls(capitalization = Nls.Capitalization.Title) val title: String = "Unsupported Capability") : CapabilityStatus() {
  override fun isSupported(): Boolean = false
}

/**
 * Indicates that the capability is not supported by the current version of the build system,
 * but would be available if it were upgraded to a newer version.
 */
class CapabilityUpgradeRequired(@Nls message: String = "You must upgrade your build system to support this feature",
                                @Nls(capitalization = Nls.Capitalization.Title) title: String = "Upgrade needed")
  : CapabilityNotSupported(message, title)
