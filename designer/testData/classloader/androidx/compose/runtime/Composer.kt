/*
 * Copyright (C) 2026 The Android Open Source Project
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
@file:Suppress("UNUSED_PARAMETER")
package androidx.compose.runtime

interface Composer {
    fun startReplaceGroup(key: Int)
    fun endReplaceGroup()
}

fun sourceInformationMarkerStart(composer: Composer, key: Int, sourceInformation: String) {}
fun sourceInformationMarkerEnd(composer: Composer) {}
fun sourceInformation(composer: Composer, sourceInformation: String) {}
fun isTraceInProgress(): Boolean = false
fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {}
fun traceEventEnd() {}
