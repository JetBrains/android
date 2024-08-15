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
package com.google.idea.blaze.common;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.Nonnull;

/** Output that can be printed to a log. */
public class PrintOutput implements Output {

  @Nonnull private final String text;

  @Nonnull private final OutputType outputType;

  /** The output type */
  public enum OutputType {
    NORMAL,
    LOGGED,
    ERROR
  }

  public PrintOutput(@Nonnull String text, @Nonnull OutputType outputType) {
    this.text = text;
    this.outputType = outputType;
  }

  public PrintOutput(@Nonnull String text) {
    this(text, OutputType.NORMAL);
  }

  @Nonnull
  public String getText() {
    return text;
  }

  @Override
  public String toString() {
    return getText();
  }

  @Nonnull
  public OutputType getOutputType() {
    return outputType;
  }

  public static PrintOutput output(String text) {
    return new PrintOutput(text);
  }

  @FormatMethod
  public static PrintOutput output(@FormatString String text, Object... args) {
    return new PrintOutput(String.format(text, args));
  }

  public static PrintOutput log(String text) {
    return new PrintOutput(text, OutputType.LOGGED);
  }

  @FormatMethod
  public static PrintOutput log(@FormatString String text, Object... args) {
    return new PrintOutput(String.format(text, args), OutputType.LOGGED);
  }

  public static PrintOutput error(String text) {
    return new PrintOutput(text, OutputType.ERROR);
  }

  @FormatMethod
  public static PrintOutput error(@FormatString String text, Object... args) {
    return new PrintOutput(String.format(text, args), OutputType.ERROR);
  }
}
