/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.util.Printer
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceFieldVisitor
import org.jetbrains.org.objectweb.asm.util.TraceMethodVisitor
import java.io.File

private val LINE_SEPARATOR = System.getProperty("line.separator")

abstract class AbstractAsmLikeInstructionListingTest : CodegenTestCase() {
    private companion object {
        const val CURIOUS_ABOUT_DIRECTIVE = "// CURIOUS_ABOUT "
        const val LOCAL_VARIABLE_TABLE_DIRECTIVE = "// LOCAL_VARIABLE_TABLE"
        const val RENDER_ANNOTATIONS_DIRECTIVE = "// RENDER_ANNOTATIONS"

        val IGNORED_CLASS_VISIBLE_ANNOTATIONS = setOf(
            "Lkotlin/Metadata;",
            "Lkotlin/annotation/Target;",
            "Lkotlin/annotation/Retention;",
            "Ljava/lang/annotation/Retention;",
            "Ljava/lang/annotation/Target;"
        )
    }

    override fun doMultiFileTest(wholeFile: File, files: List<TestFile>) {
        val txtFile = File(wholeFile.parentFile, getExpectedTextFileName(wholeFile))
        compile(files)

        val classes = classFileFactory
                .getClassFiles()
                .sortedBy { it.relativePath }
                .map { file -> ClassNode().also { ClassReader(file.asByteArray()).accept(it, ClassReader.EXPAND_FRAMES) } }

        val testFileLines = wholeFile.readLines()

        val printBytecodeForTheseMethods = testFileLines
                .filter { it.startsWith(CURIOUS_ABOUT_DIRECTIVE) }
                .map { it.substring(CURIOUS_ABOUT_DIRECTIVE.length) }
                .flatMap { it.split(',').map { it.trim() } }

        val showLocalVariables = testFileLines.any { it.trim() == LOCAL_VARIABLE_TABLE_DIRECTIVE }
        val renderAnnotations = testFileLines.any { it.trim() == RENDER_ANNOTATIONS_DIRECTIVE }

        KotlinTestUtils.assertEqualsToFile(txtFile, classes.joinToString(LINE_SEPARATOR.repeat(2)) {
            renderClassNode(it, printBytecodeForTheseMethods, showLocalVariables, renderAnnotations)
        })
    }

    protected open fun getExpectedTextFileName(wholeFile: File): String = wholeFile.nameWithoutExtension + ".txt"

