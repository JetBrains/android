/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <vector>

#include "common.h"

namespace screensharing {

// Copy on write thread-safe list.
template<typename T>
class CopyOnWriteList {
public:
  CopyOnWriteList() {}

  ~CopyOnWriteList() {
    Clear();
  }

  // Returns the current contents of the list.
  const std::vector<T>& Get() const {
    const std::vector<T>* elements = elements_;
    return elements == nullptr ? empty_vector_ : *elements;
  }

  // Returns the current contents of the list.
  const std::vector<T>& operator*() const {
    return Get();
  }

  // Adds an element to the list. Returns the size of the list immediately after adding.
  size_t Add(T element) {
    for (;;) {
      auto old_elements = elements_.load();
      auto new_elements = old_elements == nullptr ?
                           new std::vector<T>(1, element) :
                           new std::vector<T>(*old_elements);
      if (old_elements != nullptr) {
        new_elements->push_back(element);
      }
      if (elements_.compare_exchange_strong(old_elements, new_elements)) {
        delete old_elements;
        return new_elements->size();
      }
      delete new_elements;
    }
  }

  // Removes an element from the list if it is present there. Returns the size of the list
  // immediately after removal.
  size_t Remove(T element) {
    for (;;) {
      auto old_elements = elements_.load();
      if (old_elements == nullptr) {
        return 0;
      }
      auto new_elements = new std::vector<T>(*old_elements);
      auto pos = std::find(new_elements->begin(), new_elements->end(), element);
      if (pos == new_elements->end()) {
        return new_elements->size();
      }
      new_elements->erase(pos);
      if (new_elements->empty()) {
        delete new_elements;
        new_elements = nullptr;
      }
      if (elements_.compare_exchange_strong(old_elements, new_elements)) {
        delete old_elements;
        return new_elements == nullptr ? 0 : new_elements->size();
      }
      delete new_elements;
    }
  }

  // Clears the list.
  void Clear() {
    delete elements_.exchange(nullptr);
  }

private:
  std::atomic<std::vector<T>*> elements_;
  const std::vector<T> empty_vector_;

  DISALLOW_COPY_AND_ASSIGN(CopyOnWriteList);
};

}  // namespace screensharing
