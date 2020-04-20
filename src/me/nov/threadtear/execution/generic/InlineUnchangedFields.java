package me.nov.threadtear.execution.generic;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;

import me.nov.threadtear.execution.Clazz;
import me.nov.threadtear.execution.Execution;
import me.nov.threadtear.execution.ExecutionCategory;
import me.nov.threadtear.execution.ExecutionTag;
import me.nov.threadtear.util.asm.Instructions;

public class InlineUnchangedFields extends Execution {

	public InlineUnchangedFields() {
		super(ExecutionCategory.CLEANING, "Inline unchanged fields", "Inline fields that are not set anywhere in the code.<br>Can be useful for ZKM deobfuscation.", ExecutionTag.RUNNABLE,
				ExecutionTag.BETTER_DECOMPILE, ExecutionTag.BETTER_DEOBFUSCATE);
	}

	public int inlines;
	private Map<String, Clazz> classes;
	private List<FieldInsnNode> fieldPuts;

	@Override
	public boolean execute(Map<String, Clazz> classes, boolean verbose) {
		this.classes = classes;
		this.inlines = 0;
		// TODO static initializer should be excluded, we can still calculate the field value
		this.fieldPuts = classes.values().stream().map(c -> c.node.methods).flatMap(List::stream).map(m -> m.instructions.spliterator()).flatMap(insns -> StreamSupport.stream(insns, false))
				.filter(ain -> ain.getOpcode() == PUTFIELD || ain.getOpcode() == PUTSTATIC).map(ain -> (FieldInsnNode) ain).collect(Collectors.toList());

		classes.values().stream().map(c -> c.node).forEach(c -> {
			c.fields.stream().filter(f -> isNotReferenced(c, f)).forEach(f -> inline(c, f));
		});
		logger.info("Inlined " + inlines + " field references!");
		return inlines > 0;
	}

	private boolean isNotReferenced(ClassNode cn, FieldNode f) {
		return fieldPuts.stream().allMatch(fin -> !isReferenceTo(cn, fin, f));
	}

	public void inline(ClassNode cn, FieldNode fn) {
		classes.values().stream().map(c -> c.node).forEach(c -> {
			c.methods.forEach(m -> {
				for (AbstractInsnNode ain : m.instructions) {
					if (ain.getType() == AbstractInsnNode.FIELD_INSN) {
						FieldInsnNode fin = (FieldInsnNode) ain;
						if (isGetReferenceTo(cn, fin, fn)) {
							m.instructions.set(ain, Instructions.makeNullPush(Type.getType(fn.desc)));
							inlines++;
						}
					}
				}
			});
		});
	}

	private boolean isGetReferenceTo(ClassNode cn, FieldInsnNode fin, FieldNode fn) {
		return (fin.getOpcode() == GETSTATIC || fin.getOpcode() == GETFIELD) && isReferenceTo(cn, fin, fn);
	}

	private boolean isReferenceTo(ClassNode cn, FieldInsnNode fin, FieldNode fn) {
		return fin.owner.equals(cn.name) && fin.name.equals(fn.name) && fin.desc.equals(fn.desc);
	}
}
