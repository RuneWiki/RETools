package rs.lostcity.asm;

import org.objectweb.asm.tree.*;

public class MemberRef {
    public final String owner;
    public final String name;
    public final String desc;

    public MemberRef(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    public MemberRef(ClassNode clazz, FieldNode field) {
        this(clazz.name, field.name, field.desc);
    }

    public MemberRef(ClassNode clazz, MethodNode method) {
        this(clazz.name, method.name, method.desc);
    }

    public MemberRef(FieldInsnNode fieldInsn) {
        this(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
    }

    public MemberRef(MethodInsnNode methodInsn) {
        this(methodInsn.owner, methodInsn.name, methodInsn.desc);
    }

    public String toString() {
        return owner + "." + name + desc;
    }
}
