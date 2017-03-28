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
package com.android.tools.idea.monitor.ui.network.view;

import com.intellij.ui.JBColor;

import java.awt.*;

public final class Constants {

  public static final Color NETWORK_CONNECTIONS_COLOR = new JBColor(new Color(0x5A8725), new Color(0x5A8725));

  public static final Color NETWORK_RECEIVING_COLOR = new JBColor(new Color(0x2865BD), new Color(0x2865BD));

  public static final Color NETWORK_SENDING_COLOR = new JBColor(new Color(0xFF7B00), new Color(0xFF7B00));

  public static final Color NETWORK_WAITING_COLOR = new JBColor(new Color(0xAAAAAA), new Color(0xAAAAAA));

  private Constants() {
  }
}
