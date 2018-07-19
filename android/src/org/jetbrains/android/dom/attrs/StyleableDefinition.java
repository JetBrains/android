package org.jetbrains.android.dom.attrs;

import com.android.ide.common.rendering.api.ResourceReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Information about an styleable resource.
 *
 * @author Eugene.Kudelevsky
 */
public interface StyleableDefinition {
  @NotNull
  ResourceReference getResourceReference();

  @NotNull
  String getName();

  @NotNull
  List<AttributeDefinition> getAttributes();
}
