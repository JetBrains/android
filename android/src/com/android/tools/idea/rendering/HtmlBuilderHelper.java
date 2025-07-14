/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.utils.HtmlBuilder;
import com.intellij.ui.JBColor;

/**
 * Helper methods for using SDK common's {@link HtmlBuilder} in the IDE
 */
public class HtmlBuilderHelper {

  public static String getHeaderFontColor() {
    // See com.intellij.codeInspection.HtmlComposer.appendHeading
    // (which operates on StringBuffers)
    return !JBColor.isBright() ? "#A5C25C" : "#005555";
  }
}
