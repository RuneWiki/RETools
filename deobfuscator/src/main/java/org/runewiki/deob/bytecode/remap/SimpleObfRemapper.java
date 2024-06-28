package org.runewiki.deob.bytecode.remap;

import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.Map;

public class SimpleObfRemapper extends Remapper {
    private final Map<String, String> mapping;
    public final Map<String, String> reverse = new HashMap<>();

    public SimpleObfRemapper(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    @Override
    public String map(String internalName) {
        var newName = this.mapping.getOrDefault(internalName, internalName);
        this.reverse.put(newName, internalName);
        return newName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        var oldFqn = owner + "." + name + descriptor;
        var newName = this.mapping.getOrDefault(oldFqn, name);
        if (this.mapping.containsKey(owner)) {
            var newFqn = this.map(owner) + "." + newName + this.mapDesc(descriptor);
            this.reverse.put(newFqn, oldFqn);
        }
        return newName;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        var oldFqn = owner + "." + name + descriptor;
        var newName = this.mapping.getOrDefault(oldFqn, name);
        if (this.mapping.containsKey(owner)) {
            var newFqn = this.map(owner) + "." + newName + this.mapDesc(descriptor);
            this.reverse.put(newFqn, oldFqn);
        }
        return newName;
    }
}