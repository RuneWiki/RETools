package org.runewiki.deob.bytecode.remap;

import org.objectweb.asm.commons.Remapper;

import java.util.Map;

public class SimpleObfRemapper extends Remapper {
    private final Map<String, String> mapping;

    public SimpleObfRemapper(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return this.mapping.getOrDefault(owner + "." + name + descriptor, name);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return this.mapping.getOrDefault(owner + "." + name + descriptor, name);
    }

    @Override
    public String map(String internalName) {
        return this.mapping.getOrDefault(internalName, internalName);
    }
}
