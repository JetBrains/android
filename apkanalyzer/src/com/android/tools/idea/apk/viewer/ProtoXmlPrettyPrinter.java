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
package com.android.tools.idea.apk.viewer;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;

/**
 * Handle XML content stored as the proto format defined in android/frameworks/base/tools/aapt2/Resources.proto.
 */
public interface ProtoXmlPrettyPrinter {
  /**
   * Converts an XML resource proto document into a pretty printed XML document string.
   * Throws if the argument is not a valid XML resource proto.
   */
  @NotNull
  String prettyPrint(@NotNull byte[] content) throws IOException;
}
