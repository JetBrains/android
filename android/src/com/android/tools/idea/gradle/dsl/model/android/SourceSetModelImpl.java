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

import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceFileModelImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SourceSetModelImpl extends GradleDslBlockModel implements SourceSetModel {
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

  public SourceSetModelImpl(@NotNull SourceSetDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @NotNull
  public GradleNullableValue<String> root() {
    GradleDslExpression rootElement = myDslElement.getPropertyElement(ROOT, GradleDslExpression.class);
    if (rootElement == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
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

    return new GradleNullableValueImpl<>(rootElement, value);
  }

  @Override
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

  @Override
  @NotNull
  public SourceSetModel removeRoot() {
    myDslElement.removeProperty(ROOT);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel aidl() {
    SourceDirectoryDslElement aidl = myDslElement.getPropertyElement(AIDL, SourceDirectoryDslElement.class);
    if (aidl == null) {
      aidl = new SourceDirectoryDslElement(myDslElement, AIDL);
      myDslElement.setNewElement(AIDL, aidl);
    }
    return new SourceDirectoryModelImpl(aidl);
  }

  @Override
  @NotNull
  public SourceSetModel removeAidl() {
    myDslElement.removeProperty(AIDL);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel assets() {
    SourceDirectoryDslElement assets = myDslElement.getPropertyElement(ASSETS, SourceDirectoryDslElement.class);
    if (assets == null) {
      assets = new SourceDirectoryDslElement(myDslElement, ASSETS);
      myDslElement.setNewElement(ASSETS, assets);
    }
    return new SourceDirectoryModelImpl(assets);
  }

  @Override
  @NotNull
  public SourceSetModel removeAssets() {
    myDslElement.removeProperty(ASSETS);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel java() {
    SourceDirectoryDslElement java = myDslElement.getPropertyElement(JAVA, SourceDirectoryDslElement.class);
    if (java == null) {
      java = new SourceDirectoryDslElement(myDslElement, JAVA);
      myDslElement.setNewElement(JAVA, java);
    }
    return new SourceDirectoryModelImpl(java);
  }

  @Override
  @NotNull
  public SourceSetModel removeJava() {
    myDslElement.removeProperty(JAVA);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel jni() {
    SourceDirectoryDslElement jni = myDslElement.getPropertyElement(JNI, SourceDirectoryDslElement.class);
    if (jni == null) {
      jni = new SourceDirectoryDslElement(myDslElement, JNI);
      myDslElement.setNewElement(JNI, jni);
    }
    return new SourceDirectoryModelImpl(jni);
  }

  @Override
  @NotNull
  public SourceSetModel removeJni() {
    myDslElement.removeProperty(JNI);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel jniLibs() {
    SourceDirectoryDslElement jniLibs = myDslElement.getPropertyElement(JNI_LIBS, SourceDirectoryDslElement.class);
    if (jniLibs == null) {
      jniLibs = new SourceDirectoryDslElement(myDslElement, JNI_LIBS);
      myDslElement.setNewElement(JNI_LIBS, jniLibs);
    }
    return new SourceDirectoryModelImpl(jniLibs);
  }

  @Override
  @NotNull
  public SourceSetModel removeJniLibs() {
    myDslElement.removeProperty(JNI_LIBS);
    return this;
  }

  @Override
  @NotNull
  public SourceFileModel manifest() {
    SourceFileDslElement manifest = myDslElement.getPropertyElement(MANIFEST, SourceFileDslElement.class);
    if (manifest == null) {
      manifest = new SourceFileDslElement(myDslElement, MANIFEST);
      myDslElement.setNewElement(MANIFEST, manifest);
    }
    return new SourceFileModelImpl(manifest);
  }

  @Override
  @NotNull
  public SourceSetModel removeManifest() {
    myDslElement.removeProperty(MANIFEST);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel renderscript() {
    SourceDirectoryDslElement renderscript = myDslElement.getPropertyElement(RENDERSCRIPT, SourceDirectoryDslElement.class);
    if (renderscript == null) {
      renderscript = new SourceDirectoryDslElement(myDslElement, RENDERSCRIPT);
      myDslElement.setNewElement(RENDERSCRIPT, renderscript);
    }
    return new SourceDirectoryModelImpl(renderscript);
  }

  @Override
  @NotNull
  public SourceSetModel removeRenderscript() {
    myDslElement.removeProperty(RENDERSCRIPT);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel res() {
    SourceDirectoryDslElement res = myDslElement.getPropertyElement(RES, SourceDirectoryDslElement.class);
    if (res == null) {
      res = new SourceDirectoryDslElement(myDslElement, RES);
      myDslElement.setNewElement(RES, res);
    }
    return new SourceDirectoryModelImpl(res);
  }

  @Override
  @NotNull
  public SourceSetModel removeRes() {
    myDslElement.removeProperty(RES);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel resources() {
    SourceDirectoryDslElement resources = myDslElement.getPropertyElement(RESOURCES, SourceDirectoryDslElement.class);
    if (resources == null) {
      resources = new SourceDirectoryDslElement(myDslElement, RESOURCES);
      myDslElement.setNewElement(RESOURCES, resources);
    }
    return new SourceDirectoryModelImpl(resources);
  }

  @Override
  @NotNull
  public SourceSetModel removeResources() {
    myDslElement.removeProperty(RESOURCES);
    return this;
  }
}
