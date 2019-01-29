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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils

import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon

/**
 * A simple wrapper over PresentationData that can be used to redirect to a string.
 */
interface PresentationDataWrapper {
  fun addText(text: String, attributes: SimpleTextAttributes)
  fun setIcon(icon: Icon?)
}

/**
 * Create a pass-through wrapper to an underlying PresentationData.
 */
fun createPresentationDataWrapper(presentationData : PresentationData): PresentationDataWrapper {
  return object : PresentationDataWrapper {
    override fun addText(text: String, attributes: SimpleTextAttributes) {
      presentationData.addText(text, attributes)
    }

    override fun setIcon(icon: Icon?) {
      presentationData.setIcon(icon)
    }
  }
}

/**
 * Create a wrapper that writes to a StringBuilder. UI elements are ignored.
 */
fun createPresentationDataWrapper(stringBuilder : StringBuilder): PresentationDataWrapper {
  return object : PresentationDataWrapper {
    override fun addText(text: String, attributes: SimpleTextAttributes) {
      stringBuilder.append(text)
    }

    override fun setIcon(icon: Icon?) {
    }
  }
}