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

#include "concurrent_list.h"

namespace screensharing::impl {

using namespace std;

size_t Impl::Add(void* element) {
  unique_lock lock(mutex_);
  auto old_elements = elements_;
  auto new_elements = old_elements == nullptr ?
                      new vector<void*>(1, element) :
                      new vector<void*>(*old_elements);
  if (old_elements != nullptr) {
    new_elements->push_back(element);
  }
  elements_ = new_elements;
  delete old_elements;
  return new_elements->size();
}

size_t Impl::Remove(void* element) {
  unique_lock lock(mutex_);
  auto old_elements = elements_;
  if (old_elements == nullptr) {
    return 0;
  }
  auto new_elements = new vector<void*>(*old_elements);
  auto pos = find(new_elements->begin(), new_elements->end(), element);
  if (pos == new_elements->end()) {
    return new_elements->size();
  }
  new_elements->erase(pos);
  if (new_elements->empty()) {
    delete new_elements;
    new_elements = nullptr;
  }
  elements_ = new_elements;
  delete old_elements;
  return new_elements == nullptr ? 0 : new_elements->size();
}

void Impl::ClearList() {
  unique_lock lock(mutex_);
  auto old_elements = elements_;
  elements_ = nullptr;
  delete old_elements;
}

}  // namespace screensharing::impl
