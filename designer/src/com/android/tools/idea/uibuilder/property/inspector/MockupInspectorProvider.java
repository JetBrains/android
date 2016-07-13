package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ATTR_MOCKUP;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

/**
 * Inspector to quickly edit some mockup attributes as the path and the opacity.
 *
 * It will only be displayed if the user has already added a mockup to the selected component.
 */
public class MockupInspectorProvider implements InspectorProvider {

  private static final Set<String> MOCKUP_PROPERTIES = ImmutableSet.of(ATTR_MOCKUP);
  private MockupInspectorComponent myInspector;


  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    return properties.keySet().containsAll(MOCKUP_PROPERTIES)
           && !components.isEmpty()
           && components.get(0).getAttribute(TOOLS_URI, ATTR_MOCKUP) != null;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myInspector == null) {
      myInspector = new MockupInspectorComponent(propertiesManager);
    }
    myInspector.updateProperties(components, properties, propertiesManager);
    return myInspector;
  }

  /**
   * Text font inspector component for setting font family, size, decorations, color.
   */
  private static class MockupInspectorComponent implements InspectorComponent {


    public static final String TITLE = "View Mockup";
    private final NlReferenceEditor myMockupPathEditor;

    private NlProperty myMockupPath;

    public MockupInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      Project project = propertiesManager.getProject();
      myMockupPathEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);

    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {

      myMockupPath = properties.get(ATTR_MOCKUP);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 3;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      inspector.addTitle(TITLE);
      inspector.addComponent(ATTR_MOCKUP, myMockupPath.getTooltipText(), myMockupPathEditor.getComponent());
    }

    @Override
    public void refresh() {
      myMockupPathEditor.setProperty(myMockupPath);
    }

    @Nullable
    @Override
    public NlComponentEditor getEditorForProperty(@NotNull String propertyName) {
      switch (propertyName) {
        case ATTR_MOCKUP:
          return myMockupPathEditor;
        default:
          return null;
      }
    }
  }
}
