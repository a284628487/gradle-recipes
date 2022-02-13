/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.internal.impldep.org.objectweb.asm.Opcodes
import org.gradle.internal.impldep.org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.PrintWriter

abstract class ExamplePlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            variant.transformClassesWith(
                ExampleClassVisitorFactory::class.java,
                InstrumentationScope.ALL
            ) {
                it.writeToStdout.set(true)
            }
            variant.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        }
    }

    interface ExampleParams : InstrumentationParameters {
        @get:Input
        val writeToStdout: Property<Boolean>
    }

    class MyClassVisitor(val className: String, val classVisitor: ClassVisitor) :
        ClassVisitor(Opcodes.ASM7, classVisitor) {

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            println("visitMethod: ${className}.${name}, ${descriptor}")
            var md = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (name == "<clinit>") {
                md = StaticInitMethodVisitor(className, md)
            } else if (name == "hello") {
                md = AddMethodLogVisitor(className, name, md)
            }
            return md
        }
    }

    class AddMethodLogVisitor(val className: String, val methodName: String, val methodVisitor: MethodVisitor) :
        MethodVisitor(Opcodes.ASM7, methodVisitor) {

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            super.visitMaxs(maxStack + 5, maxLocals)
        }

        // 目标方法调用之前调用
        override fun visitCode() {
            println("method visit end ${methodName}")
            methodVisitor.visitLdcInsn(className)
            methodVisitor.visitLdcInsn("${methodName} begin: ")
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false)
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
            methodVisitor.visitMethodInsn(INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "stringPlus", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", false)
            methodVisitor.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e", "(Ljava/lang/String;Ljava/lang/String;)I", false)
        }

        override fun visitInsn(opcode: Int) {
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)) {
                println("method visit end ${methodName}")
                methodVisitor.visitLdcInsn(className)
                methodVisitor.visitLdcInsn("${methodName} end: ")
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "stringPlus", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", false);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e", "(Ljava/lang/String;Ljava/lang/String;)I", false);
            }
            super.visitInsn(opcode)
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            descriptor: String?,
            isInterface: Boolean
        ) {
            println("visitMethodInsn")
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

    }

    class StaticInitMethodVisitor(val className: String, val methodVisitor: MethodVisitor) :
        MethodVisitor(Opcodes.ASM7, methodVisitor) {

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            super.visitMaxs(maxStack + 4, maxLocals)
        }

        override fun visitInsn(opcode: Int) {
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)) {
                println("StaticInitMethodVisitor.visitInsn")
                methodVisitor.visitLdcInsn(className)
                methodVisitor.visitLdcInsn("<clinit>")
                methodVisitor.visitMethodInsn(
                    INVOKESTATIC,
                    "android/util/Log",
                    "e",
                    "(Ljava/lang/String;Ljava/lang/String;)I",
                    false
                )
            }
            super.visitInsn(opcode)
        }
    }

    abstract class ExampleClassVisitorFactory : AsmClassVisitorFactory<ExampleParams> {

        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return if (parameters.get().writeToStdout.get()) {
                MyClassVisitor(classContext.currentClassData.className, nextClassVisitor)
                // TraceClassVisitor(nextClassVisitor, PrintWriter(System.out))
            } else {
                TraceClassVisitor(nextClassVisitor, PrintWriter(File("trace_out")))
            }
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            return with(classData.className) {
                startsWith("com.example") && !(contains("R\$") ||
                        endsWith("BuildConfig") ||
                        endsWith("R"))
            }
        }
    }
}