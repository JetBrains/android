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

#include "remote_submix_reader.h"

#include "agent.h"
#include "codec_input_buffer.h"
#include "jvm.h"
#include "log.h"
#include "string_printf.h"

namespace screensharing {

using namespace std;

namespace {

// Audio channel mask definitions added to AAudio.h in API level 32.
enum {
    AAUDIO_CHANNEL_FRONT_LEFT = 1 << 0,
    AAUDIO_CHANNEL_FRONT_RIGHT = 1 << 1,
    AAUDIO_CHANNEL_STEREO = AAUDIO_CHANNEL_FRONT_LEFT | AAUDIO_CHANNEL_FRONT_RIGHT,
};

constexpr int TYPE_REMOTE_SUBMIX = 25;  // See https://developer.android.com/reference/android/media/AudioDeviceInfo#TYPE_REMOTE_SUBMIX

constexpr int MAX_SUBSEQUENT_ERRORS = 5;
constexpr int AUDIO_FORMAT = AAUDIO_FORMAT_PCM_I16;

// From https://developer.android.com/reference/android/media/AudioManager.
constexpr int AudioManager_GET_DEVICES_INPUTS = 1;

int32_t GetRemoteSubmixDeviceId(Jni jni) {
  JClass audio_manager_class = jni.GetClass("android/media/AudioManager");
  jmethodID method = audio_manager_class.GetStaticMethod("getDevicesStatic", "(I)[Landroid/media/AudioDeviceInfo;");
  JObjectArray devices(audio_manager_class.CallStaticObjectMethod(method, AudioManager_GET_DEVICES_INPUTS));
  auto length = devices.GetLength();
  jmethodID get_type_method;
  jmethodID get_id_method;
  for (int i = 0; i < length; ++i) {
    JObject device = devices.GetElement(i);
    if (i == 0) {
      JClass audio_device_info_class = device.GetClass();
      get_type_method = audio_device_info_class.GetMethod("getType", "()I");
      get_id_method = audio_device_info_class.GetMethod("getId", "()I");
    }
    if (device.CallIntMethod(get_type_method) == TYPE_REMOTE_SUBMIX) {
      return device.CallIntMethod(get_id_method);
    }
  }
  return -1;
}

}  // namespace

RemoteSubmixReader::RemoteSubmixReader(int32_t num_channels, int32_t sample_rate)
    : AudioReader(num_channels, sample_rate) {
}

RemoteSubmixReader::~RemoteSubmixReader() {
  Stop();
}

void RemoteSubmixReader::Start(CodecHandle* codec_handle) {
  if (reader_stopped_.exchange(false)) {
    codec_handle_ = codec_handle;
    if (!StartAudioStream()) {
      codec_handle_->Stop();
      fprintf(stderr, "NOTIFICATION Unable start the audio stream\n");
    }
  }
}

bool RemoteSubmixReader::StartAudioStream() {
  Log::D("Starting audio stream");
  aaudio_result_t result = AAudio_createStreamBuilder(&stream_builder_);
  if (result != AAUDIO_OK) {
    Log::E("Unable to create an audio stream builder: %d", result);
    return false;
  }
  Jni jni = Jvm::GetJni();
  AAudioStreamBuilder_setDeviceId(stream_builder_, GetRemoteSubmixDeviceId(jni));
  AAudioStreamBuilder_setDirection(stream_builder_, AAUDIO_DIRECTION_INPUT);
  AAudioStreamBuilder_setSampleRate(stream_builder_, sample_rate_);
  AAudioStreamBuilder_setChannelCount(stream_builder_, num_channels_);
  AAudioStreamBuilder_setFormat(stream_builder_, AUDIO_FORMAT);
  AAudioStreamBuilder_setPerformanceMode(stream_builder_, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
  AAudioStreamBuilder_setDataCallback(stream_builder_, AudioDataCallback, this);

  result = AAudioStreamBuilder_openStream(stream_builder_, &stream_);
  if (result != AAUDIO_OK) {
    Log::E("Unable to open the audio stream: %d", result);
    DeleteAudioStreamAndBuilder();
    return false;
  }

  int32_t buffer_capacity = AAudioStream_getBufferCapacityInFrames(stream_);
  Log::D("Audio buffer capacity: %d", buffer_capacity);

  consequent_queue_error_count_ = 0;

  result = AAudioStream_requestStart(stream_);
  if (result != AAUDIO_OK) {
    Log::E("Unable to start the audio stream: %d", result);
    return false;
  }

  return true;
}

void RemoteSubmixReader::Stop() {
  if (!reader_stopped_.exchange(true)) {
    Log::D("Stopping audio stream");
    codec_handle_->Stop();
    DeleteAudioStreamAndBuilder();
  }
}

void RemoteSubmixReader::DeleteAudioStreamAndBuilder() {
  if (stream_ != nullptr) {
    AAudioStream_close(stream_);
    stream_ = nullptr;
  }
  if (stream_builder_ != nullptr) {
    AAudioStreamBuilder_delete(stream_builder_);
    stream_builder_ = nullptr;
  }
}

aaudio_data_callback_result_t RemoteSubmixReader::ConsumeAudioData(AAudioStream* stream, uint8_t* audio_data, int32_t num_frames) {
  if (reader_stopped_) {
    return AAUDIO_CALLBACK_RESULT_STOP;
  }
  Log::V("RemoteSubmixReader::ConsumeAudioData(stream, audio_data, %d)", num_frames);

  int64_t frame_position;
  int64_t timestamp_ns = 0;
  if (AAudioStream_getTimestamp(stream, CLOCK_MONOTONIC, &frame_position, &timestamp_ns) != AAUDIO_OK &&
      last_presentation_timestamp_us_ == 0) {
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
  }

  int64_t presentation_time_us = timestamp_ns / 1000;
  // Make sure that presentation time is monotonically increasing.
  if (presentation_time_us <= last_presentation_timestamp_us_) {
    // Estimate presentation time based of the previous one and the sample rate.
    presentation_time_us = last_presentation_timestamp_us_ + num_frames_in_last_sample_ * 1000000L / sample_rate_;
  }
  last_presentation_timestamp_us_ = presentation_time_us;
  num_frames_in_last_sample_ = num_frames;

  size_t data_size = num_frames * num_channels_ * sizeof(int16_t);
  while (data_size > 0) {
    if (reader_stopped_) {
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    CodecInputBuffer codec_input(codec_handle_->codec(), "Audio: ");
    if (!codec_input.Deque(-1)) {
      return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    if (reader_stopped_) {
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    auto size = min(data_size, codec_input.size);
    memcpy(codec_input.buffer, audio_data, size);
    audio_data += size;
    bool queued_ok = codec_input.Queue(size, presentation_time_us, 0);
    if (reader_stopped_) {
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    if (!queued_ok && ++consequent_queue_error_count_ >= MAX_SUBSEQUENT_ERRORS) {
      Log::E("Audio streaming stopped due to repeated errors while queuing data");
      fprintf(stderr, "NOTIFICATION Audio streaming stopped due to repeated errors while queuing data\n");
      Stop();
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    consequent_queue_error_count_ = 0;
    data_size -= size;
  }
  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

aaudio_data_callback_result_t RemoteSubmixReader::AudioDataCallback(
    AAudioStream* stream, void* user_data, void* audio_data, int32_t num_frames) {
  return static_cast<RemoteSubmixReader*>(user_data)->ConsumeAudioData(stream, static_cast<uint8_t*>(audio_data), num_frames);
}

}  // namespace screensharing
