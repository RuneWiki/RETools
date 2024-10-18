/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.deob;

import javax.annotation.Nullable;
import net.runelite.asm.attributes.Annotated;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.Annotation;
import net.runelite.asm.signature.Signature;
import org.jetbrains.annotations.NotNull;

public class DeobAnnotations
{
	public static final Type OBFUSCATED_NAME = new Type("Lnet/runelite/mapping/ObfuscatedName;");
	public static final Type EXPORT = new Type("Lnet/runelite/mapping/Export;");
	public static final Type IMPLEMENTS = new Type("Lnet/runelite/mapping/Implements;");
	public static final Type OBFUSCATED_GETTER = new Type("Lnet/runelite/mapping/ObfuscatedGetter;");
	public static final Type OBFUSCATED_SIGNATURE = new Type("Lnet/runelite/mapping/ObfuscatedSignature;");

	@Nullable
	public static String getObfuscatedName(@NotNull Annotated an)
	{
		return getStringValue(an, OBFUSCATED_NAME);
	}

	@Nullable
	public static String getExportedName(@NotNull Annotated an)
	{
		return getStringValue(an, EXPORT);
	}

	@Nullable
	public static Object get(Annotated an, Type type, String name)
	{
		final var a = an.findAnnotation(type);
		return a == null ? null : a.get(name);
	}

	@Nullable
	public static String getStringValue(Annotated an, Type type)
	{
		final var a = an.findAnnotation(type);
		return a == null ? null : a.getValueString();
	}

	public static String flatten(String className)
	{
		return className.substring(className.lastIndexOf('/') + 1);
	}
}