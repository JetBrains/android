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

#pragma once

#include <jni.h>

#include <string>
#include <vector>

#include "casts.h"
#include "common.h"

#pragma clang diagnostic push
#pragma ide diagnostic ignored "HidingNonVirtualFunction"

namespace screensharing {

class Jni;
class JObject;
class JClass;

// Object oriented wrapper around jobject.
class JObject {
public:
  JObject() noexcept
    : jni_env_(nullptr),
      ref_(nullptr) {
  };
  JObject(JNIEnv* jni_env, jobject&& ref) noexcept
    : jni_env_(jni_env),
      ref_(ref) {
  }
  JObject(JObject&& other) noexcept
    : jni_env_(other.jni_env_),
      ref_(other.ref_) {
    other.ref_ = nullptr;
  }
  JObject(JNIEnv* jni_env, JObject&& other)
      : jni_env_(jni_env),
        ref_(other.Release()) {
  }
  JObject(JNIEnv* jni_env, JObject& other) = delete;

  ~JObject() {
    DeleteRef();
  }

  JObject& operator=(JObject&& other) noexcept;

  // Returns the underlying Java object reference.
  operator jobject() const {
    return ref_;
  }

  // Returns the underlying Java object reference.
  [[nodiscard]] jobject ref() const {
    return ref_;
  }

  // Returns the underlying jobject and releases the ownership.
  jobject Release() {
    auto ref = ref_;
    ref_ = nullptr;
    return ref;
  }

  [[nodiscard]] bool IsNull() const {
    return ref_ == nullptr;
  }

  [[nodiscard]] bool IsNotNull() const {
    return ref_ != nullptr;
  }

  // Converts a local reference to a global one and deletes the local reference.
  JObject& MakeGlobal();
  [[nodiscard]] JObject&& ToGlobal()&& {
    return std::move(MakeGlobal());
  }

  [[nodiscard]] JClass GetClass() const;
  [[nodiscard]] JClass GetClass(JNIEnv* jni) const;
  JObject CallObjectMethod(jmethodID method, ...) const;
  JObject CallObjectMethod(JNIEnv* jni_env, jmethodID method, ...) const;
  bool CallBooleanMethod(jmethodID method, ...) const;
  bool CallBooleanMethod(JNIEnv* jni_env, jmethodID method, ...) const;
  int32_t CallIntMethod(jmethodID method, ...) const;
  int32_t CallIntMethod(JNIEnv* jni_env, jmethodID method, ...) const;
  void CallVoidMethod(jmethodID method, ...) const;
  void CallVoidMethod(JNIEnv* jni_env, jmethodID method, ...) const;
  [[nodiscard]] JObject GetObjectField(jfieldID field) const {
    return GetObjectField(GetJni(), field);
  }
  [[nodiscard]] JObject GetObjectField(JNIEnv* jni_env, jfieldID field) const;
  void SetObjectField(jfieldID field, jobject value) const {
    SetObjectField(GetJni(), field, value);
  }
  void SetObjectField(JNIEnv* jni_env, jfieldID field, jobject value) const;
  [[nodiscard]] int32_t GetIntField(jfieldID field) const {
    return GetIntField(GetJni(), field);
  }
  [[nodiscard]] int32_t GetIntField(JNIEnv* jni_env, jfieldID field) const;
  void SetIntField(jfieldID field, int32_t value) const {
    SetIntField(GetJni(), field, value);
  }
  void SetIntField(JNIEnv* jni_env, jfieldID field, int32_t value) const;
  [[nodiscard]] float GetFloatField(jfieldID field) const {
    return GetFloatField(GetJni(), field);
  }
  [[nodiscard]] float GetFloatField(JNIEnv* jni_env, jfieldID field) const;
  void SetFloatField(jfieldID field, float value) const {
    SetFloatField(GetJni(), field, value);
  }
  void SetFloatField(JNIEnv* jni_env, jfieldID field, float value) const;
  // Returns a std::string value of this JObject if it represents a java.lang.String. It is illegal to call
  // this method on an object that is not a java.lang.String.
  [[nodiscard]] std::string GetStringValue() const;
  // Calls the toString() method on the Java object. Intended for debugging only and may be slow.
  [[nodiscard]] std::string ToString() const;

protected:
  [[nodiscard]] JNIEnv* GetJni() const {
    if (jni_env_ != nullptr) {
      return jni_env_;
    }
    IllegalGlobalReferenceUse();
  }

private:
  void DeleteRef() noexcept;
  [[noreturn]] static void IllegalGlobalReferenceUse();

  JNIEnv* jni_env_;  // Non-null for local and null for global references.
  jobject ref_;

