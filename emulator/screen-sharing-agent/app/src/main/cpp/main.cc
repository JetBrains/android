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

#include "agent.h"
#include "jvm.h"
#include "log.h"

using namespace std;
using namespace screensharing;

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_Main_nativeMain(JNIEnv* jni_env, jclass thisClass, jobjectArray argArray) {
  Log::I("Screen sharing agent started");
  Jvm::Initialize(jni_env);

  int argc = jni_env->GetArrayLength(argArray);
  vector<string> args(static_cast<size_t>(argc));

  for (int i = 0; i < argc; i++) {
    JString arg(jni_env, jni_env->GetObjectArrayElement(argArray, i));
    args.push_back(arg.GetValue());
  }

  {
    Agent agent(args);
    agent.Run();
  }
  Log::I("Screen sharing agent stopped");
  // Exit explicitly to bypass the final JVM cleanup that for some unclear reason sometimes crashes with SIGSEGV.
  exit(EXIT_SUCCESS);
}
