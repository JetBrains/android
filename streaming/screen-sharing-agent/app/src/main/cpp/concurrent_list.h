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

#include <mutex>
#include <vector>

#include "common.h"

namespace screensharing {

namespace impl {

class Impl {
public:
  Impl() = default;

  ~Impl() {
    ClearList();
  }

protected:
  size_t Add(void* element);
  size_t Remove(void* element);
  void ClearList();

  std::recursive_mutex mutex_;
  std::vector<void*>* elements_ = nullptr;  // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(Impl);
};

}  // namespace impl

// Thread-safe list.
template<typename T>
class ConcurrentList : private impl::Impl {
public:
  ConcurrentList() = default;

  // Iterates over the list calling fun for each element.
  template<typename F>
  void ForEach(F fun) {
    std::unique_lock lock(mutex_);
    if (elements_ != nullptr) {
      for (auto element: *elements_) {
        fun(static_cast<T*>(element));
      }
    }
  }

  // Adds an element to the list. Returns the size of the list immediately after adding.
  // Safe to call while iterating over the list.
  size_t Add(T* element) {
    return Impl::Add(element);
  }

  // Removes an element from the list if it is present there. Returns the size of the list
  // immediately after removal. Safe to call while iterating over the list.
  size_t Remove(T* element) {
    return Impl::Remove(element);
  }

  void Clear() {
    Impl::ClearList();
  }

private:
  DISALLOW_COPY_AND_ASSIGN(ConcurrentList);
};

}  // namespace screensharing
