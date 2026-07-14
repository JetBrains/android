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
package com.example.android;

import com.example.external.ExternalMessage;
import com.example.lib.LibMessage;
import com.example.lib.LibMessage.LibEnum;
import com.example.lib.LibEdition2024OuterClass.MessageManager;
import com.example.lib.LibEdition2024OuterClass.MessageContent;
import com.example.lib.LibEdition2024Proto.LibMessageEdition2024DisableMultiFile;
import com.example.lib.LibMessageEdition2024EnableMultiFile;

/**
 * An example proto consumer.
 */
public class ProtoConsumer {

  public MessageContent getMessage() {
    MessageManager message =
      MessageManager.newBuilder()
        .setMessage(MessageContent.newBuilder()
                      .setContent("abc")
                      .build())
        .build();
    System.out.println(LibEnum.values());
    System.out.println(LibEnum.valueOf(0));
    System.out.println(LibEnum.valueOf("VALUE_A"));
    System.out.println(LibEnum.forNumber(1));
    System.out.println(LibEnum.internalGetValueMap());
    return message.getMessage();
  }

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
    LibMessageEdition2024EnableMultiFile.Builder builder =
      LibMessageEdition2024EnableMultiFile.newBuilder()
        .setMessage("abc")
        .setLibEnum(LibMessageEdition2024EnableMultiFile.LibEnum.VALUE_A)
        .addRepeatedStrings("s1")
        .addRepeatedStrings("s2")
        .setChoiceA("choice_a_value");

    LibMessageEdition2024EnableMultiFile.NestedMessage nested =
      LibMessageEdition2024EnableMultiFile.NestedMessage.newBuilder()
        .setNestedContent("nested_abc")
        .build();

    builder.setNestedMessage(nested);
    builder.addRepeatedNestedMessages(nested);
    builder.addRepeatedNestedMessages(LibMessageEdition2024EnableMultiFile.NestedMessage.newBuilder().setNestedContent("nested_builder"));
    builder.setRepeatedNestedMessages(0, nested);
    builder.setRepeatedNestedMessages(1, LibMessageEdition2024EnableMultiFile.NestedMessage.newBuilder().setNestedContent("nested_builder_set"));
    builder.removeRepeatedNestedMessages(0);

    LibMessageEdition2024EnableMultiFile messageEdition2024EnableMultiFile = builder.build();

    LibMessageEdition2024EnableMultiFile.getDefaultInstance();
    System.out.println(messageEdition2024EnableMultiFile.getMessage());
    System.out.println(messageEdition2024EnableMultiFile.hasMessage());

    System.out.println(messageEdition2024EnableMultiFile.getLibEnum());
    System.out.println(messageEdition2024EnableMultiFile.getLibEnumValue());
    System.out.println(LibMessageEdition2024EnableMultiFile.LibEnum.values());
    System.out.println(LibMessageEdition2024EnableMultiFile.LibEnum.valueOf(1));
    System.out.println(LibMessageEdition2024EnableMultiFile.LibEnum.valueOf("VALUE_A"));
    System.out.println(LibMessageEdition2024EnableMultiFile.LibEnum.forNumber(1));
    System.out.println(LibMessageEdition2024EnableMultiFile.LibEnum.internalGetValueMap());

    System.out.println(messageEdition2024EnableMultiFile.getRepeatedStringsList());
    System.out.println(messageEdition2024EnableMultiFile.getRepeatedStringsCount());
    System.out.println(messageEdition2024EnableMultiFile.getRepeatedStrings(0));

    System.out.println(messageEdition2024EnableMultiFile.getChoiceCase());
    System.out.println(messageEdition2024EnableMultiFile.hasChoiceA());
    System.out.println(messageEdition2024EnableMultiFile.getChoiceA());

    System.out.println(messageEdition2024EnableMultiFile.getNestedMessage());
    System.out.println(messageEdition2024EnableMultiFile.hasNestedMessage());

    System.out.println(messageEdition2024EnableMultiFile.getRepeatedNestedMessagesList());
    System.out.println(messageEdition2024EnableMultiFile.getRepeatedNestedMessagesCount());
    System.out.println(messageEdition2024EnableMultiFile.getRepeatedNestedMessages(0));

  }
}
