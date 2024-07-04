/*
 * Copyright (C) 2024 The Android Open Source Project
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

#pragma once

#include <string>

namespace screensharing {

// Given a string and separators, provides an iterator that can navigate back and forth in the string elements.
class TokenIterator {
public:
  TokenIterator(const std::string& original, char delimiter = '\n');

  // Returns true if this iterator has more elements when traversing the list in the forward direction.
  bool has_next();

  // Returns the next element in the list and advances the cursor position.
  const char* next();

  // Returns true if this iterator has more elements when traversing the list in the reverse direction.
  bool has_prev();

  // Returns the previous element in the list and moves the cursor position backwards.
  const char* prev();

private:
  // A copy of the original string. Some delimiters may have been replaced with 0.
  std::string buffer_;

  // The delimiters to search for.
  std::string delimiters_;

  // The starting index of the current token in the buffer. -1 is used for position before the first token.
  int index_ = -1;

  // The ending index + 1 of the current token in the buffer. The value buffer.length() is used for the last token.
  int end_index_ = -1;
};

}  // namespace screensharing
