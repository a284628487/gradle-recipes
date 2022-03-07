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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import java.io.File
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.TaskAction

abstract class ExamplePlugin: Plugin<Project> {

    override fun apply(project: Project) {
        // attach the BuildTypeExtension to each elements returned by the
        // android buildTypes API.
        val android = project.extensions.getByType(ApplicationExtension::class.java)
        android.buildTypes.forEach {
            (it as ExtensionAware).extensions.add(
                "exampleDsl",
                BuildTypeExtension::class.java)
        }

        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        // hook up task configuration on the variant API.
        androidComponents.onVariants { variant ->
            // get the associated DSL BuildType element from the variant name
            val buildTypeDsl = android.buildTypes.getByName(variant.name)
            println("buildType: ${variant.name}") // debug | release
            // find the extension on that DSL element.
            val buildTypeExtension = (buildTypeDsl as ExtensionAware).extensions.findByName("exampleDsl")
                as BuildTypeExtension
            // create and configure the Task using the extension DSL values.
            project.tasks.register(variant.name + "Example", ExampleTask::class.java) { task ->
                task.parameters.set(buildTypeExtension.invocationParameters ?: "")
            }
        }
    }
}

abstract class CustomData {
    abstract val websiteUrl: Property<String?>?
    abstract val vcsUrl: Property<String?>?
}

abstract class SiteExtension {
    abstract val outputDir: RegularFileProperty?

    @get:Nested
    abstract val customData: CustomData?

    fun customData(action: Action<in CustomData?>) {
        action.execute(customData)
    }
}

abstract class SiteTask: DefaultTask() {

    @TaskAction
    fun doIt() {
        val  siteEx = project.extensions.getByName("siteEx") as SiteExtension
        println("testTask begin")
        val websiteUrl = siteEx.customData?.websiteUrl?.get()
        val vcsUrl = siteEx.customData?.vcsUrl?.get()
        val file = siteEx.outputDir?.get()?.asFile
        println("websiteUrl: ${websiteUrl}")
        println("vcsUrl: ${vcsUrl}")
        println("file: ${file?.toString()}") // projectRoot/app/build/mysite
    }
}

// https://docs.gradle.org/7.0.2/userguide/custom_plugins.html
abstract class SitePlugin: Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create<SiteExtension>("siteEx", SiteExtension::class.java)
        val testTask = project.tasks.register("testTask", SiteTask::class.java) {
            println("testTask config")
        }
        val task = project.tasks.getByName("preBuild")
        task.dependsOn(testTask)
    }
}

