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

#include "audio_record.h"

#include "agent.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

namespace {

// From https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/AudioAttributes.java.
constexpr int AudioAttributes_USAGE_VEHICLE_STATUS = 1000 + 2;
constexpr int AudioAttributes_USAGE_SPEAKER_CLEANUP = 1000 + 4;
// From https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/AudioFormat.java.
constexpr int AudioFormat_ENCODING_PCM_16BIT = 2;
constexpr int AudioFormat_CHANNEL_OUT_STEREO = 0x4 | 0x8;
// From https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiopolicy/AudioMix.java.
constexpr int AudioMix_ROUTE_FLAG_LOOP_BACK = 0x1 << 1;
// From https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiopolicy/AudioMixingRule.java.
constexpr int AudioMixingRule_RULE_MATCH_ATTRIBUTE_USAGE = 0x1;
// From https://android.googlesource.com/platform/frameworks/base/+/18c0cfb/media/java/android/media/AudioTimestamp.java.
constexpr int AudioTimestamp_TIMEBASE_MONOTONIC = 0;

JObject CreateAudioRecord(Jni jni, int32_t audio_sample_rate) {
  // Create an AudioAttributes object representing an unused usage type.
  JClass audio_attributes_builder_class = jni.GetClass("android/media/AudioAttributes$Builder");
  JObject audio_attributes_builder = audio_attributes_builder_class.NewObject(jni, audio_attributes_builder_class.GetConstructor());
  jmethodID set_system_usage_method =
      audio_attributes_builder_class.GetMethod(jni, "setSystemUsage", "(I)Landroid/media/AudioAttributes$Builder;");
  int unused_usage = Agent::feature_level() >= 36 ? AudioAttributes_USAGE_SPEAKER_CLEANUP : AudioAttributes_USAGE_VEHICLE_STATUS;
  audio_attributes_builder.CallObjectMethod(jni, set_system_usage_method, unused_usage);
  if (jni.GetAndClearException().IsNotNull() && unused_usage == AudioAttributes_USAGE_SPEAKER_CLEANUP) {
    // Fall back to AudioAttributes_USAGE_VEHICLE_STATUS if AudioAttributes_USAGE_SPEAKER_CLEANUP is not accepted.
    audio_attributes_builder.CallObjectMethod(jni, set_system_usage_method, AudioAttributes_USAGE_VEHICLE_STATUS);
  }
  jmethodID build_method = audio_attributes_builder_class.GetMethod(jni, "build", "()Landroid/media/AudioAttributes;");
  JObject attributes = audio_attributes_builder.CallObjectMethod(jni, build_method);

  // Create an AudioMixingRule that includes all audio types except the unused one.
  JClass mixing_rule_builder_class = jni.GetClass("android/media/audiopolicy/AudioMixingRule$Builder");
  JObject mixing_rule_builder = mixing_rule_builder_class.NewObject(jni, mixing_rule_builder_class.GetConstructor());
  jmethodID exclude_rule_method = mixing_rule_builder_class.GetMethod(
      jni, "excludeRule", "(Landroid/media/AudioAttributes;I)Landroid/media/audiopolicy/AudioMixingRule$Builder;");
  mixing_rule_builder.CallObjectMethod(jni, exclude_rule_method, attributes.ref(), AudioMixingRule_RULE_MATCH_ATTRIBUTE_USAGE);
  build_method = mixing_rule_builder_class.GetMethod(jni, "build", "()Landroid/media/audiopolicy/AudioMixingRule;");
  JObject mixing_rule = mixing_rule_builder.CallObjectMethod(jni, build_method);

  // Create an AudioFormat.
  JClass format_builder_class = jni.GetClass("android/media/AudioFormat$Builder");
  JObject format_builder = format_builder_class.NewObject(jni, format_builder_class.GetConstructor());
  jmethodID set_sample_rate_method = format_builder_class.GetMethod(jni, "setSampleRate", "(I)Landroid/media/AudioFormat$Builder;");
  format_builder.CallObjectMethod(jni, set_sample_rate_method, audio_sample_rate);
  jmethodID set_encoding_method = format_builder_class.GetMethod(jni, "setEncoding", "(I)Landroid/media/AudioFormat$Builder;");
  format_builder.CallObjectMethod(jni, set_encoding_method, AudioFormat_ENCODING_PCM_16BIT);
  jmethodID set_channel_mask_method = format_builder_class.GetMethod(jni, "setChannelMask", "(I)Landroid/media/AudioFormat$Builder;");
  format_builder.CallObjectMethod(jni, set_channel_mask_method, AudioFormat_CHANNEL_OUT_STEREO);
  build_method = format_builder_class.GetMethod(jni, "build", "()Landroid/media/AudioFormat;");
  JObject format = format_builder.CallObjectMethod(jni, build_method);

  // Create an AudioMix.
  JClass mix_builder_class = jni.GetClass("android/media/audiopolicy/AudioMix$Builder");
  JObject mix_builder = mix_builder_class.NewObject(
      jni, mix_builder_class.GetConstructor(jni, "(Landroid/media/audiopolicy/AudioMixingRule;)V"), mixing_rule.ref());
  jmethodID set_format_method = mix_builder_class.GetMethod(
      jni, "setFormat", "(Landroid/media/AudioFormat;)Landroid/media/audiopolicy/AudioMix$Builder;");
  mix_builder.CallObjectMethod(jni, set_format_method, format.ref());
  jmethodID set_route_flags_method = mix_builder_class.GetMethod(jni, "setRouteFlags", "(I)Landroid/media/audiopolicy/AudioMix$Builder;");
  mix_builder.CallObjectMethod(jni, set_route_flags_method, AudioMix_ROUTE_FLAG_LOOP_BACK);
  build_method = mix_builder_class.GetMethod(jni, "build", "()Landroid/media/audiopolicy/AudioMix;");
  JObject mix = mix_builder.CallObjectMethod(jni, build_method);

  // Create an AudioPolicy.
  JClass policy_builder_class = jni.GetClass("android/media/audiopolicy/AudioPolicy$Builder");
  JObject policy_builder = policy_builder_class.NewObject(
      jni, policy_builder_class.GetConstructor("(Landroid/content/Context;)V"), nullptr);
  jmethodID add_mix_method = policy_builder_class.GetMethod(
      jni, "addMix", "(Landroid/media/audiopolicy/AudioMix;)Landroid/media/audiopolicy/AudioPolicy$Builder;");
  policy_builder.CallObjectMethod(jni, add_mix_method, mix.ref());
  build_method = policy_builder_class.GetMethod(jni, "build", "()Landroid/media/audiopolicy/AudioPolicy;");
  JObject policy = policy_builder.CallObjectMethod(jni, build_method);
  if (policy.IsNull()) {
    JThrowable exception = jni.GetAndClearException();
    if (exception.IsNotNull()) {
      Log::Fatal(GENERIC_FAILURE, std::move(exception), "AudioPolicy$Builder.build threw an exception");
    }
  }

  // Register the AudioPolicy.
  JClass audio_manager_class = jni.GetClass("android/media/AudioManager");
  jmethodID register_audio_policy_method =
      audio_manager_class.GetStaticMethod("registerAudioPolicyStatic", "(Landroid/media/audiopolicy/AudioPolicy;)I");
  int32_t res = audio_manager_class.CallStaticIntMethod(jni, register_audio_policy_method, policy.ref());
  if (res != 0) {
    Log::W("Unable to register audio policy: %d", res);
    return JObject();
  }

  jmethodID create_audio_record_sink_method =
      policy.GetClass().GetMethod("createAudioRecordSink", "(Landroid/media/audiopolicy/AudioMix;)Landroid/media/AudioRecord;");
  JObject audio_record = policy.CallObjectMethod(create_audio_record_sink_method, mix.ref());
  if (audio_record.IsNull()) {
    jni.CheckAndClearException();
    Log::W("Unable to create AudioRecord");
  }
  return audio_record;
}

}  // namespace

