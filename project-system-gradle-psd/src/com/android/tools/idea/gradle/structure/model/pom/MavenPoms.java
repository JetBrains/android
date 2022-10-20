/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.pom;

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import java.util.ArrayList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.*;

public class MavenPoms {
  private static final Logger LOG = Logger.getInstance(MavenPoms.class);

  private MavenPoms() {
  }

  @NotNull
  public static List<PsArtifactDependencySpec> findDependenciesInPomFile(@Nullable File pomFilePath) {
    if (pomFilePath == null || !pomFilePath.isFile()) {
      return Collections.emptyList();
    }
    List<PsArtifactDependencySpec> dependencies = new ArrayList<>();
    try {
      Element rootElement = JDOMUtil.load(pomFilePath);
      Element dependenciesElement = null;
      for (Element childElement : rootElement.getChildren()) {
        if ("dependencies".equals(childElement.getName())) {
          dependenciesElement = childElement;
          break;
        }
      }

      if (dependenciesElement != null) {
        for (Element childElement : dependenciesElement.getChildren()) {
          if ("dependency".equals(childElement.getName())) {
            PsArtifactDependencySpec spec = createSpec(childElement);
            if (spec != null) {
              dependencies.add(spec);
            }
          }
        }
      }
    }
    catch (Exception e) {
      String msg = String.format("Failed to obtain dependencies in POM file '%1$s", pomFilePath.getPath());
      LOG.warn(msg, e);
    }
    return dependencies;
  }

  @Nullable
  private static PsArtifactDependencySpec createSpec(@NotNull Element dependencyElement) {
    String artifactId = null;
    String groupId = null;
    String version = null;
    String scope = null;
    boolean optional = false;
    for (Element childElement : dependencyElement.getChildren()) {
      String name = childElement.getName();
      if ("artifactId".equals(name)) {
        artifactId = textOf(childElement);
      }
      else if ("groupId".equals(name)) {
        groupId = textOf(childElement);
      }
      else if ("version".equals(name)) {
        version = textOf(childElement);
      }
      else if ("optional".equals(name)) {
        optional = Boolean.valueOf(textOf(childElement));
      }
      else if ("scope".equals(name)) {
        scope = textOf(childElement);
      }
    }
    if (isNotEmpty(artifactId) && !optional && ("compile".equals(scope) || "runtime".equals(scope) || isEmpty(scope))) {
      return PsArtifactDependencySpec.Companion.create(groupId, artifactId, version);
    }

    return null;
  }

  @Nullable
  private static String textOf(@NotNull Element e) {
    return nullize(e.getText(), true);
  }
}
