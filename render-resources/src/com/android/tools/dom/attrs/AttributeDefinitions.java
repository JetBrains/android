package com.android.tools.dom.attrs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceReference;

import java.util.Set;

/**
 * Information about attr and styleable resources in an easy to consume form.
 */
public interface AttributeDefinitions {
  @Nullable
  StyleableDefinition getStyleableDefinition(@NonNull ResourceReference styleable);

  /**
   * @deprecated Use {@link #getStyleableDefinition(ResourceReference)}. This method doesn't support namespaces.
   */
  @Deprecated
  @Nullable
  StyleableDefinition getStyleableByName(@NonNull String name);

  @NonNull
  Set<ResourceReference> getAttrs();

  @Nullable
  AttributeDefinition getAttrDefinition(@NonNull ResourceReference attr);

  /**
   * @deprecated Use {@link #getAttrDefinition(ResourceReference)}. This method doesn't support namespaces.
   */
  @Deprecated
  @Nullable
  AttributeDefinition getAttrDefByName(@NonNull String name);

  @Nullable
  String getAttrGroup(@NonNull ResourceReference attr);
}