AudioRecord::AudioRecord(Jni jni, int32_t audio_sample_rate)
    : audio_record_(CreateAudioRecord(jni, audio_sample_rate)) {
  if (audio_record_.IsNull()) {
    return;
  }
  JClass clazz = audio_record_.GetClass();
  release_method_ = clazz.GetMethod("release", "()V");
  start_recording_method_ = clazz.GetMethod("startRecording", "()V");
  stop_method_ = clazz.GetMethod("stop", "()V");
  read_method_ = clazz.GetMethod("read", "([SII)I");
  get_timestamp_method_ = clazz.GetMethod("getTimestamp", "(Landroid/media/AudioTimestamp;I)I");
  JClass audio_timestamp_class = jni.GetClass("android/media/AudioTimestamp");
  audio_timestamp_ = audio_timestamp_class.NewObject(jni, audio_timestamp_class.GetConstructor());
  audio_timestamp_nano_time_field_ = audio_timestamp_class.GetFieldId(jni, "nanoTime", "J");
}

AudioRecord::~AudioRecord() {
  Release();
}

AudioRecord& AudioRecord::operator=(AudioRecord&& other) noexcept {
  Release();
  audio_record_ = std::move(other.audio_record_);
  release_method_ = other.release_method_;
  start_recording_method_ = other.start_recording_method_;
  stop_method_ = other.stop_method_;
  read_method_ = other.read_method_;
  get_timestamp_method_ = other.get_timestamp_method_;
  audio_timestamp_ = std::move(other.audio_timestamp_);
  audio_timestamp_nano_time_field_ = other.audio_timestamp_nano_time_field_;
  return *this;
}

void AudioRecord::Release() {
  if (audio_record_.IsNotNull()) {
    Jvm::GetJni()->CallVoidMethod(audio_record_.Release(), release_method_);
  }
}

void AudioRecord::Start() {
  audio_record_.CallVoidMethod(start_recording_method_);
}

void AudioRecord::Stop() {
  audio_record_.CallVoidMethod(stop_method_);
}

int32_t AudioRecord::Read(JShortArray* buf, int32_t num_samples) {
  return audio_record_.CallIntMethod(read_method_, buf->ref(), 0, num_samples);
}

int64_t AudioRecord::GetTimestamp() {
  int32_t res = audio_record_.CallIntMethod(get_timestamp_method_, audio_timestamp_.ref(), AudioTimestamp_TIMEBASE_MONOTONIC);
  if (res < 0) {
    return res;
  }
  return audio_timestamp_.GetLongField(audio_timestamp_nano_time_field_);
}

}  // namespace screensharing
