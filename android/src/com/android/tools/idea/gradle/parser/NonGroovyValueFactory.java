/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.android.annotations.Nullable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Collections;
import java.util.List;

/**
 * A dummy {@link ValueFactory} for {@link com.android.tools.idea.gradle.parser.BuildFileKey} values that aren't saved to Groovy files.
 */
public class NonGroovyValueFactory extends ValueFactory {
  public static ValueFactory getFactory() {
    return new NonGroovyValueFactory();
  }

  @Override
  protected void setValue(@NotNull GrStatementOwner closure, @NotNull Object value, @Nullable KeyFilter filter) {
  }

  @Nullable
  @Override
  protected List getValues(@NotNull PsiElement statement) {
    return null;
  }

  @NotNull
  @Override
  public List getValues(@NotNull GrStatementOwner closure) {
    return Collections.EMPTY_LIST;
  }

  @Override
  public void setValues(@NotNull GrStatementOwner closure, @NotNull List values, @Nullable KeyFilter filter) {
  }
}
