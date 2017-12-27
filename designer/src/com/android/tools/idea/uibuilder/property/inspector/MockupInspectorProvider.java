package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.mockup.editor.MockUpFileChooser;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
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
    //noinspection ConstantConditions
    return StudioFlags.NELE_MOCKUP_EDITOR.get() && properties.keySet().containsAll(MOCKUP_PROPERTIES)
           && !components.isEmpty()
           && components.get(0).getAttribute(TOOLS_URI, ATTR_MOCKUP) != null;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myInspector == null) {
      myInspector = new MockupInspectorComponent(propertiesManager.getProject());
    }
    myInspector.updateProperties(components, properties, propertiesManager);
    return myInspector;
  }

  @Override
  public void resetCache() {
    myInspector = null;
  }

  /**
   * Text font inspector component for setting font family, size, decorations, color.
   */
  private static class MockupInspectorComponent implements InspectorComponent {

    public static final String TITLE = "View Mockup";
    private final NlReferenceEditor myOpacityEditor;
    private NlProperty myMockupPath;
    private NlProperty myOpacityProperty;
    private TextFieldWithBrowseButton myFileChooser;

    public MockupInspectorComponent(@NotNull Project project) {
      myOpacityEditor = NlReferenceEditor.createForInspector(project, DEFAULT_LISTENER);
      myFileChooser = createFileChooserButton();
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myMockupPath = properties.get(ATTR_MOCKUP);
      myOpacityProperty = properties.get(ATTR_MOCKUP_OPACITY);
    }


    @Override
    public int getMaxNumberOfRows() {
      return 3;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      inspector.addTitle(TITLE);
      inspector.addComponent(ATTR_MOCKUP, myMockupPath.getTooltipText(), myFileChooser);
      inspector.addComponent(ATTR_MOCKUP_OPACITY, null, myOpacityEditor.getComponent());
    }

    private TextFieldWithBrowseButton createFileChooserButton() {
      TextFieldWithBrowseButton button;
      button = new TextFieldWithBrowseButton();
      button.setEditable(false);
      button.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
      button.addActionListener(event -> {
        NlComponent component = getSelectionComponent();
        if (component == null) {
          return;
        }
        MockUpFileChooser.INSTANCE.chooseMockUpFile(component, (path) -> {
          button.setText(path);
          myMockupPath.setValue(path);
        });
      });
      return button;
    }

    @Nullable
    private NlComponent getSelectionComponent() {
      if (myMockupPath == null || myMockupPath.getComponents().isEmpty()) {
        return null;
      }
      return myMockupPath.getComponents().get(0);
    }

    @Override
    public void refresh() {
      if (myFileChooser != null && !myFileChooser.getText().equals(myMockupPath.getValue())) {
        myFileChooser.setText(myMockupPath.getValue());
      }
      myOpacityEditor.setProperty(myOpacityProperty);
    }

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return ImmutableList.of(myOpacityEditor);
    }
  }
}
