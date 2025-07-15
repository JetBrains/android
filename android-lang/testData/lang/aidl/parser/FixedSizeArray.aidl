interface FixedSizeArray {
    void test(
      in float[3] data,
      in float[4][5] multiDimensional,
      in float[(6 * 8)] withExpression
    );
}