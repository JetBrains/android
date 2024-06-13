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
package com.android.tools.property.ptable

/**
 * Elements removed and added to the children of a [PTableGroupItem].
 *
 * The children of a [PTableGroupItem] can be modified, but the [PTable] needs to update its model
 * for the correct expansion behaviour.
 */
class PTableGroupModification(
  val added: List<PTableItem>,
  val removed: List<PTableItem> = emptyList(),
)
