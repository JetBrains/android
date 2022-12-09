/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings

/**
 * A language version setting that delegates to an existing one except it does not enable LanguageFeature.InlineConstVals.
 *
 * This is a work around to not able to manually tell Kotlin's IR backend to not inline constant properties.
 *
 * We want to disable inline constant because of R.java values. The analysis uses a generated R.class with fake values
 * for autocomplete / resolution purpose. Should it be inlined in the currently editing class, the application will get
 * invalid index value.
 */
class LiveEditCompilerLanguageSettings (val delegate : LanguageVersionSettings) : LanguageVersionSettings {
  override val apiVersion: ApiVersion
    get() = delegate.apiVersion
  override val languageVersion: LanguageVersion
    get() = delegate.languageVersion

  override fun getFeatureSupport(feature: LanguageFeature): LanguageFeature.State {
    return if (feature == LanguageFeature.InlineConstVals) {
      LanguageFeature.State.DISABLED
    } else {
      delegate.getFeatureSupport(feature)
    }
  }

  override fun <T> getFlag(flag: AnalysisFlag<T>): T {
    return delegate.getFlag(flag)
  }

  override fun isPreRelease(): Boolean {
    return delegate.isPreRelease()
  }
}