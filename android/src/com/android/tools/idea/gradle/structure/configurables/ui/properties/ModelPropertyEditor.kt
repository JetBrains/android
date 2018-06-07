/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import javax.swing.JComponent

/**
 * A model property editor.
 *
 * The editor wraps a component configured for editing of a specific property of a model of type [ModelT].  The editor is bound to
 * an instance of[ModelT] and the property of the bound model is automatically updated with the current value of the editor.
 */
interface ModelPropertyEditor<in ModelT> {
  /**
   * The component to be added to the model editor.
   */
  val component: JComponent
}