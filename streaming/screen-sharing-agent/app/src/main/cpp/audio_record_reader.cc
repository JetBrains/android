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

#include "audio_record_reader.h"

#include "accessors/audio_record.h"
#include "agent.h"
#include "codec_input_buffer.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

namespace {

constexpr int MAX_SUBSEQUENT_ERRORS = 5;
constexpr int BUF_SIZE = 1024;

}  // namespace

AudioRecordReader::AudioRecordReader(int32_t num_channels, int32_t sample_rate)
    : AudioReader(num_channels, sample_rate) {
}

AudioRecordReader::~AudioRecordReader() {
  Stop();
}

void AudioRecordReader::Start(CodecHandle* codec_handle) {
  if (reader_stopped_.exchange(false)) {
    codec_handle_ = codec_handle;
    thread_ = thread([this]() {
      Jvm::AttachCurrentThread("AudioRecordReader");
      Run();
      Jvm::DetachCurrentThread();
      Log::D("Audio: reader terminated");
    });
  }
}

void AudioRecordReader::Stop() {
  if (!reader_stopped_.exchange(true)) {
    if (thread_.get_id() != this_thread::get_id() && thread_.joinable()) {
      thread_.join();
    }
  }
}

void AudioRecordReader::Run() {
  consequent_queue_error_count_ = 0;
  audio_record_ = AudioRecord(Jvm::GetJni(), sample_rate_);
  if (!audio_record_.IsValid()) {
    return;
  }
  audio_record_.Start();
  ReadUntilStopped();
  audio_record_.Stop();
  audio_record_.Release();
  codec_handle_->Stop();
}

void AudioRecordReader::ReadUntilStopped() {
  Jni jni = Jvm::GetJni();
  JShortArray audio_data(jni, BUF_SIZE);
  while (!reader_stopped_) {
    int32_t num_samples = audio_record_.Read(&audio_data, BUF_SIZE);
    if (num_samples <= 0) {
      Log::E("Audio: error reading audio mix: %d", num_samples);
      fprintf(stderr, "NOTIFICATION Audio streaming stopped due an error while capturing audio\n");
      break;
    }
    int32_t num_frames = num_samples / num_channels_;
    int64_t timestamp = audio_record_.GetTimestamp();
    if (timestamp < 0) {
      Log::W("Audio: error obtaining timestamp: %" PRId64, timestamp);
    }
    int64_t presentation_time_us = timestamp / 1000;
    // Make sure that presentation time is monotonically increasing.
    if (presentation_time_us <= last_presentation_timestamp_us_) {
      // Estimate presentation time based of the previous one and the sample rate.
      presentation_time_us = last_presentation_timestamp_us_ + num_frames_in_last_sample_ * 1000000L / sample_rate_;
    }
    last_presentation_timestamp_us_ = presentation_time_us;
    num_frames_in_last_sample_ = num_frames;

    int32_t offset = 0;
    while (offset < num_samples) {
      CodecInputBuffer codec_input(codec_handle_->codec(), "Audio: ");
      if (!codec_input.Deque(-1)) {
        break;
      }
      if (reader_stopped_) {
        return;
      }
      auto size = min(static_cast<size_t>(num_samples - offset), codec_input.size / sizeof(int16_t));
      audio_data.GetRegion(jni, offset, size, reinterpret_cast<int16_t*>(codec_input.buffer));
      bool queued_ok = codec_input.Queue(size * sizeof(int16_t), presentation_time_us, 0);
      if (!queued_ok && ++consequent_queue_error_count_ >= MAX_SUBSEQUENT_ERRORS) {
        if (!reader_stopped_) {
          Log::E("Audio: streaming stopped due to repeated errors while queuing data");
          fprintf(stderr, "NOTIFICATION Audio streaming stopped due to repeated errors while queuing data\n");
        }
        return;
      }
      consequent_queue_error_count_ = 0;
      offset += size;
    }
  }
}

}  // namespace screensharing
