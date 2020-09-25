/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.ndk

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun RecipeExecutor.generateBasicJniBindings(
  data: ModuleTemplateData,
  language: Language,
  className: String,
  nativeSourceName: String
) {

  val pn = data.packageName.replace("_", "_1").replace('.', '_')

  val escapcedPackageName = escapeKotlinIdentifier(data.packageName)
  when (language) {
    Language.Java -> {
      save(jniBindingJava(escapcedPackageName, className), data.srcDir.resolve("$className.java"))
    }
    Language.Kotlin -> {
      save(jniBindingKotlin(escapcedPackageName, className), data.srcDir.resolve("$className.kt"))
    }
  }

  with(data.rootDir.resolve("src/main/cpp")) {
    createDirectory(this)
    save(jniBindingCpp(pn, className), resolve(nativeSourceName))
  }
}

private fun jniBindingJava(escapedPackageName: String, className: String): String = //language=JAVA
  """
package $escapedPackageName;

public class $className {

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
"""

private fun jniBindingKotlin(escapedPackageName: String, className: String): String = //language=kotlin
  """
package $escapedPackageName

class $className {

    /**
      * A native method that is implemented by the 'native-lib' native library,
      * which is packaged with this application.
      */
    external fun stringFromJNI(): String
    
    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
"""

private fun jniBindingCpp(pn: String, className: String): String = """
#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_${pn}_${className}_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
""".trimIndent()
