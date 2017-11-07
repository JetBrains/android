/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser;

/**
 * A parser for BUILD.gradle files. Used to build up a {@link GradleBuildModel} from the underlying file.
 *
 * Standard implementations of {@link GradleDslParser} should allow the setting of a {@link GradleDslFile} (e.g as a constructor argument),
 * when {@link #parse()} is called the parser should set the properties obtained on the {@link GradleDslFile}.
 *
 * This interface aims to allow the {@link GradleBuildModel} to support different languages, each language should have its
 * own implementation of a {@link GradleDslParser}.
 */
public interface GradleDslParser {
  void parse();

  /**
   * Instructs the parser perform its parsing operation.
   */
  class Adapter implements GradleDslParser {
    @Override
    public void parse() { }
  }
}
