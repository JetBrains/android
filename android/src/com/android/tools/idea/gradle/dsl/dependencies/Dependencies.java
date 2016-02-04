/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.dependencies;

import com.android.tools.idea.gradle.dsl.dependencies.external.ExternalDependency;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.findClosableBlock;
import static com.intellij.psi.util.PsiTreeUtil.getChildrenOfType;

public class Dependencies extends GradleDslElement {
  @Nullable private PsiFile myPsiFile;
  @Nullable private GrClosableBlock myClosureBlock;

  @NotNull private final List<ExternalDependency> myExternal = Lists.newArrayList();
  @NotNull private final List<ModuleDependency> myToModules = Lists.newArrayList();

  @NotNull private final List<ExternalDependency> myExternalToRemove = Lists.newArrayList();

  @NotNull private final Multimap<String, ExternalDependencySpec> myNewExternal = HashMultimap.create();

  public Dependencies(@NotNull GradleDslElement parent) {
    super(parent, null, "dependencies"); // TODO: Pass the correct psiElement when moved to using the new parser API.
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  protected void apply() {
    applyChanges(myExternal);
    applyChanges(myToModules);
    removeExternalDependencies();
    applyNewExternalDependencies();
    myExternalToRemove.clear();
    myNewExternal.clear();
  }

  private static void applyChanges(@NotNull List<? extends Dependency> dependencies) {
    for (Dependency dependency : dependencies) {
      dependency.applyChanges();
    }
  }

  private void removeExternalDependencies() {
    for (ExternalDependency dependency : myExternalToRemove) {
      dependency.removeFromParent();
    }
  }

  private void applyNewExternalDependencies() {
    for (Map.Entry<String, ExternalDependencySpec> entry : myNewExternal.entries()) {
      applyChanges(entry.getKey(), entry.getValue());
    }
  }

  private void applyChanges(@NotNull String configurationName, @NotNull ExternalDependencySpec dependency) {
    assert myPsiFile != null;

    String compactNotation = dependency.compactNotation();

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myPsiFile.getProject());
    if (myClosureBlock == null) {
      // There are no dependency blocks. Add one.
      // We need to add line separators, otherwise reformatting won't work.
      String text = "dependencies {\n" + configurationName + " '" + compactNotation + "'" + "\n}";
      GrExpression expression = factory.createExpressionFromText(text);

      PsiElement added = myPsiFile.add(expression);
      assert added instanceof GrMethodCallExpression;
      parse((GrMethodCallExpression)added);
      CodeStyleManager.getInstance(myPsiFile.getProject()).reformat(myClosureBlock);
      return;
    }

    GrStatement statement = factory.createStatementFromText(configurationName + " '" + compactNotation + "'");
    GrStatement added = myClosureBlock.addStatementBefore(statement, null);
    assert added instanceof GrApplicationStatement;
    List<Dependency> dependencies = Dependency.parse(this, (GrApplicationStatement)added);
    add(dependencies);

    CodeStyleManager.getInstance(myPsiFile.getProject()).reformat(added);
  }

  @NotNull
  public ImmutableList<ExternalDependency> external() {
    return ImmutableList.copyOf(myExternal);
  }

  @NotNull
  public ImmutableList<ModuleDependency> toModules() {
    return ImmutableList.copyOf(myToModules);
  }

  public void add(@NotNull String configurationName, @NotNull ExternalDependencySpec dependency) {
    myNewExternal.put(configurationName, dependency);
    setModified(true);
  }

  public void remove(@NotNull ExternalDependency dependency) {
    boolean removed = myExternal.remove(dependency);
    if (!removed) {
      String msg =
        String.format("Dependency '%1$s' cannot be removed because it does not belong to this model", dependency.compactNotation());
      throw new IllegalArgumentException(msg);
    }
    myExternalToRemove.add(dependency);
    setModified(true);
  }

  public boolean parse(@NotNull GrMethodCallExpression methodCallExpression) {
    myClosureBlock = findClosableBlock(methodCallExpression, "dependencies");
    if (myClosureBlock == null) {
      return false;
    }
    GrMethodCall[] methodCalls = getChildrenOfType(myClosureBlock, GrMethodCall.class);
    if (methodCalls == null) {
      return false;
    }
    for (GrMethodCall methodCall : methodCalls) {
      List<Dependency> dependencies = Dependency.parse(this, methodCall);
      add(dependencies);
    }
    return true;
  }

  private void add(@NotNull List<Dependency> dependencies) {
    for (Dependency dependency : dependencies) {
      add(dependency);
    }
  }

  private void add(@Nullable Dependency dependency) {
    if (dependency instanceof ExternalDependency) {
      myExternal.add((ExternalDependency)dependency);
    }
    else if (dependency instanceof ModuleDependency) {
      myToModules.add((ModuleDependency)dependency);
    }
  }

  @Override
  protected void reset() {
    reset(myExternal);
    reset(myToModules);
    myExternalToRemove.clear();
    myNewExternal.clear();
  }

  private static void reset(@NotNull List<? extends Dependency> dependencies) {
    for (Dependency dependency : dependencies) {
      dependency.resetState();
    }
  }

  @Nullable
  public GrClosableBlock getClosureBlock() {
    return myClosureBlock;
  }

  public void setPsiFile(@Nullable PsiFile psiFile) {
    myPsiFile = psiFile;
  }
}
