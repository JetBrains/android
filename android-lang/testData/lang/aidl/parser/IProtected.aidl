package android.aidl.tests.permission;

interface IProtected {
    @EnforcePermission("READ_PHONE_STATE") void PermissionProtected();

    @EnforcePermission(allOf={"INTERNET", "VIBRATE"}) void MultiplePermissionsAll();

    @EnforcePermission(anyOf={"INTERNET", "VIBRATE"}) void MultiplePermissionsAny();
}
