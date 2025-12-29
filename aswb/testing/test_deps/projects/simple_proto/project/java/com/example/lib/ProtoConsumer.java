/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.lib;

import com.example.external.ExternalMessage;
import com.example.lib.LibEdition2024Proto.LibMessageEdition2024DisableMultiFile;
import com.example.lib.LibMessageEdition2024EnableMultiFile;

/** An example proto consumer. */
public class ProtoConsumer {

  public ProtoConsumer() {
    LibMessage message =
        LibMessage.newBuilder()
            .setMessage("abc")
            .setExternalMessage(ExternalMessage.newBuilder().setMessage("xyz").build())
            .build();
    LibMessageEdition2024DisableMultiFile messageEdition2024DisableMultiFile =
      LibMessageEdition2024DisableMultiFile.newBuilder()
        .setMessage("abc")
        .build();
    LibMessageEdition2024EnableMultiFile messageEdition2024EnableMultiFile =
      LibMessageEdition2024EnableMultiFile.newBuilder()
        .setMessage("abc")
        .build();
    System.out.println(message.getMessage());
    System.out.println(message.getExternalMessage().getMessage());
    System.out.println(messageEdition2024DisableMultiFile.getMessage());
    System.out.println(messageEdition2024EnableMultiFile.getMessage());
  }
}
