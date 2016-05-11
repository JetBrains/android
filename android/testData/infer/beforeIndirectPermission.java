import android.content.Context;

@SuppressWarnings({"unused", "SpellCheckingInspection", "WeakerAccess"})
public class IndirectPermission {
    private Context mContext;

    public void checkSomething() {
        myEnforcePermission(42, MY_PERMISSION);
    }

    private void myEnforcePermission(int dummy, String perm) {
        // How do I record this as a "requires permission with name=$2" ?
        mContext.enforceCallingOrSelfPermission(perm, perm);
    }

    public static final String MY_PERMISSION = "mypermission";
}
