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

#include "agent.h"

#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/un.h>
#include <unistd.h>
#include <sys/system_properties.h>

#include <cassert>
#include <chrono>
#include <cstdlib>
#include <cstring>

#include "accessors/service_manager.h"
#include "flags.h"
#include "log.h"
#include "session_environment.h"

namespace screensharing {

using namespace std;
using namespace std::chrono;

const char ATTRIBUTION_TAG[] = "studio.screen.sharing";

namespace {

constexpr int CHANNEL_HEADER_LENGTH = 20;

int CreateAndConnectSocket(const string& socket_name) {
  int socket_fd = socket(AF_UNIX, SOCK_STREAM, 0);
  if (socket_fd < 0) {
    Log::Fatal(SOCKET_CONNECTIVITY_ERROR, "Failed to create a socket");
  }
  sockaddr_un address = { AF_UNIX, "" };
  // An abstract socket address is distinguished by a null byte in front of the socket name
  // and doesn't need a null terminator. See https://man7.org/linux/man-pages/man7/unix.7.html.
  if (socket_name.size() > sizeof(address.sun_path) - 2) {
    Log::Fatal(SOCKET_CONNECTIVITY_ERROR, "Socket name \"%s\" is too long", socket_name.c_str());
  }
  strncpy(address.sun_path + 1, socket_name.c_str(), sizeof(address.sun_path) - 2);
  int len = sizeof(sa_family_t) + 1 + socket_name.size();
  int ret = connect(socket_fd, (const struct sockaddr*) &address, len);
  if (ret < 0) {
    close(socket_fd);
    Log::Fatal(SOCKET_CONNECTIVITY_ERROR, "Failed to connect to socket \"%s\" - %s", socket_name.c_str(), strerror(errno));
  }
  return socket_fd;
}

[[noreturn]] void InvalidCommandLineArgument(const string& arg) {
  Log::Fatal(INVALID_COMMAND_LINE, "Invalid command line argument: \"%s\"", arg.c_str());
}

void sighup_handler(int signal_number) {
  Agent::Shutdown();
}

CodecInfo* SelectVideoEncoder(const string& mime_type) {
  Jni jni = Jvm::GetJni();
  JClass clazz = jni.GetClass("com/android/tools/screensharing/CodecInfo");
  jmethodID method = clazz.GetStaticMethod("selectVideoEncoderForType",
                                           "(Ljava/lang/String;)Lcom/android/tools/screensharing/CodecInfo;");
  JObject codec_info = clazz.CallStaticObjectMethod(method, JString(jni, mime_type).ref());
  if (codec_info.IsNull()) {
    Log::Fatal(VIDEO_ENCODER_NOT_FOUND, "No video encoder is available for %s", mime_type.c_str());
  }
  JString jname = JString(codec_info.GetObjectField(clazz.GetFieldId("name", "Ljava/lang/String;")));
  string codec_name = jname.IsNull() ? "<unnamed>" : jname.GetValue();
  int max_width = codec_info.GetIntField(clazz.GetFieldId("maxWidth", "I"));
  int max_height = codec_info.GetIntField(clazz.GetFieldId("maxHeight", "I"));
  int width_alignment = codec_info.GetIntField(clazz.GetFieldId("widthAlignment", "I"));
  int height_alignment = codec_info.GetIntField(clazz.GetFieldId("heightAlignment", "I"));
  int max_frame_rate = codec_info.GetIntField(clazz.GetFieldId("maxFrameRate", "I"));
  return new CodecInfo(mime_type, codec_name, Size(max_width, max_height), Size(width_alignment, height_alignment), max_frame_rate);
}

void WriteChannelHeader(const string& codec_name, int socket_fd) {
  string buf;
  int buf_size = 1 + CHANNEL_HEADER_LENGTH;
  buf.reserve(buf_size);  // Single-byte channel marker followed by header.
  buf.append("V");  // Video channel marker.
  buf.append(codec_name);
  // Pad with spaces to the fixed length.
  while (buf.length() < buf_size) {
    buf.insert(buf.end(), ' ');
  }
  if (write(socket_fd, buf.c_str(), buf_size) != buf_size) {
    if (errno != EBADF && errno != EPIPE) {
      Log::Fatal(SOCKET_IO_ERROR, "Error writing to video socket - %s", strerror(errno));
    }
    Agent::Shutdown();
  }
}

int GetFeatureLevel() {
  int api_level = android_get_device_api_level();
  char codename[PROP_VALUE_MAX] = { 0 };
  if (__system_property_get("ro.build.version.codename", codename) < 1) {
    return api_level;
  }
  return *codename == '\0' || strcmp(codename, "REL") == 0 ? api_level : api_level + 1;
}

string GetBuildCharacteristics() {
  char result[PROP_VALUE_MAX] = { 0 };
  if (__system_property_get("ro.build.characteristics", result) < 1) {
    return "";
  }
  return result;
}

}  // namespace

void Agent::Initialize(const vector<string>& args) {
  for (int i = 1; i < args.size(); i++) {
    const string& arg = args[i];
    if (arg.rfind("--socket=", 0) == 0) {
      socket_name_ = arg.substr(sizeof("--socket=") - 1, arg.size());
    } else if (arg.rfind("--log=", 0) == 0) {
      auto value = arg.substr(sizeof("--log=") - 1, arg.size());
      if (value == "verbose") {
        Log::SetLevel(Log::Level::VERBOSE);
      } else if (value == "debug") {
        Log::SetLevel(Log::Level::DEBUG);
      } else if (value == "info") {
        Log::SetLevel(Log::Level::INFO);
      } else if (value == "warn") {
        Log::SetLevel(Log::Level::WARN);
      } else if (value == "error") {
        Log::SetLevel(Log::Level::ERROR);
      } else {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--max_size=", 0) == 0) {
      char* ptr;
      auto w = strtoul(arg.c_str() + sizeof("--max_size=") - 1, &ptr, 10);
      auto h = *ptr == ',' ? strtoul(ptr + 1, &ptr, 10) : 0;
      if (*ptr != '\0' || w <= 0 || numeric_limits<int32_t>::max() < w || h <= 0 || numeric_limits<int32_t>::max() < h) {
        InvalidCommandLineArgument(arg);
      }
      max_video_resolution_.width = w;
      max_video_resolution_.height = h;
    } else if (arg.rfind("--orientation=", 0) == 0) {
      char* ptr;
      auto orientation = strtoul(arg.c_str() + sizeof("--orientation=") - 1, &ptr, 10);
      if (*ptr != '\0') {
        InvalidCommandLineArgument(arg);
      }
      initial_video_orientation_ = orientation & 0x03;
    } else if (arg.rfind("--flags=", 0) == 0) {
      char* ptr;
      flags_ = strtoul(arg.c_str() + sizeof("--flags=") - 1, &ptr, 10);
      if (*ptr != '\0') {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--max_bit_rate=", 0) == 0) {
      char* ptr;
      max_bit_rate_ = strtoul(arg.c_str() + sizeof("--max_bit_rate=") - 1, &ptr, 10);
      if (*ptr != '\0') {
        InvalidCommandLineArgument(arg);
      }
    } else if (arg.rfind("--codec=", 0) == 0) {
      codec_name_ = arg.substr(sizeof("--codec=") - 1, arg.size());
    } else if (!arg.empty()) {  // For some unclear reason some command line arguments are empty strings.
      InvalidCommandLineArgument(arg);
    }
  }

  feature_level_ = GetFeatureLevel();
}

void Agent::Run(const vector<string>& args) {
  Initialize(args);

  struct sigaction action = { .sa_handler = sighup_handler };
  int res = sigaction(SIGHUP, &action, nullptr);
  if (res < 0) {
    Log::E("Unable to set SIGHUP handler - sigaction returned %d", res);
  }

  assert(display_streamers_.empty());
  video_socket_fd_ = CreateAndConnectSocket(socket_name_);
  control_socket_fd_ = CreateAndConnectSocket(socket_name_);
  string mime_type = (codec_name_.compare(0, 2, "vp") == 0 ? "video/x-vnd.on2." : "video/") + codec_name_;
  codec_info_ = SelectVideoEncoder(mime_type);
  WriteChannelHeader(codec_name_, video_socket_fd_);

  Log::D("Using %s video encoder with %dx%d max resolution",
         codec_info_->name.c_str(), codec_info_->max_resolution.width, codec_info_->max_resolution.height);
  auto ret = display_streamers_.try_emplace(
      PRIMARY_DISPLAY_ID,
      PRIMARY_DISPLAY_ID, codec_info_, max_video_resolution_, initial_video_orientation_, max_bit_rate_, video_socket_fd_);
  primary_display_streamer_ = &ret.first->second;
  controller_ = new Controller(control_socket_fd_);
  Log::D("Created video and control sockets");
  if ((flags_ & START_VIDEO_STREAM) != 0) {
    primary_display_streamer_->Start();
  }
  controller_->Run();
  Shutdown();
}

void Agent::StartVideoStream(int32_t display_id, Size max_video_resolution) {
  DisplayStreamer* display_streamer;
  bool created;
  if (display_id == PRIMARY_DISPLAY_ID) {
    display_streamer = primary_display_streamer_;
    created = false;
  } else {
    auto ret = display_streamers_.try_emplace(
        display_id,
        display_id, codec_info_, max_video_resolution, DisplayStreamer::CURRENT_DISPLAY_ORIENTATION,
        primary_display_streamer_->bit_rate(), video_socket_fd_);
    display_streamer = &ret.first->second;
    created = ret.second;
  }
  if (!created) {
    display_streamer->SetMaxVideoResolution(max_video_resolution);
  }
  display_streamer->Start();
}

void Agent::StopVideoStream(int32_t display_id) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    display_streamer.Stop();
    if (display_id != PRIMARY_DISPLAY_ID) {
      display_streamers_.erase(it);
    }
  }
}

void Agent::SetVideoOrientation(int32_t display_id, int32_t orientation) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    display_streamer.SetVideoOrientation(orientation);
  }
}

