package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.converters.QuietResourceReferenceConverter;

@Convert(QuietResourceReferenceConverter.class)
public interface FractionElement extends ResourceElement {
}
