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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.DECLARATIVE_TOML;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;

public class BuildTypeProcessor {

  // interfaces below were introduced for type safety when call runnables for Declarative and Script
  interface BuildFileType {
  }

  static public abstract class ScriptBuildType implements BuildFileType{
  }

  static public abstract class DeclarativeBuildType implements BuildFileType{
  }

  static public abstract class BuildTypeRunnable<T extends BuildFileType> implements Runnable{

  }

  static public void run(GradleDslElement element,
                         BuildTypeRunnable<DeclarativeBuildType> runForDeclarative,
                         BuildTypeRunnable<ScriptBuildType> runForScript) {
    if (element.getDslFile().getParser().getKind() == DECLARATIVE_TOML) {
      runForDeclarative.run();
    }
    else {
      runForScript.run();
    }
  }
}
