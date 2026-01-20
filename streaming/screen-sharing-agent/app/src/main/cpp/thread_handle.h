/*
 * Copyright (C) 2025 The Android Open Source Project
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

#include <exception>
#include <functional>
#include <mutex>
#include <thread>

#include "jvm.h"
#include "log.h"

namespace screensharing {

class ThreadHandle {
public:
  ThreadHandle();
  ~ThreadHandle();

  // Starts a thread associated with this handle.
  void Start(const std::string& thread_name, const std::function<void()>& runnable);
  // Requests the associated thread to stop. The code running in that thread has to check IsStopping periodically.
  void Stop();
  // Joins the tread and mark the handle as stopped. The Stop method has to be called before calling this method.
  void Join();
  // Returns true if Stop was called but Join has not been called or has not returned yet.
  bool IsStopping();

private:
  enum class RunState { STOPPED, RUNNING, STOPPING };

  std::mutex mutex_;
  RunState run_state_ = RunState::STOPPED;  // GUARDED_BY(mutex_)
  std::thread thread_;  // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(ThreadHandle);
};

// Thrown when the agent is being shut down due to an unrecoverable error.
class EmergencyShutdownException : std::exception {
};

std::thread CreateThread(const std::string& thread_name, const std::function<void()>& runnable);

}  // namespace screensharing
