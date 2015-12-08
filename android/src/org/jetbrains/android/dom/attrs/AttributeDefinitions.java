package org.jetbrains.android.dom.attrs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface AttributeDefinitions {
  @Nullable
  StyleableDefinition getStyleableByName(@NotNull String name);

  @NotNull
  Set<String> getAttributeNames();

  @Nullable
  AttributeDefinition getAttrDefByName(@NotNull String name);

  @Nullable
  String getAttrGroupByName(@NotNull String name);
}
