/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.openapi.util.NullableLazyValue;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Code completion support for file paths within BUILD file labels. */
public class FilePathLookupElement extends BuildLookupElement {

  private final String itemText;
  private final NullableLazyValue<Icon> icon;

  public FilePathLookupElement(
      String fullLabel, String itemText, QuoteType quoteWrapping, NullableLazyValue<Icon> icon) {
    super(fullLabel, quoteWrapping);
    this.itemText = itemText;
    this.icon = icon;
  }

  @Override
  protected String getItemText() {
    return itemText;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return icon.getValue();
  }

  @Override
  protected boolean caretInsideQuotes() {
    // after completing, leave the caret inside the closing quote, so the user can
    // continue typing the path.
    return true;
  }
}
