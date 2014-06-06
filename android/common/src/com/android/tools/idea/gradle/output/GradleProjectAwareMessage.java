/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.output;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Message produced by Gradle when building a project, just like {@link GradleMessage} with the difference that this message does not
 * contain the path of the file originating the message, but the Gradle path of the project instead.
 */
public class GradleProjectAwareMessage extends GradleMessage {
  @NotNull private final String myGradlePath;

  public GradleProjectAwareMessage(@NotNull Kind kind, @NotNull String text, @NotNull String gradlePath) {
    super(kind, text);
    myGradlePath = gradlePath;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "kind=" + getKind() +
           ", text=" + StringUtil.wrapWithDoubleQuote(getText()) +
           ", gradlePath=" + myGradlePath +
           ']';
  }
}