    private fun renderClassNode(
        clazz: ClassNode,
        showBytecodeForTheseMethods: List<String>,
        showLocalVariables: Boolean,
        renderAnnotations: Boolean
    ): String {
        val fields = (clazz.fields ?: emptyList()).sortedBy { it.name }
        val methods = (clazz.methods ?: emptyList()).sortedBy { it.name }

        val superTypes = (listOf(clazz.superName) + clazz.interfaces).filterNotNull()

        return buildString {
            if (renderAnnotations) {
                clazz.signature?.let {
                    append(it).append("\n")
                }
            }
            renderVisibilityModifiers(clazz.access)
            renderModalityModifiers(clazz.access)
            append(if ((clazz.access and ACC_INTERFACE) != 0) "interface " else "class ")
            append(clazz.name)

            if (superTypes.isNotEmpty()) {
                append(" : " + superTypes.joinToString())
            }

            appendLine(" {")

            if (renderAnnotations) {
                val textifier = Textifier()
                val visitor = TraceMethodVisitor(textifier)

                clazz.visibleAnnotations?.forEach {
                    if (it.desc !in IGNORED_CLASS_VISIBLE_ANNOTATIONS) {
                        it.accept(visitor.visitAnnotation(it.desc, true))
                    }
                }
                clazz.invisibleAnnotations?.forEach {
                    it.accept(visitor.visitAnnotation(it.desc, false))
                }

                clazz.visibleTypeAnnotations?.forEach {
                    it.accept(visitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
                }
                clazz.invisibleTypeAnnotations?.forEach {
                    it.accept(visitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
                }

                textifier.getText().takeIf { it.isNotEmpty() }?.let {
                    appendLine(textifier.getText().joinToString("").trimEnd())
                    appendLine("")
                }
            }

            fields.joinTo(this, LINE_SEPARATOR.repeat(2)) { renderField(it, renderAnnotations).withMargin() }

            if (fields.isNotEmpty()) {
                appendLine().appendLine()
            }

            methods.joinTo(this, LINE_SEPARATOR.repeat(2)) {
                val showBytecode = showBytecodeForTheseMethods.contains(it.name)
                renderMethod(it, showBytecode, showLocalVariables, renderAnnotations).withMargin()
            }

            appendLine().append("}")
        }
    }

    private fun renderField(field: FieldNode, renderAnnotations: Boolean) = buildString {
        if (renderAnnotations) {
            field.signature?.let {
                append(it).append("\n")
            }
        }
        renderVisibilityModifiers(field.access)
        renderModalityModifiers(field.access)
        append(Type.getType(field.desc).className).append(' ')
        append(field.name)

        if (renderAnnotations) {
            val textifier = Textifier()
            val visitor = TraceFieldVisitor(textifier)

            field.visibleAnnotations?.forEach {
                it.accept(visitor.visitAnnotation(it.desc, true))
            }
            field.invisibleAnnotations?.forEach {
                it.accept(visitor.visitAnnotation(it.desc, false))
            }

            field.visibleTypeAnnotations?.forEach {
                it.accept(visitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
            }
            field.invisibleTypeAnnotations?.forEach {
                it.accept(visitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
            }
            textifier.getText().takeIf { it.isNotEmpty() }?.let {
                append("\n${textifier.getText().joinToString("").trimEnd()}")
            }
        }
    }

    private fun renderMethod(
        method: MethodNode,
        showBytecode: Boolean,
        showLocalVariables: Boolean,
        renderAnnotations: Boolean
    ) = buildString {
        if (renderAnnotations) {
            method.signature?.let {
                append(it).append("\n")
            }
        }

        renderVisibilityModifiers(method.access)
        renderModalityModifiers(method.access)
        val (returnType, parameterTypes) = with(Type.getMethodType(method.desc)) { returnType to argumentTypes }
        append(returnType.className).append(' ')
        append(method.name)

        parameterTypes.mapIndexed { index, type ->
            val name = getParameterName(index, method)
            "${type.className} $name"
        }.joinTo(this, prefix = "(", postfix = ")")

        if (renderAnnotations) {
            val textifier = Textifier()
            val visitor = TraceMethodVisitor(textifier)

            method.visibleAnnotations?.forEach {
                it.accept(visitor.visitAnnotation(it.desc, true))
            }
            method.invisibleAnnotations?.forEach {
                it.accept(visitor.visitAnnotation(it.desc, false))
            }

            method.visibleTypeAnnotations?.forEach {
                it.accept(visitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, true))
            }
            method.invisibleTypeAnnotations?.forEach {
                it.accept(visitor.visitTypeAnnotation(it.typeRef, it.typePath, it.desc, false))
            }

            method.visibleParameterAnnotations?.forEachIndexed { index, parameterAnnotations: MutableList<AnnotationNode>? ->
                parameterAnnotations?.forEach {
                    it.accept(
                        visitor.visitParameterAnnotation(
                            index,
                            it.desc,
                            true
                        )
                    )
                }
            }
            method.invisibleParameterAnnotations?.forEachIndexed { index, parameterAnnotations: MutableList<AnnotationNode>? ->
                parameterAnnotations?.forEach {
                    it.accept(
                        visitor.visitParameterAnnotation(
                            index,
                            it.desc,
                            false
                        )
                    )
                }
            }
            textifier.getText().takeIf { it.isNotEmpty() }?.let {
                append("\n${textifier.getText().joinToString("").trimEnd()}")
            }
        }

        val actualShowBytecode = showBytecode && (method.access and ACC_ABSTRACT) == 0
        val actualShowLocalVariables = showLocalVariables && method.localVariables?.takeIf { it.isNotEmpty() } != null

        if (actualShowBytecode || actualShowLocalVariables) {
            appendLine(" {")

            if (actualShowLocalVariables) {
                val localVariableTable = buildLocalVariableTable(method)
                if (localVariableTable.isNotEmpty()) {
                    append(localVariableTable.withMargin())
                }
            }

            if (actualShowBytecode) {
                if (actualShowLocalVariables) {
                    appendLine().appendLine()
                }
                append(renderBytecodeInstructions(method.instructions).trimEnd().withMargin())
            }

            appendLine().append("}")

            method.visibleTypeAnnotations
        }
    }

    private fun getParameterName(index: Int, method: MethodNode): String {
        val localVariableIndexOffset = when {
            (method.access and ACC_STATIC) != 0 -> 0
            method.isJvmOverloadsGenerated() -> 0
            else -> 1
        }

        val actualIndex = index + localVariableIndexOffset
        val localVariables = method.localVariables
        return localVariables?.firstOrNull {
            it.index == actualIndex
        }?.name ?: "p$index"
    }

    private fun buildLocalVariableTable(method: MethodNode): String {
        val localVariables = method.localVariables?.takeIf { it.isNotEmpty() } ?: return ""
        return buildString {
            append("Local variables:")
            for (variable in localVariables) {
                appendLine().append((variable.index.toString() + " " + variable.name + ": " + variable.desc).withMargin())
            }
        }
    }

    private fun renderBytecodeInstructions(instructions: InsnList) = buildString {
        val labelMappings = LabelMappings()

        var currentInsn = instructions.first
        while (currentInsn != null) {
            renderInstruction(currentInsn, labelMappings)
            currentInsn = currentInsn.next
        }
    }

    private fun StringBuilder.renderInstruction(node: AbstractInsnNode, labelMappings: LabelMappings) {
        if (node is LabelNode) {
            appendLine("LABEL (L" + labelMappings[node.label] + ")")
            return
        }

        if (node is LineNumberNode) {
            appendLine("LINENUMBER (" + node.line + ")")
            return
        }

        if (node is FrameNode) return

        append("  ").append(Printer.OPCODES[node.opcode] ?: error("Invalid opcode ${node.opcode}"))

        when (node) {
            is FieldInsnNode -> append(" (" + node.owner + ", " + node.name + ", " + node.desc + ")")
            is JumpInsnNode -> append(" (L" + labelMappings[node.label.label] + ")")
            is IntInsnNode -> append(" (" + node.operand + ")")
            is MethodInsnNode -> append(" (" + node.owner + ", "+ node.name + ", " + node.desc + ")")
            is VarInsnNode -> append(" (" + node.`var` + ")")
            is LdcInsnNode -> append(" (" + node.cst + ")")
        }

        appendLine()
    }

    private fun String.withMargin(margin: String = "    "): String {
        return lineSequence().map { margin + it }.joinToString(LINE_SEPARATOR)
    }

    private fun StringBuilder.renderVisibilityModifiers(access: Int) {
        if ((access and ACC_PUBLIC) != 0) append("public ")
        if ((access and ACC_PRIVATE) != 0) append("private ")
        if ((access and ACC_PROTECTED) != 0) append("protected ")
    }

    private fun StringBuilder.renderModalityModifiers(access: Int) {
        if ((access and ACC_FINAL) != 0) append("final ")
        if ((access and ACC_ABSTRACT) != 0) append("abstract ")
        if ((access and ACC_STATIC) != 0) append("static ")
    }

    private class LabelMappings {
        private var mappings = hashMapOf<Int, Int>()
        private var currentIndex = 0

        operator fun get(label: Label): Int {
            val hashCode = System.identityHashCode(label)
            return mappings.getOrPut(hashCode) { currentIndex++ }
        }
    }
}

private fun MethodNode.isJvmOverloadsGenerated(): Boolean {
    fun AnnotationNode.isJvmOverloadsGenerated() =
        this.desc == DefaultParameterValueSubstitutor.ANNOTATION_TYPE_DESCRIPTOR_FOR_JVM_OVERLOADS_GENERATED_METHODS

    return (visibleAnnotations?.any { it.isJvmOverloadsGenerated() } ?: false)
            || (invisibleAnnotations?.any { it.isJvmOverloadsGenerated() } ?: false)
}