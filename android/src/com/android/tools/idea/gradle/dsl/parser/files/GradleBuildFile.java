/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;


import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslReference;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.SOURCE_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.TARGET_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA_BLOCK_NAME;

public class GradleBuildFile extends GradleDslFile {
  public GradleBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    super(file, project, moduleName);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(property) && element instanceof GradleDslExpressionMap) {
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this);
        super.addParsedElement(APPLY_BLOCK_NAME, applyDslElement);
      }
      for (Map.Entry<String, GradleDslElement> entry : ((GradleDslExpressionMap)element).getPropertyElements().entrySet()) {
        applyDslElement.addParsedElement(entry.getKey(), entry.getValue());
      }
      return;
    }
    super.addParsedElement(property, element);
  }

  @Override
  public void setParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if ((SOURCE_COMPATIBILITY_ATTRIBUTE_NAME.equals(property) || TARGET_COMPATIBILITY_ATTRIBUTE_NAME.equals(property)) &&
        (element instanceof GradleDslLiteral || element instanceof GradleDslReference)) {
      JavaDslElement javaDslElement = getPropertyElement(JAVA_BLOCK_NAME, JavaDslElement.class);
      if (javaDslElement == null) {
        javaDslElement = new JavaDslElement(this);
        super.setParsedElement(JAVA_BLOCK_NAME, javaDslElement);
      }
      javaDslElement.setParsedElement(property, element);
      return;
    }
    super.setParsedElement(property, element);
  }
}
