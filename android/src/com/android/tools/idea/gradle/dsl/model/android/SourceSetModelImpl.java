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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceFileModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  @NonNls private static final String SET_ROOT = "setRoot";

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
  public ResolvedPropertyModel root() {
    GradleDslSimpleExpression rootElement =
      myDslElement.getPropertyElement(ImmutableList.of(ROOT, SET_ROOT), GradleDslSimpleExpression.class);

    SingleArgumentMethodTransform samt;
    if (rootElement == null || rootElement.getName().equals(SET_ROOT)) {
      samt = new SingleArgumentMethodTransform(SET_ROOT, myDslElement);
    }
    else {
      samt = new SingleArgumentMethodTransform(ROOT);
    }

    return rootElement != null ?
           GradlePropertyModelBuilder.create(rootElement).addTransform(samt).buildResolved() :
           GradlePropertyModelBuilder.create(myDslElement, SET_ROOT).asMethod(true).addTransform(samt).buildResolved();
  }

  @Override
  @NotNull
  public SourceDirectoryModel aidl() {
    SourceDirectoryDslElement aidl = myDslElement.getPropertyElement(AIDL, SourceDirectoryDslElement.class);
    if (aidl == null) {
      GradleNameElement name = GradleNameElement.create(AIDL);
      aidl = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(aidl);
    }
    return new SourceDirectoryModelImpl(aidl);
  }

  @Override
  public void removeAidl() {
    myDslElement.removeProperty(AIDL);
  }

  @Override
  @NotNull
  public SourceDirectoryModel assets() {
    SourceDirectoryDslElement assets = myDslElement.getPropertyElement(ASSETS, SourceDirectoryDslElement.class);
    if (assets == null) {
      GradleNameElement name = GradleNameElement.create(ASSETS);
      assets = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(assets);
    }
    return new SourceDirectoryModelImpl(assets);
  }

  @Override
  public void removeAssets() {
    myDslElement.removeProperty(ASSETS);
  }

  @Override
  @NotNull
  public SourceDirectoryModel java() {
    SourceDirectoryDslElement java = myDslElement.getPropertyElement(JAVA, SourceDirectoryDslElement.class);
    if (java == null) {
      GradleNameElement name = GradleNameElement.create(JAVA);
      java = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(java);
    }
    return new SourceDirectoryModelImpl(java);
  }

  @Override
  public void removeJava() {
    myDslElement.removeProperty(JAVA);
  }

  @Override
  @NotNull
  public SourceDirectoryModel jni() {
    SourceDirectoryDslElement jni = myDslElement.getPropertyElement(JNI, SourceDirectoryDslElement.class);
    if (jni == null) {
      GradleNameElement name = GradleNameElement.create(JNI);
      jni = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(jni);
    }
    return new SourceDirectoryModelImpl(jni);
  }

  @Override
  public void removeJni() {
    myDslElement.removeProperty(JNI);
  }

  @Override
  @NotNull
  public SourceDirectoryModel jniLibs() {
    SourceDirectoryDslElement jniLibs = myDslElement.getPropertyElement(JNI_LIBS, SourceDirectoryDslElement.class);
    if (jniLibs == null) {
      GradleNameElement name = GradleNameElement.create(JNI_LIBS);
      jniLibs = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(jniLibs);
    }
    return new SourceDirectoryModelImpl(jniLibs);
  }

  @Override
  public void removeJniLibs() {
    myDslElement.removeProperty(JNI_LIBS);
  }

  @Override
  @NotNull
  public SourceFileModel manifest() {
    SourceFileDslElement manifest = myDslElement.getPropertyElement(MANIFEST, SourceFileDslElement.class);
    if (manifest == null) {
      GradleNameElement name = GradleNameElement.create(MANIFEST);
      manifest = new SourceFileDslElement(myDslElement, name);
      myDslElement.setNewElement(manifest);
    }
    return new SourceFileModelImpl(manifest);
  }

  @Override
  public void removeManifest() {
    myDslElement.removeProperty(MANIFEST);
  }

  @Override
  @NotNull
  public SourceDirectoryModel renderscript() {
    SourceDirectoryDslElement renderscript = myDslElement.getPropertyElement(RENDERSCRIPT, SourceDirectoryDslElement.class);
    if (renderscript == null) {
      GradleNameElement name = GradleNameElement.create(RENDERSCRIPT);
      renderscript = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(renderscript);
    }
    return new SourceDirectoryModelImpl(renderscript);
  }

  @Override
  public void removeRenderscript() {
    myDslElement.removeProperty(RENDERSCRIPT);
  }

  @Override
  @NotNull
  public SourceDirectoryModel res() {
    SourceDirectoryDslElement res = myDslElement.getPropertyElement(RES, SourceDirectoryDslElement.class);
    if (res == null) {
      GradleNameElement name = GradleNameElement.create(RES);
      res = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(res);
    }
    return new SourceDirectoryModelImpl(res);
  }

  @Override
  public void removeRes() {
    myDslElement.removeProperty(RES);
  }

  @Override
  @NotNull
  public SourceDirectoryModel resources() {
    SourceDirectoryDslElement resources = myDslElement.getPropertyElement(RESOURCES, SourceDirectoryDslElement.class);
    if (resources == null) {
      GradleNameElement name = GradleNameElement.create(RESOURCES);
      resources = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(resources);
    }
    return new SourceDirectoryModelImpl(resources);
  }

  @Override
  public void removeResources() {
    myDslElement.removeProperty(RESOURCES);
  }
}
