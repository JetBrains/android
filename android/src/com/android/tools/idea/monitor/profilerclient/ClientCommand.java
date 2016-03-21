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
package com.android.tools.idea.monitor.profilerclient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ClientCommand {
  public enum CommandType {
    SYSTEM,
    SEND
  }

  @NotNull public CommandType commandType;
  @Nullable public ProfilerClientListener listener;
  @Nullable public byte[] data;
  public int offset;
  public int length;
  public short subType;
  public byte flags;
  public byte type;
  public boolean waitForResponse;

  public ClientCommand(@NotNull CommandType commandType) {
    this.commandType = commandType;
  }

  public ClientCommand(@NotNull CommandType commandType,
                       @NotNull ProfilerClientListener listener,
                       @Nullable byte[] data,
                       int offset,
                       int length,
                       byte flags,
                       byte type,
                       short subType,
                       boolean waitForResponse) {
    this.commandType = commandType;
    this.listener = listener;
    this.data = data;
    this.offset = offset;
    this.length = length;
    this.flags = flags;
    this.type = type;
    this.subType = subType;
    this.waitForResponse = waitForResponse;
  }
}
