import android.support.annotation.RequiresPermission;

@SuppressWarnings({"WeakerAccess", "unused", "SpellCheckingInspection"})
public class ConditionalPermission {
    public boolean conditionalPermission(boolean conditional1, int val) {
        // These calls should all be treated as conditional and should not lead
        // to transferring the permission from the call to this method
        if (conditional1) {
            unconditionalPermission();
        }
        boolean x = conditional1 && unconditionalPermission();
        //noinspection SimplifiableConditionalExpression
        boolean y = conditional1 ? unconditionalPermission() : false;
        switch (val) {
            case 1: {
                break;
            }
            case 2: {
                unconditionalPermission();
                break;
            }
        }

        return x & y;
    }

    public void mutuallyExclusive(boolean conditional1) {
        // Impossible to reach unconditionalPermission() from here
        if (conditional1) {
            unconditionalPermission();
        }

        requiresNothing();

        if (!conditional1) {
            return true;
        }
        unconditionalPermission();
    }

    private void requiresNothing() {
    }


    @RequiresPermission(MY_PERMISSION)
    public boolean unconditionalPermission() {
        return true;
    }

    public static final String MY_PERMISSION = "mypermission";
}
