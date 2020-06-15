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
package com.android.tools.idea.uibuilder.editor.multirepresentation

/**
 * A uniquely identifying display name of the corresponding [PreviewRepresentation]. It is used for
 *  a) Having a display string in the selection DropDownAction for a corresponding [PreviewRepresentation]
 *  b) Have a unique identifier for creating, storing and disposing [PreviewRepresentation]s, so that we do not create the same twice,
 *  know which we do not use anymore etc.
 */
typealias RepresentationName = String