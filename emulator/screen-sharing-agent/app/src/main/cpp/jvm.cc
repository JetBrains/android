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

#include "casts.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

JObject& JObject::operator=(JObject&& other) noexcept {
  DeleteRef();
  jni_env_ = other.jni_env_;
  ref_ = other.ref_;
  other.ref_ = nullptr;
  return *this;
}

JObject& JObject::MakeGlobal() {
  if (ref_ != nullptr && jni_env_ != nullptr) {
    auto ref = ref_;
    ref_ = jni_env_->NewGlobalRef(ref_);
    jni_env_->DeleteLocalRef(ref);
    jni_env_ = nullptr;
  }
  return *this;
}

JClass JObject::GetClass() const {
  return GetClass(GetJni());
}

JClass JObject::GetClass(JNIEnv* jni_env) const {
  return JClass(jni_env, jni_env->GetObjectClass(ref_));
}

JObject JObject::CallObjectMethod(jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  JNIEnv* jni_env = GetJni();
  JObject result(jni_env, jni_env->CallObjectMethodV(ref_, method, args));
  va_end(args);
  return result;
}

JObject JObject::CallObjectMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  JObject result(jni_env, jni_env->CallObjectMethodV(ref_, method, args));
  va_end(args);
  return result;
}

int32_t JObject::GetIntField(JNIEnv* jni_env, jfieldID field) const {
  return jni_env->GetIntField(ref_, field);
}

void JObject::SetIntField(JNIEnv* jni_env, jfieldID field, int32_t value) const {
  jni_env->SetIntField(ref_, field, value);
}

float JObject::GetFloatField(JNIEnv* jni_env, jfieldID field) const {
  return jni_env->GetFloatField(ref_, field);
}

void JObject::SetFloatField(JNIEnv* jni_env, jfieldID field, float value) const {
  jni_env->SetFloatField(ref_, field, value);
}

bool JObject::CallBooleanMethod(jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jboolean result = GetJni()->CallBooleanMethodV(ref_, method, args);
  va_end(args);
  return result != JNI_FALSE;
}

bool JObject::CallBooleanMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jboolean result = jni_env->CallBooleanMethodV(ref_, method, args);
  va_end(args);
  return result != JNI_FALSE;
}

int32_t JObject::CallIntMethod(jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jboolean result = GetJni()->CallIntMethodV(ref_, method, args);
  va_end(args);
  return result;
}

int32_t JObject::CallIntMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jboolean result = jni_env->CallIntMethodV(ref_, method, args);
  va_end(args);
  return result;
}

void JObject::CallVoidMethod(jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  GetJni()->CallVoidMethodV(ref_, method, args);
  va_end(args);
}

void JObject::CallVoidMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jni_env->CallVoidMethodV(ref_, method, args);
  va_end(args);
}

string JObject::ToString() const {
  jmethodID method = GetClass().GetDeclaredOrInheritedMethodId("toString", "()Ljava/lang/String;");
  return JString(jni_env_, jni_env_->CallObjectMethod(ref_, method)).GetValue();
}

void JObject::DeleteRef() noexcept {
  if (ref_ != nullptr) {
    if (jni_env_ == nullptr) {
      Jvm::GetJni()->DeleteGlobalRef(ref_);
    } else {
      jni_env_->DeleteLocalRef(ref_);
    }
  }
}

void JObject::IllegalGlobalReferenceUse() {
  Log::Fatal("JNIEnv pointer has to be provided when using a global reference");
}

jfieldID JClass::GetFieldId(const char* name, const char* signature) const {
  auto field = GetJni()->GetFieldID(ref(), name, signature);
  if (field == nullptr) {
    Log::Fatal("Unable to find the %s.%s field with signature %s", GetName().c_str(), name, signature);
  }
  return field;
}

jmethodID JClass::GetStaticMethodId(const char* name, const char* signature) const {
  auto method = GetJni()->GetStaticMethodID(ref(), name, signature);
  if (method == nullptr) {
    Log::Fatal("Unable to find the %s.%s method with signature %s", GetName().c_str(), name, signature);
  }
  return method;
}

jmethodID JClass::GetMethodId(const char* name, const char* signature) const {
  auto method = GetJni()->GetMethodID(ref(), name, signature);
  if (method == nullptr) {
    Log::Fatal("Unable to find the %s.%s method with signature %s", GetName().c_str(), name, signature);
  }
  return method;
}

jmethodID JClass::GetConstructorId(const char* signature) const {
  auto constructor = GetJni()->GetMethodID(ref(), "<init>", signature);
  if (constructor == nullptr) {
    Log::Fatal("Unable to find the %s constructor with signature %s", GetName().c_str(), signature);
  }
  return constructor;
}

