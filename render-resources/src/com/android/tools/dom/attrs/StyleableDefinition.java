package com.android.tools.dom.attrs;

import com.android.ide.common.rendering.api.ResourceReference;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Information about an styleable resource.
 */
public interface StyleableDefinition {
  @NotNull
  ResourceReference getResourceReference();

  @NotNull
  String getName();

  @NotNull
  List<AttributeDefinition> getAttributes();
}