  DISALLOW_COPY_AND_ASSIGN(JObject);
};

template <typename Wrapper, typename Base>
class JRef : public JObject {
public:
  using JObject::JObject;
  JRef(JNIEnv* jni_env, Base&& ref)
    : JObject(jni_env, ref) {
  }
  explicit JRef(JObject&& other) noexcept
    : JObject(std::move(other)) {
  }

  Wrapper& operator=(Wrapper&& other) noexcept {
    return down_cast<Wrapper&>(JObject::operator=(static_cast<JObject&&>(other)));
  }

  // Returns the underlying Java object reference. Do not pass that reference to a constructor of
  // JObject or a derived class since it would result in double deletion.
  operator Base() const {
    return ref();
  }

  // Returns the underlying Java object reference. Do not pass that reference to a constructor of
  // JObject or a derived class since it would result in double deletion.
  [[nodiscard]] Base ref() const {
    return down_cast<Base>(JObject::ref());
  }

  Wrapper& MakeGlobal() {
    return down_cast<Wrapper&>(JObject::MakeGlobal());
  };
  [[nodiscard]] Wrapper&& ToGlobal()&& {
    return std::move(MakeGlobal());
  };

  template <class U = Base>
  typename std::enable_if<std::is_base_of<_jarray, std::remove_pointer_t<U>>::value, int32_t>::type
  GetLength() const {
    return GetJni()->GetArrayLength(ref());
  }
};

class JObjectArray : public JRef<JObjectArray, jobjectArray> {
public:
  using JRef::JRef;

  [[nodiscard]] JObject GetElement(int32_t index) const {
    return GetElement(GetJni(), index);
  }
  [[nodiscard]] JObject GetElement(JNIEnv* jni_env, int32_t index) const;
  void SetElement(int32_t index, const JObject& element) const {
    SetElement(GetJni(), index, element);
  }
  void SetElement(JNIEnv* jni_env, int32_t index, const JObject& element) const;
};

// Object oriented wrapper around jclass.
class JClass : public JRef<JClass, jclass> {
public:
  using JRef::JRef;

  [[nodiscard]] jfieldID GetStaticFieldId(const char* name, const char* signature) const {
    return GetStaticFieldId(GetJni(), name, signature);
  }
  [[nodiscard]] jfieldID GetStaticFieldId(JNIEnv* jni_env, const char* name, const char* signature) const;
  [[nodiscard]] jfieldID GetFieldId(const char* name, const char* signature) const {
    return GetFieldId(GetJni(), name, signature);
  }
  [[nodiscard]] jfieldID GetFieldId(JNIEnv* jni_env, const char* name, const char* signature) const;

  [[nodiscard]] jmethodID GetStaticMethod(const char* name, const char* signature) const {
    return GetStaticMethod(GetJni(), name, signature);
  }
  [[nodiscard]] jmethodID GetStaticMethod(JNIEnv* jni_env, const char* name, const char* signature) const;
  [[nodiscard]] jmethodID GetMethod(const char* name, const char* signature) const {
    return GetMethod(GetJni(), name, signature);
  }
  [[nodiscard]] jmethodID GetMethod(JNIEnv* jni_env, const char* name, const char* signature) const;
  [[nodiscard]] jmethodID GetConstructor(const char* signature) const {
    return GetConstructor(GetJni(), signature);
  }
  [[nodiscard]] jmethodID GetConstructor(JNIEnv* jni_env, const char* signature) const;
  [[nodiscard]] jmethodID GetDeclaredOrInheritedMethod(const char* name, const char* signature) const {
    return GetDeclaredOrInheritedMethod(GetJni(), name, signature);
  }
  [[nodiscard]] jmethodID GetDeclaredOrInheritedMethod(JNIEnv* jni_env, const char* name, const char* signature) const;

  // Similar to GetMethod, but gracefully handles a non-existent method by returning nullptr.
  [[nodiscard]] jmethodID FindMethod(const char* name, const char* signature) const {
    return FindMethod(GetJni(), name, signature);
  }
  [[nodiscard]] jmethodID FindMethod(JNIEnv* jni_env, const char* name, const char* signature) const;
  // Similar to GetStaticMethod, but gracefully handles a non-existent method by returning nullptr.
  [[nodiscard]] jmethodID FindStaticMethod(const char* name, const char* signature) const {
    return FindStaticMethod(GetJni(), name, signature);
  }
  [[nodiscard]] jmethodID FindStaticMethod(JNIEnv* jni_env, const char* name, const char* signature) const;

