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
package com.android.tools.idea.whatsnew.assistant;

import com.android.tools.idea.assistant.DefaultTutorialBundle;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * What's New Assistant needs a custom bundle for the special version field,
 * which will be used to automatically open on startup if Android Studio
 * version is the same but WNA config is higher version
 */
public class WhatsNewAssistantBundle extends DefaultTutorialBundle {
  @XmlAttribute(name = "version")
  private int myVersion = -1;

  public int getVersion() {
      return myVersion;
  }
}
