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
package com.android.tools.tests;

import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

public class XDisplayRule extends ExternalResource {

  @NotNull
  private final XvfbServer xServer;

  public XDisplayRule() {
    // This needs to be done in the constructor and not in "before", to make sure the display
    // is initialized before AWT tries to use it.
    xServer = new XvfbServer();
  }

  @Override
  protected void after() {
    xServer.kill();
  }
}
