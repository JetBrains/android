/*
 * Copyright (C) 2026 The Android Open Source Project
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

#include "accessors/agent_context.h"

#include "accessors/looper.h"
#include "jvm.h"

namespace screensharing {

using namespace std;

void AgentContext::Initialize(Jni jni) {
  promise<JObject> context_promise;
  main_looper_thread_.Start("MainLooper", [&context_promise]() { CreateContext(&context_promise); });
  context_ = context_promise.get_future().get();
}

void AgentContext::CreateContext(promise<JObject>* context_promise) {
  Jni jni = Jvm::GetJni();
  Log::D("AgentContext: Initializing main looper");
  Looper::SetMainLooper(jni, Looper::Create(jni));

  JClass agent_context_class = jni.GetClass("com/android/tools/screensharing/AgentContext");
  jfieldID instance_field = agent_context_class.GetStaticFieldId("INSTANCE", "Lcom/android/tools/screensharing/AgentContext;");
  JObject context = JObject(jni, jni->GetStaticObjectField(agent_context_class.ref(), instance_field));
  context.MakeGlobal();
  context_promise->set_value(std::move(context));
  Looper::Loop();
  Log::D("AgentContext: Terminating main looper thread");
}

void AgentContext::StopMainLooper() {
  Log::D("AgentContext: Stopping main looper");
  Looper::GetMainLooper(Jvm::GetJni()).Quit();
}

ThreadHandle AgentContext::main_looper_thread_;
JObject AgentContext::context_;

extern "C"
JNIEXPORT jobject JNICALL
Java_com_android_tools_screensharing_AgentContext_getSystemContext(JNIEnv* jni_env, jclass clazz) {
  Jni jni(jni_env);

  JClass activity_thread_class = jni.GetClass("android/app/ActivityThread");
  jmethodID system_main_method = activity_thread_class.GetStaticMethod("systemMain", "()Landroid/app/ActivityThread;");
  JObject activity_thread = activity_thread_class.CallStaticObjectMethod(jni, system_main_method);

  jfieldID current_activity_thread_field =
      activity_thread_class.GetStaticFieldId(jni, "sCurrentActivityThread", "Landroid/app/ActivityThread;");
  jni->SetStaticObjectField(activity_thread_class.ref(), current_activity_thread_field, activity_thread.ref());

  jfieldID system_thread_field = activity_thread_class.GetFieldId(jni, "mSystemThread", "Z");
  jni->SetBooleanField(activity_thread.ref(), system_thread_field, JNI_TRUE);

  jmethodID get_system_context_method = activity_thread_class.GetMethod(jni, "getSystemContext", "()Landroid/app/ContextImpl;");
  JObject system_context = activity_thread.CallObjectMethod(jni, get_system_context_method);
  if (system_context.IsNull()) {
    Log::E(jni.GetAndClearException(), "Unable to get system context");
  }
  return system_context.Release();
}

}  // namespace screensharing
