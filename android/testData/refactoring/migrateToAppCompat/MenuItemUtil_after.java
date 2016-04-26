package p1.p2;

import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MenuItemUtil {

  public void menuOperations(Menu menu, int id) {
    // Use a qualifier expression such as menu.findItem(id) rather than an id such as `item`
    // to ensure that we can handle complex expressions.
    ShareActionProvider shareActionProvider =
      (ShareActionProvider) MenuItemCompat.getActionProvider(menu.findItem(id));
    boolean b = MenuItemCompat.collapseActionView(menu.findItem(id));
    MenuItem item = menu.findItem(id);
    View view = MenuItemCompat.getActionView(item);
    boolean x = MenuItemCompat.expandActionView(item);
    MenuItemCompat.setActionProvider(menu.findItem(id), shareActionProvider);
    MenuItemCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_ALWAYS);
    boolean b = MenuItemCompat.isActionViewExpanded(item);
    MenuItemCompat.setOnActionExpandListener(menu.findItem(id), new MenuItemCompat.OnActionExpandListener() {
      @Override
      public void onMenuItemActionCollapse(MenuItem item) {

      }
      @Override
      public void onMenuItemActionExpand(MenuItem item) {

      }
    });
  }
}
