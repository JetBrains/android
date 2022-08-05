public class InferResourceFromArgument {
    public void inferredParameterFromOutsideCall(boolean dummy, int inferredDimension) {
        // Nothing here
    }

    private void callWhichImpliesParameterType() {
        inferredParameterFromOutsideCall(true, R.dimen.some_dimension);
    }

    public static class R {
        public static class dimen {
            public static final int some_dimension = 1;
        }
    }
}
