/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.uipreview.nontransitive

/**
 * Without this class, fully-qualified references to [org.jetbrains.android.uipreview.nontransitive.lib.R] fail to resolve,
 * seemingly due to a Kotlin compiler bug in which a package is considered "missing" if it has no classes.
 */
@Suppress("unused")
class EmptyClass
