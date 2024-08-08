/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.processhandler;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import java.io.IOException;

/** Writes output from a process to a stream */
public final class LineProcessingProcessAdapter extends ProcessAdapter {
  private final LineProcessingOutputStream myOutputStream;

  public LineProcessingProcessAdapter(LineProcessingOutputStream outputStream) {
    myOutputStream = outputStream;
  }

  @SuppressWarnings({"rawtypes"})
  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
    String text = event.getText();
    if (text != null) {
      try {
        myOutputStream.write(text.getBytes(UTF_8));
      } catch (IOException e) {
        // Ignore -- cannot happen
      }
    }
  }
}
