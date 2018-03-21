package p1.p2;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ShareActionProvider;

public class MenuItemUtil {

  public void menuOperations(Menu menu, int id) {
    // Use a qualifier expression such as menu.findItem(id) rather than an id such as `item`
    // to ensure that we can handle complex expressions.
    ShareActionProvider shareActionProvider =
      (ShareActionProvider) menu.findItem(id).getActionProvider();
    boolean b = menu.findItem(id).collapseActionView();
    MenuItem item = menu.findItem(id);
    View view = item.getActionView();
    boolean x = item.expandActionView();
    menu.findItem(id).setActionProvider(shareActionProvider);
    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    boolean b = item.isActionViewExpanded();
    menu.findItem(id).setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public void onMenuItemActionCollapse(MenuItem item) {

      }
      @Override
      public void onMenuItemActionExpand(MenuItem item) {

      }
    });
  }
}
