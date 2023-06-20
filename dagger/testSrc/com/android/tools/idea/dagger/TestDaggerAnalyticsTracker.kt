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
package com.android.tools.idea.dagger

import com.google.wireless.android.sdk.stats.DaggerEditorEvent

class TestDaggerAnalyticsTracker : DaggerAnalyticsTracker {
  val calledMethods = mutableListOf<String>()

  override fun trackNavigation(
    context: DaggerEditorEvent.NavigationMetadata.NavigationContext,
    fromElement: DaggerEditorEvent.ElementType,
    toElement: DaggerEditorEvent.ElementType
  ) {
    calledMethods.add("trackNavigation $context $fromElement $toElement")
  }

  override fun shouldLog(percentage: Int): Boolean = true

  override fun trackGutterWasDisplayed(ownerElement: DaggerEditorEvent.ElementType, time: Long) {
    calledMethods.add("trackGutterWasDisplayed owner: $ownerElement time: $time")
  }

  override fun trackFindUsagesNodeWasDisplayed(
    ownerElement: DaggerEditorEvent.ElementType,
    time: Long
  ) {
    calledMethods.add("trackFindUsagesNodeWasDisplayed owner: $ownerElement time: $time")
  }

  override fun trackClickOnGutter(ownerElement: DaggerEditorEvent.ElementType) {
    calledMethods.add("trackClickOnGutter $ownerElement")
  }

  override fun trackOpenLinkFromError() {
    calledMethods.add("trackOpenLinkFromError")
  }
}