jmethodID JClass::GetDeclaredOrInheritedMethodId(const char* name, const char* signature) const {
  jmethodID method = GetJni()->GetMethodID(ref(), name, signature);
  if (method != nullptr) {
    return method;
  }
  GetJni()->ExceptionClear();
  for (JClass clazz = GetSuperclass(); !clazz.IsNull(); clazz = clazz.GetSuperclass()) {
    method = GetJni()->GetMethodID(ref(), name, signature);
    if (method != nullptr) {
      return method;
    }
    GetJni()->ExceptionClear();
  }
  Log::Fatal("Unable to find the declared or inherited %s.%s method with signature %s", GetName().c_str(), name, signature);
}

JClass JClass::GetSuperclass() const {
  JNIEnv* jni_env = GetJni();
  return JClass(jni_env, jni_env->GetSuperclass(ref()));
}

string JClass::GetName() const {
  JNIEnv* jni_env = GetJni();
  jclass clazz = ref();
  jmethodID get_name_method = jni_env->GetMethodID(clazz, "getName", "()Ljava/lang/String;");
  if (get_name_method == nullptr) {
    return "<unknown>";
  }
  auto name = down_cast<jstring>(jni_env->CallObjectMethod(clazz, get_name_method));
  return JString(jni_env, name).GetValue();
}

JObject JClass::NewObject(jmethodID constructor, ...) const {
  JNIEnv* jni_env = GetJni();
  va_list args;
  va_start(args, constructor);
  JObject result(jni_env, jni_env->NewObjectV(ref(), constructor, args));
  va_end(args);
  return result;
}

JObject JClass::NewObject(JNIEnv* jni_env, jmethodID constructor, ...) const {
  va_list args;
  va_start(args, constructor);
  JObject result(jni_env, jni_env->NewObjectV(ref(), constructor, args));
  va_end(args);
  return result;
}

JObject JClass::CallStaticObjectMethod(jmethodID method, ...) const {
  JNIEnv* jni_env = GetJni();
  va_list args;
  va_start(args, method);
  JObject result(jni_env, jni_env->CallStaticObjectMethodV(ref(), method, args));
  va_end(args);
  return result;
}

JObject JClass::CallStaticObjectMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  JObject result(jni_env, jni_env->CallStaticObjectMethodV(ref(), method, args));
  va_end(args);
  return result;
}

void JClass::CallStaticVoidMethod(jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  GetJni()->CallStaticObjectMethodV(ref(), method, args);
  va_end(args);
}

void JClass::CallStaticVoidMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jni_env->CallStaticObjectMethodV(ref(), method, args);
  va_end(args);
}

JObjectArray JClass::NewObjectArray(JNIEnv* jni_env, int32_t length, jobject initialElement) const {
  return JObjectArray(jni_env, jni_env->NewObjectArray(length, ref(), initialElement));
}

string JString::GetValue() const {
  const char* localName = GetJni()->GetStringUTFChars(ref(), nullptr);
  std::string res(localName);
  GetJni()->ReleaseStringUTFChars(ref(), localName);
  return res;
}

JObject JObjectArray::GetElement(JNIEnv* jni_env, int32_t index) const {
  return JObject(jni_env, jni_env->GetObjectArrayElement(ref(), index));
}

void JObjectArray::SetElement(JNIEnv* jni_env, int32_t index, const JObject& element) const {
  jni_env->SetObjectArrayElement(ref(), index, element);
}

JClass Jni::GetClass(const char* name) const {
  jclass clazz = jni_env_->FindClass(name);
  if (clazz == nullptr) {
    Log::Fatal("Unable to find the %s class", name);
  }
  return JClass(jni_env_, clazz);
}

JString Jni::NewStringUtf(const char* string) const {
  return JString(jni_env_, jni_env_->NewStringUTF(string));
}

JCharArray Jni::NewCharArray(int32_t length) const {
  return JCharArray(jni_env_, jni_env_->NewCharArray(length));
}

bool Jni::ExceptionCheckAndClear() const {
  jboolean exception_thrown = jni_env_->ExceptionCheck();
  if (exception_thrown) {
    jni_env_->ExceptionClear();
  }
  return exception_thrown != JNI_FALSE;
}

JavaVM* Jvm::jvm_ = nullptr;
jint Jvm::jni_version_ = 0;

void Jvm::Initialize(JNIEnv* jni_env) {
  jni_env->GetJavaVM(&jvm_);
  jni_version_ = jni_env->GetVersion();
}

Jni Jvm::AttachCurrentThread(const char* thread_name) {
  JavaVMAttachArgs args;
  args.version = jni_version_;
  args.name = thread_name;
  args.group = nullptr;
  JNIEnv* env;
  jvm_->AttachCurrentThread(&env, &args);
  return env;
}

void Jvm::DetachCurrentThread() {
  jvm_->DetachCurrentThread();
}

}  // namespace screensharing
