package com.android.tools.dom.attrs;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceReference;

import java.util.List;

/**
 * Information about an styleable resource.
 */
public interface StyleableDefinition {
  @NonNull
  ResourceReference getResourceReference();

  @NonNull
  String getName();

  @NonNull
  List<AttributeDefinition> getAttributes();
}
