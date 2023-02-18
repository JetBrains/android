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
package com.android.tools.idea.appinspection.inspectors.network.model

/**
 * Aspects used for notifying UI events across the network inspector front end.
 *
 * When an aspect is changed, all dependent listeners receive a callback. This is useful for cases
 * such as when the user selects a connection in the details view and we want to notify the rest of
 * the UI to react to it.
 */
enum class NetworkInspectorAspect {
  /** The active tooltip has changed */
  TOOLTIP,

  /**
   * Aspect associated with a single, focused connection. The connection may be `null`, which means
   * that currently no connection is selected (perhaps recently deselected).
   */
  SELECTED_CONNECTION,

  /**
   * Aspect associated with a single, focused rule. The rule may be `null`, which means that
   * currently no rule is selected (perhaps recently deselected).
   */
  SELECTED_RULE,

  /**
   * Aspect associated with the panel that shows details of a connection or a rule. The panel may be
   * empty when there is no active selection or the panel is closed manually.
   */
  DETAILS
}
