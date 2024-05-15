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

#include <cassert>
#include <mutex>

#include "casts.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

namespace {

mutex static_initialization_mutex_;

}

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

JObject JObject::GetObjectField(JNIEnv* jni_env, jfieldID field) const {
  return JObject(jni_env, jni_env->GetObjectField(ref_, field));
}

void JObject::SetObjectField(JNIEnv* jni_env, jfieldID field, jobject value) const {
  jni_env->SetObjectField(ref_, field, value);
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

string JObject::ToString() const {
  if (ref_ == nullptr) {
    Log::Fatal(NULL_POINTER, "ToString is called on a null object");
  }
  Jni jni = GetJni();
  JClass clazz = GetClass(jni);
  jmethodID method = clazz.GetDeclaredOrInheritedMethod("toString", "()Ljava/lang/String;");
  jobject jstr = jni->CallObjectMethod(ref_, method);
  if (jstr == nullptr) {
    JThrowable exception = jni.GetAndClearException();
    if (exception.IsNull()) {
      Log::W("%s.toString returned null", clazz.GetName(jni).c_str());
    } else {
      Log::W("%s in %s.toString", exception.GetClass().GetName(jni).c_str(), clazz.GetName(jni).c_str());
    }
    return "";
  }
  return jni.GetStringValue(jstr);
}

string JObject::GetStringValue() const {
  return Jni(GetJni()).GetStringValue(ref_);
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
  Log::E("JNIEnv pointer has to be provided when using a global reference");
  abort();
}

jfieldID JClass::GetStaticFieldId(JNIEnv* jni_env, const char* name, const char* signature) const {
  auto field = jni_env->GetStaticFieldID(ref(), name, signature);
  if (field == nullptr) {
    GetJni()->ExceptionClear();
    Log::Fatal(FIELD_NOT_FOUND, "Unable to find the static %s.%s field with signature \"%s\"", GetName(jni_env).c_str(), name, signature);
  }
  return field;
}

jfieldID JClass::GetFieldId(JNIEnv* jni_env, const char* name, const char* signature) const {
  auto field = jni_env->GetFieldID(ref(), name, signature);
  if (field == nullptr) {
    GetJni()->ExceptionClear();
    Log::Fatal(FIELD_NOT_FOUND, "Unable to find the %s.%s field with signature \"%s\"", GetName(jni_env).c_str(), name, signature);
  }
  return field;
}

jmethodID JClass::GetStaticMethod(JNIEnv* jni_env, const char* name, const char* signature) const {
  auto method = jni_env->GetStaticMethodID(ref(), name, signature);
  if (method == nullptr) {
    GetJni()->ExceptionClear();
    Log::Fatal(METHOD_NOT_FOUND, "Unable to find the static %s.%s method with signature \"%s\"", GetName(jni_env).c_str(), name, signature);
  }
  return method;
}

jmethodID JClass::GetMethod(JNIEnv* jni_env, const char* name, const char* signature) const {
  auto method = jni_env->GetMethodID(ref(), name, signature);
  if (method == nullptr) {
    GetJni()->ExceptionClear();
    Log::Fatal(METHOD_NOT_FOUND, "Unable to find the %s.%s method with signature \"%s\"", GetName(jni_env).c_str(), name, signature);
  }
  return method;
}

jmethodID JClass::GetConstructor(JNIEnv* jni_env, const char* signature) const {
  auto constructor = jni_env->GetMethodID(ref(), "<init>", signature);
  if (constructor == nullptr) {
    GetJni()->ExceptionClear();
    Log::Fatal(CONSTRUCTOR_NOT_FOUND, "Unable to find the %s constructor with signature \"%s\"", GetName(jni_env).c_str(), signature);
  }
  return constructor;
}

jmethodID JClass::GetDeclaredOrInheritedMethod(JNIEnv* jni_env, const char* name, const char* signature) const {
  jmethodID method = jni_env->GetMethodID(ref(), name, signature);
  if (method != nullptr) {
    return method;
  }
  GetJni()->ExceptionClear();
  for (JClass clazz = GetSuperclass(jni_env); !clazz.IsNull(); clazz = clazz.GetSuperclass(jni_env)) {
    method = jni_env->GetMethodID(ref(), name, signature);
    if (method != nullptr) {
      return method;
    }
    jni_env->ExceptionClear();
  }
  Log::Fatal(METHOD_NOT_FOUND, "Unable to find the declared or inherited %s.%s method with signature \"%s\"",
             GetName(jni_env).c_str(), name, signature);
}

jmethodID JClass::FindMethod(JNIEnv* jni_env, const char* name, const char* signature) const {
  jmethodID method = jni_env->GetMethodID(ref(), name, signature);
  jboolean exception_thrown = jni_env->ExceptionCheck();
  if (exception_thrown) {
    jni_env->ExceptionClear();
  }
  return method;
}

jmethodID JClass::FindStaticMethod(JNIEnv* jni_env, const char* name, const char* signature) const {
  jmethodID method = jni_env->GetStaticMethodID(ref(), name, signature);
  jboolean exception_thrown = jni_env->ExceptionCheck();
  if (exception_thrown) {
    jni_env->ExceptionClear();
  }
  return method;
}

JClass JClass::GetSuperclass(JNIEnv* jni_env) const {
  return JClass(jni_env, jni_env->GetSuperclass(ref()));
}

string JClass::GetName(JNIEnv* jni_env) const {
  if (Jvm::class_get_name_method_ == nullptr) {
    // class_get_name_method_ is not initialized yet. This means that GetName was called indirectly by Jvm::Initialize.
    return "java.lang.Class";
  }
  auto name = down_cast<jstring>(jni_env->CallObjectMethod(ref(), Jvm::class_get_name_method_));
  if (name == nullptr) {
    // For some mysterious reason the java.lang.Class.getName method sometimes returns null,
    // but returns the correct class name when retried.
    name = down_cast<jstring>(jni_env->CallObjectMethod(ref(), Jvm::class_get_name_method_));
    if (name == nullptr) {
      return "<unknown_class>";
    }
  }
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
  if (result.IsNull()) {
    Jni jni(jni_env);
    JThrowable exception = jni.GetAndClearException();
    if (exception.IsNull()) {
      Log::Fatal(NULL_POINTER, "Unable to instantiate %s - constructor returned null", GetName(jni).c_str());
    } else {
      Log::Fatal(JAVA_EXCEPTION, "Unable to instantiate %s - %s", GetName(jni).c_str(), exception.Describe().c_str());
    }
  }
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
  GetJni()->CallStaticVoidMethodV(ref(), method, args);
  va_end(args);
}

void JClass::CallStaticVoidMethod(JNIEnv* jni_env, jmethodID method, ...) const {
  va_list args;
  va_start(args, method);
  jni_env->CallStaticVoidMethodV(ref(), method, args);
  va_end(args);
}

JObjectArray JClass::NewObjectArray(JNIEnv* jni_env, int32_t length, jobject initialElement) const {
  return JObjectArray(jni_env, jni_env->NewObjectArray(length, ref(), initialElement));
}

JString::JString(JNIEnv* jni_env, const char* value)
    : JRef(jni_env, value == nullptr ? nullptr : jni_env->NewStringUTF(value)) {
}

string JString::GetValue() const {
  if (IsNull()) {
    Log::Fatal(NULL_POINTER, "JString::GetValue is called on a null String");
  }
  const char* localName = GetJni()->GetStringUTFChars(ref(), nullptr);
  string result(localName);
  GetJni()->ReleaseStringUTFChars(ref(), localName);
  return result;
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
    Log::Fatal(CLASS_NOT_FOUND, "Unable to find the %s class", name);
  }
  return JClass(jni_env_, clazz);
}

