/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.api

/**
 * A definition of a group in a table.
 *
 * A group has a [name] and a [itemFilter] which matches the items which make
 * up the group. A non editable value can be displayed in the table by
 * supplying a [value].
 */
interface GroupSpec<P : PropertyItem> {
  val name: String
  val value: String?
  val itemFilter: (P) -> Boolean
}
