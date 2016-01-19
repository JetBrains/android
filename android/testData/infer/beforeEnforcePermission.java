import android.content.Context;

@SuppressWarnings({"unused", "WeakerAccess"})
public class EnforcePermission {
    private Context mContext;

    public void unconditionalPermission() {
        // This should transfer the permission requirement from impliedPermission to this method
        impliedPermission();
    }

    boolean impliedPermission() {
        // From this we should infer @RequiresPermission(MY_PERMISSION)
        mContext.enforceCallingOrSelfPermission(MY_PERMISSION, "");
        return true;
    }

    public static final String MY_PERMISSION = "mypermission";
}
