/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.quickFixes

import com.android.tools.idea.gradle.project.sync.idea.issues.OpenLinkDescribedQuickFix

class OpenSourceCompatibilityLinkQuickFix : OpenLinkDescribedQuickFix() {
  override val description = "More information about sourceCompatibility..."
  override val id = "OpenSourceCompatibilityLinkQuickFix"
  override val link = "https:/developers.android.com/r/tools/upgrade-assistant/source-compatibility"
}