string Jni::GetStringValue(jobject string_object) const {
  if (string_object == nullptr) {
    Log::Fatal(NULL_POINTER, "Jni::GetStringValue is called on a null object");
  }
  jstring str = static_cast<jstring>(string_object);
  const char* localName = jni_env_->GetStringUTFChars(str, nullptr);
  string result(localName);
  jni_env_->ReleaseStringUTFChars(str, localName);
  return result;
}

JCharArray Jni::NewCharArray(int32_t length) const {
  return JCharArray(jni_env_, jni_env_->NewCharArray(length));
}

bool Jni::CheckAndClearException() const {
  jboolean exception_thrown = jni_env_->ExceptionCheck();
  if (exception_thrown) {
    jni_env_->ExceptionClear();
  }
  return exception_thrown != JNI_FALSE;
}

JThrowable Jni::GetAndClearException() const {
  jthrowable exception = jni_env_->ExceptionOccurred();
  if (exception != nullptr) {
    jni_env_->ExceptionClear();
  }
  return JThrowable(jni_env_, exception);
}

string JThrowable::Describe() const {
  if (IsNull()) {
    Log::Fatal(NULL_POINTER, "Describe is called on a null object");
  }
  Jni jni = GetJni();
  JClass clazz = jni.GetClass("com/android/tools/screensharing/ThrowableHelper");
  jmethodID method = clazz.GetStaticMethod("describe", "(Ljava/lang/Throwable;)Ljava/lang/String;");
  JString str = JString(jni, jni->CallStaticObjectMethod(clazz.ref(), method, ref()));
  return str.GetValue();
}

int32_t JNumber::IntValue() {
  InitializeStatics(GetJni());
  return CallIntMethod(int_value_method_);
}

void JNumber::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex_);
  if (int_value_method_ == nullptr) {
    JClass iterable_class = jni.GetClass("java/lang/Number");
    int_value_method_ = iterable_class.GetMethod("intValue", "()I");
  }
}

jmethodID JNumber::int_value_method_ = nullptr;

JIterator JIterable::Iterator() {
  JNIEnv* jni = GetJni();
  InitializeStatics(jni);
  return JIterator(CallObjectMethod(iterator_method_));
}

void JIterable::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex_);
  if (iterator_method_ == nullptr) {
    JClass iterable_class = jni.GetClass("java/lang/Iterable");
    iterator_method_ = iterable_class.GetMethod("iterator", "()Ljava/util/Iterator;");
    JClass iterator_class = jni.GetClass("java/util/Iterator");
    JIterator::has_next_method_ = iterator_class.GetMethod("hasNext", "()Z");
    JIterator::next_method_ = iterator_class.GetMethod("next", "()Ljava/lang/Object;");
  }
}

jmethodID JIterable::iterator_method_ = nullptr;
jmethodID JIterator::has_next_method_ = nullptr;
jmethodID JIterator::next_method_ = nullptr;


JavaVM* Jvm::jvm_ = nullptr;
jint Jvm::jni_version_ = 0;
jmethodID Jvm::class_get_name_method_ = nullptr;

void Jvm::Initialize(JNIEnv* jni_env) {
  jni_env->GetJavaVM(&jvm_);
  jni_version_ = jni_env->GetVersion();
  JClass class_class = Jni(jni_env).GetClass("java/lang/Class");
  class_get_name_method_ = class_class.GetMethod("getName", "()Ljava/lang/String;");
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

[[noreturn]] void Jvm::Exit(int exitCode) {
  Jni jni = GetJni();
  JClass system = jni.GetClass("java/lang/System");
  jmethodID exit_method = system.GetStaticMethod(jni, "exit", "(I)V");
  system.CallStaticVoidMethod(exit_method, exitCode);
  assert(false);
}

}  // namespace screensharing
