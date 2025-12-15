/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "thread_handle.h"

#include <cassert>
#include <condition_variable>

namespace screensharing {

using namespace std;

ThreadHandle::ThreadHandle() = default;

ThreadHandle::~ThreadHandle() {
  Stop();
  Join();
}

void ThreadHandle::Start(const std::string& thread_name, const std::function<void()>& runnable) {
  std::unique_lock lock(mutex_);
  if (run_state_ == RunState::RUNNING) {
    return;
  }
  if (run_state_ == RunState::STOPPING) {
    lock.unlock();
    Join();
    lock.lock();
  }
  if (run_state_ == RunState::STOPPED) {
    thread_ = CreateThread(thread_name, runnable);
    run_state_ = RunState::RUNNING;
  }
}

void ThreadHandle::Stop() {
  unique_lock lock(mutex_);
  if (run_state_ == RunState::RUNNING) {
    run_state_ = RunState::STOPPING;
  }
}

void ThreadHandle::Join() {
  unique_lock lock(mutex_);
  if (run_state_ == RunState::STOPPING) {
    if (thread_.joinable()) {
      lock.unlock();
      thread_.join();
      lock.lock();
    }
    if (run_state_ == RunState::STOPPING) {
      run_state_ = RunState::STOPPED;
    }
  }
}

bool ThreadHandle::IsStopping() {
  unique_lock lock(mutex_);
  return run_state_ == RunState::STOPPING;
}

thread CreateThread(const string& thread_name, const std::function<void()>& runnable) {
  return std::thread([thread_name, runnable]() {
    Jvm::AttachCurrentThread(thread_name.c_str());
    try {
      runnable();
    } catch (const EmergencyShutdownException& e) {
    }
    Jvm::DetachCurrentThread();
    Log::D("%s thread terminated", thread_name.c_str());
  });
}

}  // namespace screensharing
