/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.proto;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.Interner;
import com.google.idea.blaze.common.Interners;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Eliminates duplicate strings inside a proto message by interning them.
 *
 * <p>All fields in a proto message are examined and strings passed through {@link Interners} to
 * ensure that identical strings use the same instance in memory. This then allows duplicated
 * strings to be garbage collected.
 */
public class ProtoStringInterner {

  private ProtoStringInterner() {}

  @SuppressWarnings("unchecked") // casts of proto field values should be safe.
  public static <T extends Message> T intern(T message) {
    Interner<String> stringInterner = Interners.STRING;
    if (message == null) {
      return null;
    }
    Message.Builder builder = null;
    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      Object original = message.getField(field);
      Object interned = original;
      switch (field.getType()) {
        case STRING:
          if (field.isRepeated()) {
            List<String> repeated = (List<String>) message.getField(field);
            if (!repeated.isEmpty()) {
              interned = repeated.stream().map(stringInterner::intern).collect(toImmutableList());
            }
          } else if (message.hasField(field)) {
            interned = stringInterner.intern((String) message.getField(field));
          }
          break;
        case MESSAGE:
        case GROUP:
          if (field.isRepeated()) {
            List<Message> originalList = (List<Message>) original;
            List<Message> internedList =
                originalList.stream().map(ProtoStringInterner::intern).collect(toImmutableList());
            if (!IntStream.range(0, internedList.size())
                .allMatch(i -> internedList.get(i) == originalList.get(i))) {
              interned = internedList;
            }
          } else if (message.hasField(field)) {
            interned = intern((Message) original);
          }
          break;
        default:
          // Nothing needs doing.
      }
      if (original != interned) {
        if (builder == null) {
          builder = message.toBuilder();
        }
        builder.setField(field, interned);
      }
    }
    if (builder == null) {
      return message;
    }
    return (T) builder.build();
  }
}
