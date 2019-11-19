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
  public void rename(@NotNull String newName) {
    myDslElement.getNameElement().rename(newName);
    myDslElement.setModified();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel root() {
    GradleDslSimpleExpression rootElement =
      myDslElement.getPropertyElement(ImmutableList.of(ROOT, SET_ROOT), GradleDslSimpleExpression.class);

    return rootElement != null ?
           GradlePropertyModelBuilder.create(rootElement).buildResolved() :
           GradlePropertyModelBuilder.create(myDslElement, SET_ROOT).asMethod(true).buildResolved();
  }

  @Override
  @NotNull
  public SourceDirectoryModel aidl() {
    SourceDirectoryDslElement aidl = myDslElement.ensureNamedPropertyElement(AIDL, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(aidl);
  }

  @Override
  public void removeAidl() {
    myDslElement.removeProperty(AIDL);
  }

  @Override
  @NotNull
  public SourceDirectoryModel assets() {
    SourceDirectoryDslElement assets = myDslElement.ensureNamedPropertyElement(ASSETS, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(assets);
  }

  @Override
  public void removeAssets() {
    myDslElement.removeProperty(ASSETS);
  }

  @Override
  @NotNull
  public SourceDirectoryModel java() {
    SourceDirectoryDslElement java = myDslElement.ensureNamedPropertyElement(JAVA, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(java);
  }

  @Override
  public void removeJava() {
    myDslElement.removeProperty(JAVA);
  }

  @Override
  @NotNull
  public SourceDirectoryModel jni() {
    SourceDirectoryDslElement jni = myDslElement.ensureNamedPropertyElement(JNI, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(jni);
  }

  @Override
  public void removeJni() {
    myDslElement.removeProperty(JNI);
  }

  @Override
  @NotNull
  public SourceDirectoryModel jniLibs() {
    SourceDirectoryDslElement jniLibs = myDslElement.ensureNamedPropertyElement(JNI_LIBS, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(jniLibs);
  }

  @Override
  public void removeJniLibs() {
    myDslElement.removeProperty(JNI_LIBS);
  }

  @Override
  @NotNull
  public SourceFileModel manifest() {
    SourceFileDslElement manifest = myDslElement.ensureNamedPropertyElement(MANIFEST, SourceFileDslElement.class);
    return new SourceFileModelImpl(manifest);
  }

  @Override
  public void removeManifest() {
    myDslElement.removeProperty(MANIFEST);
  }

  @Override
  @NotNull
  public SourceDirectoryModel renderscript() {
    SourceDirectoryDslElement renderscript = myDslElement.ensureNamedPropertyElement(RENDERSCRIPT, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(renderscript);
  }

  @Override
  public void removeRenderscript() {
    myDslElement.removeProperty(RENDERSCRIPT);
  }

  @Override
  @NotNull
  public SourceDirectoryModel res() {
    SourceDirectoryDslElement res = myDslElement.ensureNamedPropertyElement(RES, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(res);
  }

  @Override
  public void removeRes() {
    myDslElement.removeProperty(RES);
  }

  @Override
  @NotNull
  public SourceDirectoryModel resources() {
    SourceDirectoryDslElement resources = myDslElement.ensureNamedPropertyElement(RESOURCES, SourceDirectoryDslElement.class);
    return new SourceDirectoryModelImpl(resources);
  }

  @Override
  public void removeResources() {
    myDslElement.removeProperty(RESOURCES);
  }
}
