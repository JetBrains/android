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
package com.android.tools.idea.fd.gradle;

/** {@link InstantRunGradleSupport} indicates whether the current version of the gradle plugin supports an instant run build. */
public enum InstantRunGradleSupport {
  SUPPORTED,
  DISABLED,

  NO_GRADLE_MODEL,
  GRADLE_PLUGIN_TOO_OLD,
  VARIANT_DOES_NOT_SUPPORT_INSTANT_RUN,
  LEGACY_MULTIDEX_REQUIRES_ART,

  CANNOT_BUILD_FOR_MULTIPLE_DEVICES,
}
