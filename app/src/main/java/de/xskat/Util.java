package de.xskat;

public class Util {

    /**
     * Returns the index
     * @param input integer array of length 3
     * @return the index for the largest value in the array.
     * @throws IllegalStateException if all values are equal.
     */
    public static int getIndexOfLargestValue(int[] input) {
        if (input.length != 3) {
            throw new IllegalArgumentException("Expected integer array of length 3 but got " + input.length);
        }
        if (input[0] < input[1]) {
            if (input[1] < input[2]) {
                return 2;
            } else {
                return 1;
            }
        } else if (input[1] < input[2]) {
            if (input[2] < input[0]) {
                return 0;
            } else {
                return 2;
            }
        } else if (input[2] < input[0]) {
            if (input[0] < input[1]) {
                return 1;
            } else {
                return 0;
            }
        }
        // will happen when all values are equal, e.g. 40
        throw new IllegalStateException("IllegalState.");
    }
}