void Agent::SetMaxVideoResolution(int32_t display_id, Size max_video_resolution) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    display_streamer.SetMaxVideoResolution(max_video_resolution);
  }
}

DisplayInfo Agent::GetDisplayInfo(int32_t display_id) {
  auto it = display_streamers_.find(display_id);
  if (it != display_streamers_.end()) {
    DisplayStreamer& display_streamer = it->second;
    return display_streamer.GetDisplayInfo();
  }
  return DisplayManager::GetDisplayInfo(Jvm::GetJni(), display_id);
}

void Agent::InitializeSessionEnvironment() {
  ServiceManager::GetService(Jvm::GetJni(), "settings");  // Wait for the "settings" service to initialize.
  unique_lock lock(environment_mutex_);
  session_environment_ = new SessionEnvironment((flags_ & TURN_OFF_DISPLAY_WHILE_MIRRORING) != 0);
}

void Agent::RestoreEnvironment() {
  unique_lock lock(environment_mutex_);
  delete session_environment_;
  session_environment_ = nullptr;
}

void Agent::Shutdown() {
  if (!shutting_down_.exchange(true)) {
    for (auto& it : display_streamers_) {
      it.second.Stop();
    }
    DisplayManager::RemoveAllDisplayListeners(Jvm::GetJni());
    if (controller_ != nullptr) {
      controller_->Stop();
    }
    close(video_socket_fd_);
    RestoreEnvironment();
  }
}

