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

#include "token_iterator.h"

namespace screensharing {

using namespace std;

TokenIterator::TokenIterator(const string& original, char delimiter) {
  buffer_ = original;
  delimiters_.push_back(delimiter);
  delimiters_.push_back(0);
}

bool TokenIterator::has_next() {
  return buffer_.length() > max(0, end_index_);
}

bool TokenIterator::has_prev() {
  return index_ > 0;
}

const char* TokenIterator::next() {
  if (!has_next()) {
    return nullptr;
  }
  index_ = end_index_ + 1;
  end_index_ = buffer_.find_first_of(delimiters_, index_);
  if (end_index_ == string::npos) {
    end_index_ = buffer_.length();
  }
  buffer_[end_index_] = 0;
  return &buffer_.c_str()[index_];
}

const char* TokenIterator::prev() {
  if (!has_prev()) {
    return nullptr;
  }
  index_--;
  end_index_ = index_;
  while (index_ > 0 && buffer_[--index_] != 0);
  if (index_ > 0) {
    index_++;
  }
  return &buffer_.c_str()[index_];
}

}  // namespace screensharing
