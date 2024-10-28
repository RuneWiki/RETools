package zwyz.deob;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

public class ZwyzDeobStep1 {
    public static boolean RUNELITE = false;
    public static boolean UNRELIABLE_CLASS_ORDER = false; // rs3
    public static boolean COMPLEX_PARAMETER_CHECKS = false; // older rs3 revs
    public static boolean SHOW_LINE_NUMBERS = false;
    public static boolean TRACK_MOVED = false;

    // todo: profile driven list
    public static final List<String> EXTERNAL_LIBRARIES = List.of(
        "com/jagex/oldscape/pub",
        "org/bouncycastle/",
        "org/json/"
    );

    public static void run(Path INPUT, Path OUTPUT, boolean runelite, boolean unreliableClassOrder, boolean complexParameterChecks, boolean showLineNumbers, boolean trackMoved) throws IOException {
        RUNELITE = runelite;
        UNRELIABLE_CLASS_ORDER = unreliableClassOrder;
        COMPLEX_PARAMETER_CHECKS = complexParameterChecks;
        SHOW_LINE_NUMBERS = showLineNumbers;
        TRACK_MOVED = trackMoved;

        var classes = JarUtil.readClasses(INPUT);
        if (RUNELITE) AnnotationRemover.run(classes); // done
        if (RUNELITE) DeleteInvokeDynamic.run(classes); // done
        AnnotateObfuscatedNames.annotate(classes); // done
        UniqueRenamer.remap(classes); // sort of replaced
        if (RUNELITE) StaticInstanceMethods.run(classes); // done

        var calledMethods = CalledMethods.run(classes);  // done
        var obfuscatedMethods = new HashSet<String>();
        var unobfuscatedMethods = new HashSet<String>();
        ErrorHandlers.run(classes, calledMethods, obfuscatedMethods, unobfuscatedMethods);  // done
        ParameterChecks.run(classes, obfuscatedMethods, unobfuscatedMethods);  // done
        if (RUNELITE) calledMethods = CalledMethods.run(classes); // param checks reference other methods on rl  // done
        GotoDeobfuscator.run(classes); // Undo goto obfuscation, so that matching statics can work properly  // done

        var staticsClass = new ClassNode();
        staticsClass.version = RUNELITE ? Opcodes.V1_8 : Opcodes.V1_6;
        staticsClass.access = Opcodes.ACC_PUBLIC;
        staticsClass.name = "statics";
        staticsClass.superName = "java/lang/Object";
        staticsClass.sourceFile = "statics.java";
        classes.add(staticsClass);

        var movedAnnotationClass = new ClassNode();
        movedAnnotationClass.version = Opcodes.V1_6;
        movedAnnotationClass.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION;
        movedAnnotationClass.name = "Moved";
        movedAnnotationClass.superName = "java/lang/Object";
        movedAnnotationClass.sourceFile = "Moved.java";

        if (TRACK_MOVED) {
            classes.add(movedAnnotationClass);
        }

        StaticMethods.run(classes, calledMethods, obfuscatedMethods, staticsClass, movedAnnotationClass);  // done
        SortMethods.run(classes); // done
        StaticFields.run(classes, staticsClass, movedAnnotationClass); // done
        SortFieldsName.run(classes); // done
        VariableSplitter.run(classes); // done
        ExpressionSorter.run(classes); // done
        if (SHOW_LINE_NUMBERS) LineNumberAdder.run(classes); // no need

        JarUtil.writeClasses(OUTPUT, classes);
    }
}
