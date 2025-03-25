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
package com.android.tools.visuallint

/** Meta data for base config issues. */
class VisualLintBaseConfigIssues {

  /** List of issues that current component has in base configuration. */
  data class BaseConfigComponentState(
    var hasI18NEllipsis: Boolean = false,
    var hasI18NTextTooBig: Boolean = false,
  )

  /**
   * State of the component. Key is hashcode of the [com.intellij.psi.xml.XmlTag] and value shows
   * what state configuration is in.
   */
  val componentState: MutableMap<Int, BaseConfigComponentState> = HashMap()

  fun clear() {
    componentState.clear()
  }
}
