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

#include "device_state.h"

#include <mutex>

#include "log.h"

namespace screensharing {

using namespace std;

namespace {

uint32_t ExtractProperties(JObject&& int_set) {
  uint32_t result = 0;
  for (JIterator it = JIterable(std::move(int_set)).Iterator(); it.HasNext();) {
    result |= 1 << (JNumber(it.Next()).IntValue() - 1);
  }
  return result;
}

}  // namespace

DeviceState::DeviceState(const JObject& device_state) {
  InitializeStatics(device_state);

  identifier_ = device_state.CallIntMethod(get_identifier_method_);
  name_ = device_state.CallObjectMethod(get_name_method_).ToString();
  Log::D("DeviceState: identifier=%d, name=%s", identifier_, name_.c_str());
  JObject configuration = device_state.CallObjectMethod(get_configuration_method_);

  {
    unique_lock lock(static_initialization_mutex_);
    if (!statics_initialized_) {
      JClass clazz = configuration.GetClass();
      system_properties_field_ = clazz.GetFieldId("mSystemProperties", "Landroid/util/ArraySet;");
      physical_properties_field_ = clazz.GetFieldId("mPhysicalProperties", "Landroid/util/ArraySet;");
      statics_initialized_ = true;
    }
  }

  system_properties_ = ExtractProperties(configuration.GetObjectField(system_properties_field_));
  physical_properties_ = ExtractProperties(configuration.GetObjectField(physical_properties_field_));
}

void DeviceState::InitializeStatics(const JObject& device_state) {
  unique_lock lock(static_initialization_mutex_);
  if (!statics_initialized_) {
    JClass clazz = device_state.GetClass();
    get_identifier_method_ = clazz.GetMethod("getIdentifier", "()I");
    get_name_method_ = clazz.GetMethod("getName", "()Ljava/lang/String;");
    get_configuration_method_ = clazz.GetMethod("getConfiguration", "()Landroid/hardware/devicestate/DeviceState$Configuration;");
  }
}

DeviceState::DeviceState(int32_t identifier, string name, uint32_t system_properties, uint32_t physical_properties)
    : identifier_(identifier),
      name_(std::move(name)),
      system_properties_(system_properties),
      physical_properties_(physical_properties) {
}

DeviceState::DeviceState(DeviceState&& other) noexcept
    : identifier_(other.identifier_),
      name_(std::move(other.name_)),
      system_properties_(other.system_properties_),
      physical_properties_(other.physical_properties_) {
}

DeviceState& DeviceState::operator=(DeviceState&& other) noexcept {
  identifier_ = other.identifier_;
  name_ = std::move(other.name_);
  system_properties_ = other.system_properties_;
  physical_properties_ = other.physical_properties_;
  return *this;
}

void DeviceState::Serialize(Base128OutputStream& stream) const {
  stream.WriteInt32(identifier_);
  stream.WriteBytes(name_);
  stream.WriteUInt32(system_properties_);
  stream.WriteUInt32(physical_properties_);
}

int32_t DeviceState::GetIdentifier(const JObject& device_state) {
  InitializeStatics(device_state);
  return device_state.CallIntMethod(get_identifier_method_);
}

mutex DeviceState::static_initialization_mutex_;
bool DeviceState::statics_initialized_ = false;

jmethodID DeviceState::get_identifier_method_;
jmethodID DeviceState::get_name_method_;
jmethodID DeviceState::get_configuration_method_;
jfieldID DeviceState::system_properties_field_;
jfieldID DeviceState::physical_properties_field_;

}  // namespace screensharing
