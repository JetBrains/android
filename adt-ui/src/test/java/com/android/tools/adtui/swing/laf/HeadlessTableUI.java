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
package com.android.tools.adtui.swing.laf;

import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeMouse;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTableUI;
import java.awt.*;

/**
 * A stubbed {@link BasicTableUI} for use in headless unit tests, where some functionality is
 * removed to avoid making calls that would otherwise throw a {@link HeadlessException}. This will
 * allow you to interact with {@link JTable} components using {@link FakeMouse} and
 * {@link FakeKeyboard}.
 *
 * To use, you must remember to call {@code someTable.setUI(new HeadlessTableUI()} before calling
 * methods like {@link FakeMouse#click(int, int)} or {@link FakeKeyboard#press(FakeKeyboard.Key)}.
 *
 * NOTE: Changing the UI of a component can subtly change its behavior! This class may need to be
 * updated in the future to add more functionality, so it more closely matches its parent class.
 */
public class HeadlessTableUI extends BasicTableUI {
  @Override
  protected void installListeners() {
  }

  @Override
  protected void uninstallListeners() {
  }
}
