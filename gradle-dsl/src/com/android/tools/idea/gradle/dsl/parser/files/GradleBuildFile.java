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

import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.SOURCE_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleBuildFile extends GradleScriptFile {
  @Nullable private GradlePropertiesFile myPropertiesFile;
  @Nullable private GradleBuildFile myParentModuleBuildFile;
  @NotNull private final Set<GradleBuildFile> myChildModuleBuildFiles = Sets.newHashSet();

  public GradleBuildFile(@NotNull VirtualFile file,
                         @NotNull Project project,
                         @NotNull String moduleName,
                         @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(element.getFullName())) {
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this, this);
        super.addParsedElement(applyDslElement);
      }
      applyDslElement.addParsedElement(element);
      return;
    }
    super.addParsedElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    // TODO(xof): doing explicit effect assignment here is ugly, but a GradleDslFile is not a GradleBlockDslElement, but a
    //  GradlePropertiesDslElement.  Maybe we should rethink where in the hierarchy the effect logic should go?
    ModelEffectDescription effect = null;

    if (element instanceof GradleDslLiteral) {
      // This is only in setParsedElement, not addParsedElement, because these are only supported as properties, not as setter methods.
      //
      // TODO(xof): these are only supported in Groovy.  Or maybe the criterion is something else?
      if (element.getName().equals("sourceCompatibility")) {
        effect = new ModelEffectDescription(new ModelPropertyDescription(SOURCE_COMPATIBILITY), SET);
      }
      else if (element.getName().equals("targetCompatibility")) {
        effect = new ModelEffectDescription(new ModelPropertyDescription(TARGET_COMPATIBILITY), SET);
      }
      else {
        super.setParsedElement(element);
        return;
      }
      JavaDslElement javaDslElement = getPropertyElement(JAVA);
      if (javaDslElement == null) {
        javaDslElement = new JavaDslElement(this, GradleNameElement.create(JAVA.name));
        setParsedElement(javaDslElement);
      }
      element.setModelEffect(effect);
      javaDslElement.setParsedElement(element);
      return;
    }

    super.setParsedElement(element);
  }

  @Override
  public void addAppliedProperty(GradleDslElement element) {
    // TODO(xof): unify with setParsedElement above.
    ModelEffectDescription effect;

    if (element instanceof GradleDslLiteral) {
      if (element.getName().equals("sourceCompatibility")) {
        effect = new ModelEffectDescription(new ModelPropertyDescription(SOURCE_COMPATIBILITY), SET);
      }
      else if (element.getName().equals("targetCompatibility")) {
        effect = new ModelEffectDescription(new ModelPropertyDescription(TARGET_COMPATIBILITY), SET);
      }
      else {
        super.addAppliedProperty(element);
        return;
      }
      JavaDslElement javaDslElement = getPropertyElement(JAVA);
      if (javaDslElement == null) {
        javaDslElement = new JavaDslElement(this, GradleNameElement.create(JAVA.name));
        addAppliedProperty(javaDslElement);
      }
      element.setModelEffect(effect);
      javaDslElement.addAppliedProperty(element);
      return;
    }

    super.addAppliedProperty(element);
  }

  @NotNull
  @Override
  public ImmutableMap<String, PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    @NotNull GradleDslNameConverter.Kind kind
  ) {
    return GradleBlockModelMap.getElementMap(GradleBuildFile.class, kind);
  }

  /**
   * Sets the properties dsl file of this file.
   *
   * <p>build.gradle and gradle.properties files belongs to the same module are considered as sibling files.
   */
  public void setPropertiesFile(@NotNull GradlePropertiesFile propertiesFile) {
    myPropertiesFile = propertiesFile;
  }

  /**
   * Returns the properties dsl file of this file.
   *
   * <p>build.gradle and gradle.properties files belongs to the same module are considered as sibling files.
   */
  @Nullable
  public GradlePropertiesFile getPropertiesFile() {
    return myPropertiesFile;
  }

  public void setParentModuleBuildFile(@NotNull GradleBuildFile parentModuleBuildFile) {
    myParentModuleBuildFile = parentModuleBuildFile;
    myParentModuleBuildFile.myChildModuleBuildFiles.add(this);
  }

  @Nullable
  public GradleBuildFile getParentModuleBuildFile() {
    return myParentModuleBuildFile;
  }

  @NotNull
  public Collection<GradleBuildFile> getChildModuleBuildFiles() {
    return myChildModuleBuildFiles;
  }
}
