import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.tree.TreePanel
import com.intellij.icons.AllIcons

class TreePanelDefinition : ToolWindowDefinition<DesignSurface>(
  "Component Tree", AllIcons.Toolwindows.ToolWindowStructure, "TREE", Side.LEFT, Split.BOTTOM, AutoHide.DOCKED,
  { TreePanel() }
)