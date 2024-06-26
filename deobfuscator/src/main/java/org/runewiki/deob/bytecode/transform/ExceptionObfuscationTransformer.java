package org.runewiki.deob.bytecode.transform;

/**
 * A [Transformer] responsible for removing [ZKM](http://www.zelix.com/klassmaster/)'s
 * [exception obfuscation](https://www.zelix.com/klassmaster/featuresExceptionObfuscation.html),
 * which inserts exception handlers that catch any type of exception and
 * immediately re-throw them. The exception handlers are inserted in locations
 * where there is no Java source code equivalent, confusing decompilers.
 */
public class ExceptionObfuscationTransformer {
}
