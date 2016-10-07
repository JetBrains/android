import android.content.Context;
import android.support.annotation.RequiresPermission;

@SuppressWarnings({"unused", "WeakerAccess"})
public class EnforcePermission {
    private Context mContext;

    @RequiresPermission(EnforcePermission.MY_PERMISSION)
    public void unconditionalPermission() {
        // This should transfer the permission requirement from impliedPermission to this method
        impliedPermission();
    }

    @RequiresPermission(EnforcePermission.MY_PERMISSION)
    boolean impliedPermission() {
        // From this we should infer @RequiresPermission(MY_PERMISSION)
        mContext.enforceCallingOrSelfPermission(MY_PERMISSION, "");
        return true;
    }

    public static final String MY_PERMISSION = "mypermission";
}
