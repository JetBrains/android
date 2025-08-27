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

#include "agent.h"
#include "audio_record_reader.h"
#include "codec_output_buffer.h"
#include "jvm.h"
#include "log.h"
#include "remote_submix_reader.h"
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

constexpr int AUDIO_SAMPLE_RATE = 48000; // TODO: Consider changing to 44100.
constexpr int MAX_SUBSEQUENT_ERRORS = 5;
constexpr int CHANNEL_COUNT = 2;
constexpr int CHANNEL_MASK = AAUDIO_CHANNEL_STEREO;
constexpr int BIT_RATE = 128000;
constexpr char MIME_TYPE[] = "audio/opus";
const char* CODEC_NAME = MIME_TYPE + sizeof("audio/") - 1;

AMediaFormat* CreateMediaFormat() {
  AMediaFormat* media_format = AMediaFormat_new();
  AMediaFormat_setString(media_format, AMEDIAFORMAT_KEY_MIME, MIME_TYPE);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, CHANNEL_COUNT);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_CHANNEL_MASK, CHANNEL_MASK);
  AMediaFormat_setInt32(media_format, AMEDIAFORMAT_KEY_SAMPLE_RATE, AUDIO_SAMPLE_RATE);
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

}  // namespace

AudioStreamer::AudioStreamer(SocketWriter* writer)
    : writer_(writer) {
}

AudioStreamer::~AudioStreamer() {
  Stop();
}

void AudioStreamer::Start() {
  if (streamer_stopped_.exchange(false)) {
    Log::D("Audio: starting streaming");
    thread_ = thread([this]() {
      Jvm::AttachCurrentThread("AudioStreamer");
      Run();
      Jvm::DetachCurrentThread();
      Log::D("Audio: streaming terminated");
    });
  }
}

void AudioStreamer::Stop() {
  if (!streamer_stopped_.exchange(true)) {
    Log::D("Audio: stopping streaming");
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
    CodecOutputBuffer codec_buffer(codec_handle_->codec(), "Audio: ");
    if (!codec_buffer.Deque(-1)) {
      if (++consequent_deque_error_count_ >= MAX_SUBSEQUENT_ERRORS) {
        Log::E("Audio: streaming stopped due to repeated encoder errors");
        fprintf(stderr, "NOTIFICATION Audio streaming stopped due to repeated encoder errors\n");
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
    auto res = writer_->Write(&packet_header, AudioPacketHeader::SIZE, codec_buffer.buffer(), codec_buffer.size());
    if (res != SocketWriter::Result::SUCCESS && res != SocketWriter::Result::SUCCESS_AFTER_BLOCKING) {
      continue_streaming = false;
    }
  }

  StopAudioCapture();
}

void AudioStreamer::StopCodec() {
  if (codec_handle_ != nullptr) {
    codec_handle_->Stop();
  }
}

bool AudioStreamer::StartAudioCapture() {
  if (Agent::feature_level() >= 34 || (Agent::feature_level() == 33 && Agent::device_manufacturer() == "Google")) {
    audio_reader_ = new AudioRecordReader(CHANNEL_COUNT, AUDIO_SAMPLE_RATE);
  } else {
    audio_reader_ = new RemoteSubmixReader(CHANNEL_COUNT, AUDIO_SAMPLE_RATE);
  }

  AMediaCodec* codec = AMediaCodec_createEncoderByType(MIME_TYPE);
  if (codec == nullptr) {
    Log::W("Audio: unable to create %s encoder", CODEC_NAME);
    return false;
  }
  codec_handle_ = new CodecHandle(codec, "Audio: ");
  media_format_ = CreateMediaFormat();
  media_status_t status = AMediaCodec_configure(codec, media_format_, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
  if (status != AMEDIA_OK) {
    Log::W("Audio: error configuring encoder: %d", status);
    return false;
  }

  if (!codec_handle_->Start()) {
    return false;
  }
  audio_reader_->Start(codec_handle_);
  return true;
}

void AudioStreamer::StopAudioCapture() {
  delete audio_reader_;
  audio_reader_ = nullptr;
  delete codec_handle_;
  codec_handle_ = nullptr;
  AMediaFormat_delete(media_format_);
  media_format_ = nullptr;
  consequent_deque_error_count_ = 0;
}

}  // namespace screensharing
