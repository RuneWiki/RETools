package org.runewiki.deob.bytecode.transform;

/**
 * A [Transformer] that performs
 * [copy propagation](https://en.wikipedia.org/wiki/Copy_propagation) of
 * assignments of one variable to another.
 *
 * This is primarily for improving the decompilation of `for` loops. Without
 * copy propagation, the initializer in many `for` loops declares a different
 * variable to the one in the increment expression:
 *
 * ```
 * Object[] array = ...
 * int i = 0;
 * for (Object[] array2 = array; i < n; i++) {
 *     // use array2[n]
 * }
 * ```
 *
 * With copy propagation, the variables match:
 *
 * ```
 * Object[] array = ...
 * for (int i = 0; i < n; i++) {
 *     // use array[n]
 * }
 * ```
 */
public class CopyPropagationTransformer {
}
