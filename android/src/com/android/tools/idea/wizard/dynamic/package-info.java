/*
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * Support for wizards that allow dynamically adding multiple paths and steps (unlike the wizards
 * offered by IntelliJ, which require a fixed list of steps up front).
 * <p/>
 * A path is a collection of one or more steps, and wizards contain one or more paths. Furthermore,
 * different wizards may share (their own instances of) the same path class.
 * <p/>
 * Data is passed along the wizard from step to step via a
 * {@link com.android.tools.idea.wizard.dynamic.ScopedStateStore}, which is a hashtable of data,
 * essentially.
 * <p/>
 * TODO: Post wizard migration: delete this package
 */
package com.android.tools.idea.wizard.dynamic;