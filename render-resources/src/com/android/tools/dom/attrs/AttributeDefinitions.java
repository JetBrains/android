package com.android.tools.dom.attrs;

import com.android.ide.common.rendering.api.ResourceReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Information about attr and styleable resources in an easy to consume form.
 */
public interface AttributeDefinitions {
  @Nullable
  StyleableDefinition getStyleableDefinition(@NotNull ResourceReference styleable);

  /**
   * @deprecated Use {@link #getStyleableDefinition(ResourceReference)}. This method doesn't support namespaces.
   */
  @Deprecated
  @Nullable
  StyleableDefinition getStyleableByName(@NotNull String name);

  @NotNull
  Set<ResourceReference> getAttrs();

  @Nullable
  AttributeDefinition getAttrDefinition(@NotNull ResourceReference attr);

  /**
   * @deprecated Use {@link #getAttrDefinition(ResourceReference)}. This method doesn't support namespaces.
   */
  @Deprecated
  @Nullable
  AttributeDefinition getAttrDefByName(@NotNull String name);

  @Nullable
  String getAttrGroup(@NotNull ResourceReference attr);
}
