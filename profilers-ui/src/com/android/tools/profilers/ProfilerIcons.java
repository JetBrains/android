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
package com.android.tools.profilers;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class ProfilerIcons {

  public static final Icon RECORD = load("/icons/record.png");
  public static final Icon STOP_RECORDING = load("/icons/stop-recording.png");
  public static final Icon GARBAGE_EVENT = load("/icons/garbage-event.png");
  public static final Icon BACK_ARROW = load("/icons/back-arrow.png");
  public static final Icon FORCE_GARBAGE_COLLECTION = load("/icons/force-garbage-collection.png");
  public static final Icon HEAP_DUMP = load("/icons/heap-dump.png");
  public static final Icon CLASS_STACK = load("/icons/stack-class.png");
  public static final Icon INTERFACE_STACK = load("/icons/stack-interface.png");
  public static final Icon PACKAGE_STACK = load("/icons/stack-package.png");
  public static final Icon GOTO_LIVE = load("/icons/goto-live.png");
  public static final Icon ZOOM_OUT = load("/icons/zoom-out.png");
  public static final Icon ZOOM_IN = load("/icons/zoom-in.png");
  public static final Icon RESET_ZOOM = load("/icons/reset-zoom.png");

  // Collections of constant, do not instantiate.
  private ProfilerIcons() {}

  private static Icon load(String path) {
    return IconLoader.getIcon(path, ProfilerIcons.class);
  }
}