int64_t Agent::GetLastTouchEventTime() {
  return last_touch_time_millis_;
}

void Agent::RecordTouchEvent() {
  last_touch_time_millis_ = duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

bool Agent::HasBuildCharacteristic(const char* characteristic) {
  string characteristics = GetBuildCharacteristics();
  auto len = strlen(characteristic);
  string::size_type p = 0;
  while (true) {
    if (characteristics.compare(p, len, characteristic) == 0) {
      auto end = p + len;
      if (characteristics.length() == end || characteristics[end] == ',') {
        Log::D("Agent::HasBuildCharacteristic(\"%s\") returned true", characteristic);
        return true;
      }
    }
    p = characteristics.find(',', p);
    if (p == string::npos) {
      break;
    }
    ++p;
  }
  Log::D("Agent::HasBuildCharacteristic(\"%s\") returned false", characteristic);
  return false;
}

bool Agent::IsWatch() {
  return HasBuildCharacteristic("watch");
}

int32_t Agent::feature_level_(0);
string Agent::socket_name_("screen-sharing-agent");
Size Agent::max_video_resolution_(numeric_limits<int32_t>::max(), numeric_limits<int32_t>::max());
int32_t Agent::initial_video_orientation_(-1);
int32_t Agent::max_bit_rate_(0);
string Agent::codec_name_("vp8");
CodecInfo* Agent::codec_info_(nullptr);
int32_t Agent::flags_(0);
int Agent::video_socket_fd_(0);
int Agent::control_socket_fd_(0);
map<int32_t, DisplayStreamer> Agent::display_streamers_;
DisplayStreamer* Agent::primary_display_streamer_(nullptr);
Controller* Agent::controller_(nullptr);
mutex Agent::environment_mutex_;
SessionEnvironment* Agent::session_environment_(nullptr);  // GUARDED_BY(environment_mutex_)
atomic_int64_t Agent::last_touch_time_millis_(0);
atomic_bool Agent::shutting_down_(false);

}  // namespace screensharing
