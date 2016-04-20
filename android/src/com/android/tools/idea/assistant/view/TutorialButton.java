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
package com.android.tools.idea.assistant.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A custom button for navigating to tutorials. Renders similar to a link.
 * Noting that a styled label was not used for accessibility reasons. For
 * example, arbitrary objects with click listeners don't handle tabbing, focus,
 * properly support screen readers, etc.
 *
 * TODO: Investigate migrating display properties to a form.
 * TODO: If we have further button-as-link treatments, extract that portion out
 * into a new class, just leaving the insets here.
 */
public class TutorialButton extends NavigationButton {

  public TutorialButton(String label, String key, ActionListener listener) {
    // Render the button as a link to conform with the document-style presentation. We're using a button rather than a link with a
    // click handler to retain built-in accessibility benefits of a button.
    super("<html><body><a href=\"#\">" + label + "</a></body></html>", key, listener);
    setHorizontalAlignment(SwingConstants.LEFT);
    setMargin(new Insets(50, 10, 10, 10));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setBorder(null);
    setOpaque(false);
  }
}
