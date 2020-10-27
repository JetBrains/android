package org.jetbrains.android.dom.converters;

import com.android.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface AttributeValueDocumentationProvider {
  @Nullable
  String getDocumentation(@NotNull String value);
}
