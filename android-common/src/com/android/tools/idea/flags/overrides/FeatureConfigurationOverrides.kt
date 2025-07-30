/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.flags.overrides

import com.android.flags.Flag
import com.android.flags.ImmutableFlagOverrides
import com.android.tools.idea.flags.FeatureConfiguration
import com.android.utils.associateNotNull
import com.google.common.annotations.VisibleForTesting
import java.io.InputStream

/**
 * Overrides for the default values for boolean flags.
 *
 * Boolean flags cannot receive their default values in the
 * constructor because they are (mostly) used for feature flags,
 * and we want to control these better during the feature lifecycle.
 *
 * Therefore, this [ImmutableFlagOverrides] is used to provide default values
 * by reading the values from a file.
 *
 * It must be added to [com.android.flags.Flags] as the *last* override in the list
 * since it's not actually an override.
 */
class FeatureConfigurationOverrides: ImmutableFlagOverrides {

  override fun get(flag: Flag<*>): String? = defaultValues[flag.id]

  companion object {
    /** Returns the current IDE feature flags configuration as a stream. */
    private fun featureFlagsResourceStream(): InputStream =
      requireNotNull(FeatureConfigurationOverrides::class.java.getResourceAsStream(FEATURE_FLAGS_FILE))

    /**
     * The map that contains the default values. The values are already applied to the
     * current configuration, so that the value can be used directly.
     *
     * The map is from flag id (group + name) to a serialized boolean value
     */
    private val defaultValues: Map<String, String> by lazy {
      loadValues()
    }

    @VisibleForTesting
    fun loadValues(
      inputStream: InputStream = featureFlagsResourceStream(),
      currentConfig: FeatureConfiguration = FeatureConfiguration.current,
    ): Map<String, String> {
      val configsByName = FeatureConfiguration.entries.associateBy { it.name }

      return inputStream.use { stream ->
        stream.reader(Charsets.UTF_8).use { reader ->
          reader.readLines().filter { !it.startsWith("#") }.associateNotNull {
            val tokens = parseLine(it) ?: return@associateNotNull null
            val flagConfig = configsByName[tokens.second] ?: return@associateNotNull null
            tokens.first to (currentConfig.stabilityLevel <= flagConfig.stabilityLevel).toString()
          }
        }
      }
    }

    @VisibleForTesting
    fun parseLine(
      line: String,
      removeDate: Boolean = true,
      throwOnInvalidValue: Boolean = false,
      ): Pair<String, String>? {
      val tokens = line.split("=")
      if (tokens.size != 2) {
        if (throwOnInvalidValue)
          throw RuntimeException("line '$line' does not split in 2 components around =")
        else
          return null
      }

      // remove comments
      val commentCharPos = tokens[1].indexOf('#')
      val flagValue = if (commentCharPos != -1) {
        tokens[1].substring(0, commentCharPos).trim()
      } else {
        tokens[1]
      }

      return tokens[0] to if (removeDate) {
        if (flagValue.startsWith("${FeatureConfiguration.STABLE.name}:")) { FeatureConfiguration.STABLE.name } else flagValue
      } else flagValue
    }
  }
}

const val FEATURE_FLAGS_FILE = "/feature_flags.txt"
