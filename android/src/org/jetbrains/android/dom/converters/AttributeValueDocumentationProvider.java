package org.jetbrains.android.dom.converters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AttributeValueDocumentationProvider {
  @Nullable
  String getDocumentation(@NotNull String value);
}
