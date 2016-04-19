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
import java.awt.event.ActionListener;

/**
 * Generic button used for navigating between cards. No display properties should be overridden here, make them directly or in a subclass.
 */
public class NavigationButton extends JButton {
  private String myKey;

  public NavigationButton(String label, String key, ActionListener listener) {
    super(label);

    myKey = key;
    addActionListener(listener);
  }

  public String getKey() {
    return myKey;
  }
}