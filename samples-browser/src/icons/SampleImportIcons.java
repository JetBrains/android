/*
 * Copyright (C) 2023 The Android Open Source Project
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
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public final class SampleImportIcons {

  public static class Welcome {
    public static final Icon IMPORT_CODE_SAMPLE = load("/icons/welcome/importAndroidCodeSample.png"); // 16x16
  }

  private static Icon load(String path) {
    return IconLoader.getIcon(path, SampleImportIcons.class);
  }

  private SampleImportIcons() {
    // This utility class should not be instantiated.
  }
}
