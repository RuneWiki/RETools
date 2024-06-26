package org.runewiki.deob.bytecode.transform;

/**
 * A [Transformer] that splits multiple assignments to static fields in a
 * single expression in `<clinit>` methods. For example, `a = b = new X()` is
 * translated to `b = new X(); a = b`. This allows [StaticFieldUnscrambler] to
 * move the fields independently.
 */
public class MultipleAssignmentTransformer {
}
