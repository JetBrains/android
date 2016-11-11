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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SourceSetModel extends GradleDslBlockModel {
  @NonNls private static final String AIDL = "aidl";
  @NonNls private static final String ASSETS = "assets";
  @NonNls private static final String JAVA = "java";
  @NonNls private static final String JNI = "jni";
  @NonNls private static final String JNI_LIBS = "jniLibs";
  @NonNls private static final String MANIFEST = "manifest";
  @NonNls private static final String RENDERSCRIPT = "renderscript";
  @NonNls private static final String RES = "res";
  @NonNls private static final String RESOURCES = "resources";
  @NonNls private static final String ROOT = "root";

  public SourceSetModel(@NotNull SourceSetDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @NotNull
  public GradleNullableValue<String> root() {
    GradleDslExpression rootElement = myDslElement.getPropertyElement(ROOT, GradleDslExpression.class);
    if (rootElement == null) {
      return new GradleNullableValue<>(myDslElement, null);
    }

    String value = null;
    if (rootElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)rootElement).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement pathArgument = arguments.get(0);
        if (pathArgument instanceof GradleDslExpression) {
          value = ((GradleDslExpression)pathArgument).getValue(String.class);
        }
      }
    }
    else {
      value = rootElement.getValue(String.class);
    }

    return new GradleNullableValue<>(rootElement, value);
  }

  @NotNull
  public SourceSetModel setRoot(@NotNull String root) {
    GradleDslExpression rootElement = myDslElement.getPropertyElement(ROOT, GradleDslExpression.class);
    if (rootElement == null) {
      myDslElement.setNewLiteral(ROOT, root);
      return this;
    }

    if (rootElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)rootElement).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement pathArgument = arguments.get(0);
        if (pathArgument instanceof GradleDslExpression) {
          ((GradleDslExpression)pathArgument).setValue(root);
          return this;
        }
      }
    }

    rootElement.setValue(root);
    return this;
  }

  @NotNull
  public SourceSetModel removeRoot() {
    myDslElement.removeProperty(ROOT);
    return this;
  }

  @NotNull
  public SourceDirectoryModel aidl() {
    SourceDirectoryDslElement aidl = myDslElement.getPropertyElement(AIDL, SourceDirectoryDslElement.class);
    if (aidl == null) {
      aidl = new SourceDirectoryDslElement(myDslElement, AIDL);
      myDslElement.setNewElement(AIDL, aidl);
    }
    return new SourceDirectoryModel(aidl);
  }

  @NotNull
  public SourceSetModel removeAidl() {
    myDslElement.removeProperty(AIDL);
    return this;
  }

  @NotNull
  public SourceDirectoryModel assets() {
    SourceDirectoryDslElement assets = myDslElement.getPropertyElement(ASSETS, SourceDirectoryDslElement.class);
    if (assets == null) {
      assets = new SourceDirectoryDslElement(myDslElement, ASSETS);
      myDslElement.setNewElement(ASSETS, assets);
    }
    return new SourceDirectoryModel(assets);
  }

  @NotNull
  public SourceSetModel removeAssets() {
    myDslElement.removeProperty(ASSETS);
    return this;
  }

  @NotNull
  public SourceDirectoryModel java() {
    SourceDirectoryDslElement java = myDslElement.getPropertyElement(JAVA, SourceDirectoryDslElement.class);
    if (java == null) {
      java = new SourceDirectoryDslElement(myDslElement, JAVA);
      myDslElement.setNewElement(JAVA, java);
    }
    return new SourceDirectoryModel(java);
  }

  @NotNull
  public SourceSetModel removeJava() {
    myDslElement.removeProperty(JAVA);
    return this;
  }

  @NotNull
  public SourceDirectoryModel jni() {
    SourceDirectoryDslElement jni = myDslElement.getPropertyElement(JNI, SourceDirectoryDslElement.class);
    if (jni == null) {
      jni = new SourceDirectoryDslElement(myDslElement, JNI);
      myDslElement.setNewElement(JNI, jni);
    }
    return new SourceDirectoryModel(jni);
  }

  @NotNull
  public SourceSetModel removeJni() {
    myDslElement.removeProperty(JNI);
    return this;
  }

  @NotNull
  public SourceDirectoryModel jniLibs() {
    SourceDirectoryDslElement jniLibs = myDslElement.getPropertyElement(JNI_LIBS, SourceDirectoryDslElement.class);
    if (jniLibs == null) {
      jniLibs = new SourceDirectoryDslElement(myDslElement, JNI_LIBS);
      myDslElement.setNewElement(JNI_LIBS, jniLibs);
    }
    return new SourceDirectoryModel(jniLibs);
  }

  @NotNull
  public SourceSetModel removeJniLibs() {
    myDslElement.removeProperty(JNI_LIBS);
    return this;
  }

  @NotNull
  public SourceFileModel manifest() {
    SourceFileDslElement manifest = myDslElement.getPropertyElement(MANIFEST, SourceFileDslElement.class);
    if (manifest == null) {
      manifest = new SourceFileDslElement(myDslElement, MANIFEST);
      myDslElement.setNewElement(MANIFEST, manifest);
    }
    return new SourceFileModel(manifest);
  }

  @NotNull
  public SourceSetModel removeManifest() {
    myDslElement.removeProperty(MANIFEST);
    return this;
  }

  @NotNull
  public SourceDirectoryModel renderscript() {
    SourceDirectoryDslElement renderscript = myDslElement.getPropertyElement(RENDERSCRIPT, SourceDirectoryDslElement.class);
    if (renderscript == null) {
      renderscript = new SourceDirectoryDslElement(myDslElement, RENDERSCRIPT);
      myDslElement.setNewElement(RENDERSCRIPT, renderscript);
    }
    return new SourceDirectoryModel(renderscript);
  }

  @NotNull
  public SourceSetModel removeRenderscript() {
    myDslElement.removeProperty(RENDERSCRIPT);
    return this;
  }

  @NotNull
  public SourceDirectoryModel res() {
    SourceDirectoryDslElement res = myDslElement.getPropertyElement(RES, SourceDirectoryDslElement.class);
    if (res == null) {
      res = new SourceDirectoryDslElement(myDslElement, RES);
      myDslElement.setNewElement(RES, res);
    }
    return new SourceDirectoryModel(res);
  }

  @NotNull
  public SourceSetModel removeRes() {
    myDslElement.removeProperty(RES);
    return this;
  }

  @NotNull
  public SourceDirectoryModel resources() {
    SourceDirectoryDslElement resources = myDslElement.getPropertyElement(RESOURCES, SourceDirectoryDslElement.class);
    if (resources == null) {
      resources = new SourceDirectoryDslElement(myDslElement, RESOURCES);
      myDslElement.setNewElement(RESOURCES, resources);
    }
    return new SourceDirectoryModel(resources);
  }

  @NotNull
  SourceSetModel removeResources() {
    myDslElement.removeProperty(RESOURCES);
    return this;
  }
}
