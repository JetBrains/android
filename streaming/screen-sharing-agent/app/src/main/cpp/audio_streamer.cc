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

#include "audio_streamer.h"

#include "accessors/audio_manager.h"
#include "agent.h"
#include "codec_output_buffer.h"
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
constexpr int SAMPLE_RATE = 48000;
constexpr int CHANNEL_COUNT = 2;
constexpr int CHANNEL_MASK = AAUDIO_CHANNEL_STEREO;
constexpr int AUDIO_FORMAT = AAUDIO_FORMAT_PCM_I16;
constexpr int BYTES_PER_SAMPLE = 2;
constexpr int BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNEL_COUNT;
constexpr int BIT_RATE = 128000;
constexpr char MIME_TYPE[] = "audio/opus";
const char* CODEC_NAME = MIME_TYPE + sizeof("audio/") - 1;
constexpr int SOCKET_TIMEOUT_MICROS = 10000000;

AMediaFormat* CreateMediaFormat() {
  AMediaFormat* media_format = AMediaFormat_new();
  AMediaFormat_setString(media_format, AMEDIAFORMAT_KEY_MIME, MIME_TYPE);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, CHANNEL_COUNT);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_CHANNEL_MASK, CHANNEL_MASK);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_SAMPLE_RATE, SAMPLE_RATE);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_BIT_RATE, BIT_RATE);
  return media_format;
}

// The header of a video packet.
class AudioPacketHeader {
public:
  AudioPacketHeader(bool config, int32_t packet_size)
      : packet_size_((packet_size & 0x7FFFFFFF) | (config ? 0x80000000 : 0)) {
  }

  [[nodiscard]] int32_t GetPacketSize() const {
    return packet_size_ & 0x7FFFFFFF;
  }

  [[nodiscard]] bool IsConfig() const {
    return (packet_size_ & 0x80000000) != 0;
  }

  [[nodiscard]] std::string ToDebugString() const {
    return StringPrintf("%s audio packet size=%d", IsConfig() ? "config" : "data", GetPacketSize());
  }

private:
  int32_t packet_size_ = 0;

public:
  static constexpr size_t SIZE = sizeof(packet_size_);  // Similar to sizeof(AudioPacketHeader) but without the trailing alignment.
};

struct CodecInputBuffer {
  explicit CodecInputBuffer(AMediaCodec* codec)
      : codec(codec) {
  }

  [[nodiscard]] bool Deque(int64_t timeout_us) {
    index = AMediaCodec_dequeueInputBuffer(codec, timeout_us);
    if (index < 0) {
      Log::W("Audio: MediaCodec_dequeueInputBuffer returned %ld", static_cast<long>(index));
      return false;
    }
    buffer = AMediaCodec_getInputBuffer(codec, static_cast<size_t>(index), &size);
    if (buffer == nullptr) {
      Log::W("Audio: AMediaCodec_getInputBuffer(codec, %ld, &size_) returned null", static_cast<long>(index));
      return false;
    }
    return true;
  }

  [[nodiscard]] bool Queue(size_t data_size, uint64_t presentation_time_us, uint32_t flags) {
    auto res = AMediaCodec_queueInputBuffer(codec, index, 0, data_size, presentation_time_us, flags);
    if (res == AMEDIA_OK) {
      return true;
    }
    Log::W("Audio: AMediaCodec_queueInputBuffer returned %d", res);
    return false;
  }

  AMediaCodec* codec;
  uint8_t* buffer;
  ssize_t index;
  size_t size;
};

}  // namespace

AudioStreamer::AudioStreamer(int socket_fd)
    : writer_(socket_fd, "audio") {
}

AudioStreamer::~AudioStreamer() {
  Stop();
}

void AudioStreamer::Start() {
  if (streamer_stopped_.exchange(false)) {
    Log::D("Starting audio stream");
    thread_ = thread([this]() {
      Jvm::AttachCurrentThread("AudioStreamer");
      Run();
      Jvm::DetachCurrentThread();
      Log::D("Audio streaming terminated");
    });
  }
}

void AudioStreamer::Stop() {
  if (!streamer_stopped_.exchange(true)) {
    Log::D("Stopping audio stream");
    StopCodec();
    if (thread_.get_id() != this_thread::get_id() && thread_.joinable()) {
      thread_.join();
    }
  }
}

void AudioStreamer::Run() {
  if (!StartAudioCapture()) {
    return;
  }

  bool continue_streaming = true;
  consequent_deque_error_count_ = 0;
  while (continue_streaming && !streamer_stopped_) {
    CodecOutputBuffer codec_buffer(codec_, "Audio: ");
    if (!codec_buffer.Deque(-1)) {
      if (++consequent_deque_error_count_ >= MAX_SUBSEQUENT_ERRORS) {
        Log::E("Audio streaming stopped due to repeated encoder errors");
        break;
      }
      continue;
    }
    consequent_deque_error_count_ = 0;
    continue_streaming = !codec_buffer.IsEndOfStream();

    AudioPacketHeader packet_header(codec_buffer.IsConfig(), codec_buffer.size());
    if (Log::IsEnabled(Log::Level::VERBOSE)) {
      Log::V("Audio: writing %s", packet_header.ToDebugString().c_str());
    }
    auto res = writer_.Write(&packet_header, AudioPacketHeader::SIZE, codec_buffer.buffer(), codec_buffer.size(), SOCKET_TIMEOUT_MICROS);
    if (res != SocketWriter::Result::SUCCESS && res != SocketWriter::Result::SUCCESS_AFTER_BLOCKING) {
      continue_streaming = false;
    }
  }

  StopAudioCapture();
}