  [[nodiscard]] JObject NewObject(jmethodID constructor, ...) const;
  [[nodiscard]] JObject NewObject(JNIEnv* jni_env, jmethodID constructor, ...) const;
  [[nodiscard]] JObjectArray NewObjectArray(int32_t length, jobject initialElement) const {
    return NewObjectArray(GetJni(), length, initialElement);
  }
  [[nodiscard]] JObjectArray NewObjectArray(JNIEnv* jni_env, int32_t length, jobject initialElement) const;
  JObject CallStaticObjectMethod(jmethodID method, ...) const;
  JObject CallStaticObjectMethod(JNIEnv* jni_env, jmethodID method, ...) const;
  void CallStaticVoidMethod(jmethodID method, ...) const;
  void CallStaticVoidMethod(JNIEnv* jni_env, jmethodID method, ...) const;

  [[nodiscard]] JClass GetSuperclass(JNIEnv* jni_env) const;
  // Returns the name of the Java class.
  [[nodiscard]] std::string GetName(JNIEnv* jni_env) const;
};

class JString : public JRef<JString, jstring> {
public:
  using JRef::JRef;
  JString(JNIEnv* jni_env, const char* value);
  JString(JNIEnv* jni_env, const std::string& value) : JString(jni_env, value.c_str()) {}

  JString& MakeGlobal() {
    return down_cast<JString&>(JRef::MakeGlobal());
  };
  [[nodiscard]] JString&& ToGlobal()&& {
    return std::move(MakeGlobal());
  };

  [[nodiscard]] std::string GetValue() const;
  // Returns the result of calling String.valueOf(obj).
  [[nodiscard]] static std::string ValueOf(jobject obj);

private:
  static void InitializeStatics(Jni jni);

  static JClass string_class_;
  static jmethodID value_of_method_;
};

class JCharArray : public JRef<JCharArray, jcharArray> {
public:
  using JRef::JRef;

  void SetRegion(int32_t start, int32_t len, const uint16_t* chars) const {
    SetRegion(GetJni(), start, len, chars);
  }
  void SetRegion(JNIEnv* jni_env, int32_t start, int32_t len, const uint16_t* chars) const {
    jni_env->SetCharArrayRegion(ref(), start, len, chars);
  }
};

class JThrowable : public JRef<JThrowable, jthrowable> {
public:
  using JRef::JRef;

  [[nodiscard]] std::string Describe() const;
};

class JNumber : public JObject {
public:
  using JObject::JObject;
  explicit JNumber(JObject&& number) noexcept
      : JObject::JObject(std::move(number)) {}

  [[nodiscard]] int32_t IntValue();

private:
  static void InitializeStatics(Jni jni);

  static jmethodID int_value_method_;
};

class JIterator : public JObject {
public:
  using JObject::JObject;
  explicit JIterator(JObject&& iterator) noexcept
      : JObject::JObject(std::move(iterator)) {}

  [[nodiscard]] bool HasNext() {
    return CallBooleanMethod(has_next_method_);
  }
  [[nodiscard]] JObject Next() {
    return CallObjectMethod(next_method_);
  }

private:
  friend class JIterable;

  static jmethodID has_next_method_;
  static jmethodID next_method_;
};

class JIterable : JObject {
public:
  using JObject::JObject;
  explicit JIterable(JObject&& iterable) noexcept
      : JObject::JObject(std::move(iterable)) {}

  [[nodiscard]] JIterator Iterator();

private:
  static void InitializeStatics(Jni jni);

  static jmethodID iterator_method_;
};

class Jni {
public:
  Jni(JNIEnv* jni_env)
    : jni_env_(jni_env) {
  }

  operator JNIEnv*() const {
    return jni_env_;
  }

  JNIEnv* operator ->() const {
    return jni_env_;
  }

  JClass GetClass(const char* name) const;

  [[nodiscard]] JCharArray NewCharArray(int32_t length) const;

  std::vector<int64_t> GetElements(jlongArray array) {
    jsize size = jni_env_->GetArrayLength(array);
    std::vector<int64_t> result(size);
    jni_env_->GetLongArrayRegion(array, 0, size, result.data());
    return result;
  }

  bool CheckAndClearException() const;
  [[nodiscard]] JThrowable GetAndClearException() const;

private:
  JNIEnv* jni_env_;
};

// Provides access to JNI environment.
class Jvm {
public:
  static void Initialize(JNIEnv* jni_env);
  static Jni AttachCurrentThread(const char* thread_name);
  static void DetachCurrentThread();
  // Returns the JNI environment for the current thread.
  [[nodiscard]] static JNIEnv* GetJni() {
    JNIEnv* jni_env;
    jvm_->GetEnv(reinterpret_cast<void**>(&jni_env), jni_version_);
    return jni_env;
  }

  // Calls the System.exit method.
  [[noreturn]] static void Exit(int exitCode);

  Jvm() = delete;

private:
  friend class JClass;

  static JavaVM* jvm_;
  static jint jni_version_;
  static jmethodID class_get_name_method_;  // The java.lang.Class.getName method.
};

}  // namespace screensharing

#pragma clang diagnostic pop