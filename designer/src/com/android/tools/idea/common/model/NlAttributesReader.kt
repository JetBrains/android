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
package com.android.tools.idea.common.model

import com.android.SdkConstants.ANDROID_URI

/**
 * Read-only interface for attributes of [NlAttributesHolder] and [NlComponent].
 *
 * Using this interface to restrict the modification of attributes.
 */
interface NlAttributesReader {
  /**
   * Get an attribute value by given [namespace] and [attribute].
   */
  fun getAttribute(namespace: String?, attribute: String): String?

  /**
   * Gets an attribute value from the [ANDROID_URI] namespace.
   */
  fun getAndroidAttribute(attribute: String): String?
}