void AudioStreamer::StopCodec() {
  unique_lock lock(mutex_);
  if (running_codec_ == nullptr) {
    codec_stop_pending_ = true;
  } else {
    Log::D("Stopping audio codec");
    AMediaCodec_stop(running_codec_);
    running_codec_ = nullptr;
  }
}

aaudio_data_callback_result_t AudioStreamer::ConsumeAudioData(AAudioStream* stream, uint8_t* audio_data, int32_t num_frames) {
  if (streamer_stopped_) {
    return AAUDIO_CALLBACK_RESULT_STOP;
  }
  Log::V("AudioStreamer::ConsumeAudioData(stream, audio_data, %d)", num_frames);

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
    presentation_time_us = last_presentation_timestamp_us_ + num_frames_in_last_sample_ * 1000000L / SAMPLE_RATE;
  }
  last_presentation_timestamp_us_ = presentation_time_us;
  num_frames_in_last_sample_ = num_frames;

  size_t data_size = num_frames * BYTES_PER_FRAME;
  while (data_size > 0) {
    unique_lock lock(mutex_);
    if (running_codec_ == nullptr) {
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    CodecInputBuffer codec_input(running_codec_);
    if (!codec_input.Deque(-1)) {
      return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    if (streamer_stopped_) {
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    auto size = min(data_size, codec_input.size);
    memcpy(codec_input.buffer, audio_data, size);
    audio_data += size;
    bool queued_ok = codec_input.Queue(size, presentation_time_us, 0);
    if (streamer_stopped_) {
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    if (!queued_ok && ++consequent_queue_error_count_ >= MAX_SUBSEQUENT_ERRORS) {
      Log::E("Audio streaming stopped due to repeated errors while queuing data");
      Stop();
      return AAUDIO_CALLBACK_RESULT_STOP;
    }
    consequent_queue_error_count_ = 0;
    data_size -= size;
  }
  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

bool AudioStreamer::StartAudioCapture() {
  aaudio_result_t result = AAudio_createStreamBuilder(&stream_builder_);
  if (result != AAUDIO_OK) {
    Log::W("Unable to create an audio stream builder: %d", result);
    return false;
  }
  Jni jni = Jvm::GetJni();
  AAudioStreamBuilder_setDeviceId(stream_builder_, AudioManager::GetInputAudioDeviceId(jni, TYPE_REMOTE_SUBMIX));
  AAudioStreamBuilder_setDirection(stream_builder_, AAUDIO_DIRECTION_INPUT);
  AAudioStreamBuilder_setSampleRate(stream_builder_, SAMPLE_RATE);
  AAudioStreamBuilder_setChannelCount(stream_builder_, CHANNEL_COUNT);
  AAudioStreamBuilder_setFormat(stream_builder_, AUDIO_FORMAT);
  AAudioStreamBuilder_setPerformanceMode(stream_builder_, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
  AAudioStreamBuilder_setDataCallback(stream_builder_, AudioDataCallback, this);

  result = AAudioStreamBuilder_openStream(stream_builder_, &stream_);
  if (result != AAUDIO_OK) {
    Log::W("Unable to open the audio stream: %d", result);
    DeleteAudioStreamAndBuilder();
    return false;
  }

  int32_t buffer_capacity = AAudioStream_getBufferCapacityInFrames(stream_);
  Log::D("Audio buffer capacity: %d", buffer_capacity);

  codec_ = AMediaCodec_createEncoderByType(MIME_TYPE);
  if (codec_ == nullptr) {
    Log::W("Unable to create %s audio encoder", CODEC_NAME);
    return false;
  }
  media_format_ = CreateMediaFormat();
  media_status_t status = AMediaCodec_configure(codec_, media_format_, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
  if (status != AMEDIA_OK) {
    Log::W("Error configuring audio encoder: %d", status);
    return false;
  }

  unique_lock lock(mutex_);
  if (codec_stop_pending_) {
    return false;
  }
  consequent_queue_error_count_ = 0;
  running_codec_ = codec_;
  AMediaCodec_start(codec_);

  result = AAudioStream_requestStart(stream_);
  if (result != AAUDIO_OK) {
    Log::W("Unable to start the audio stream: %d", result);
    running_codec_ = nullptr;
    return false;
  }

  return true;
}

void AudioStreamer::StopAudioCapture() {
  AMediaFormat_delete(media_format_);
  media_format_ = nullptr;
  DeleteAudioStreamAndBuilder();
  consequent_deque_error_count_ = 0;
}

void AudioStreamer::DeleteAudioStreamAndBuilder() {
  if (stream_builder_ != nullptr) {
    AAudioStreamBuilder_delete(stream_builder_);
    stream_builder_ = nullptr;
  }
  if (stream_ != nullptr) {
    AAudioStream_close(stream_);
    stream_ = nullptr;
  }
}

aaudio_data_callback_result_t AudioStreamer::AudioDataCallback(
    AAudioStream* stream, void* user_data, void* audio_data, int32_t num_frames) {
  return static_cast<AudioStreamer*>(user_data)->ConsumeAudioData(stream, static_cast<uint8_t*>(audio_data), num_frames);
}

}  // namespace screensharing
