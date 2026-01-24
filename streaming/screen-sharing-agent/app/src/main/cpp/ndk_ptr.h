/*
 * Copyright (C) 2026 The Android Open Source Project
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

namespace screensharing {

template<typename NdkType>
void NdkDelete(NdkType* ptr);

// Smart pointer to an NDK object.
template<typename NdkType>
class NdkPtr {
public:
  // Initializes a pointer not associated with any object.
  NdkPtr() noexcept = default;
  // Initializes a pointer associated with the given object.
  NdkPtr(NdkType* ptr) noexcept
      : pointer_(ptr) {}
  NdkPtr(NdkPtr<NdkType>&& other) noexcept
      : pointer_(other.pointer_) {
    other.pointer_ = nullptr;
  }
  ~NdkPtr() {
    Reset();
  }

  // Replaces the managed object.
  void Reset(NdkType* ptr = nullptr) {
    if (pointer_ != nullptr && pointer_ != ptr) {
      NdkDelete(pointer_);
    }
    pointer_ = ptr;
  }

  NdkPtr& operator=(NdkType* ptr) {
    Reset(ptr);
    return *this;
  }

  NdkPtr& operator=(NdkPtr&& other) {
    Reset(other.pointer_);
    other.pointer_ = nullptr;
    return *this;
  }

  bool operator==(nullptr_t) {
    return pointer_ == nullptr;
  }

  bool operator!=(nullptr_t) {
    return pointer_ != nullptr;
  }

  bool IsNull() const {
    return pointer_ == nullptr;
  }

  bool IsNotNull() const {
    return pointer_ != nullptr;
  }

  operator NdkType*() noexcept { return pointer_; }
  operator const NdkType*() const noexcept { return pointer_; }

  NdkType** operator &() noexcept { return &pointer_; }
  NdkType* Get() noexcept { return pointer_; }
  const NdkType* Get() const noexcept { return pointer_; }

private:
  NdkType* pointer_ = nullptr;

  NdkPtr(NdkPtr const&) = delete;
  void operator=(NdkPtr const&) = delete;
};

}  // namespace screensharing
