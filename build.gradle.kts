plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
    id("io.github.sgtsilvio.gradle.proguard") version "0.7.0"
}

group = "com.lhstack"
version = "v1.0.2"


repositories {
    mavenLocal()
    maven("https://maven.aliyun.com/repository/public/")
    mavenCentral()
}

intellij {
    version.set("2022.3")
    type.set("IC") // Target IDE Platform
    plugins.set(listOf("com.intellij.java", "org.jetbrains.plugins.yaml", "org.intellij.groovy"))
}
dependencies {
    implementation(files("C:/Users/lhstack/.jtools/sdk/sdk.jar"))
    testImplementation(kotlin("test"))
}

val proguardJar by tasks.registering(proguard.taskClass) {
//    addInput {
//        classpath.from(tasks.shadowJar)
//    }
    addInput {
        classpath.from(base.libsDirectory.file("${project.name}-${project.version}.jar"))
    }
    addOutput {
        archiveFile.set(base.libsDirectory.file("${project.name}-${project.version}-proguarded.jar"))
    }
    jdkModules.add("java.base")
    mappingFile.set(base.libsDirectory.file("${project.name}-${project.version}-mapping.txt"))

    rules.addAll(
        "-target 17",
        "-dontoptimize",
        "-dontshrink",
        "-useuniqueclassmembernames",
        "-dontwarn !com.lhstack.**",
        "-flattenpackagehierarchy",
        "-libraryjars C:\\Users\\lhstack\\.m2\\repository\\org\\jetbrains\\kotlin\\kotlin-stdlib\\1.9.22\\kotlin-stdlib-1.9.22.jar",
        "-libraryjars F:\\Repo\\Gradle\\caches\\modules-2\\files-2.1\\com.jetbrains.intellij.idea\\ideaIC\\2022.3\\4d343cadac04a0a31d70f6f96facfaa7f949df01\\ideaIC-2022.3\\lib\\util.jar",
        "-libraryjars F:\\Repo\\Gradle\\caches\\modules-2\\files-2.1\\com.jetbrains.intellij.idea\\ideaIC\\2022.3\\4d343cadac04a0a31d70f6f96facfaa7f949df01\\ideaIC-2022.3\\lib\\app.jar",
        "-libraryjars D:\\Program Files\\java\\17/jmods/java.base.jmod(!.jar;!module-info.class)",
        "-libraryjars D:\\Program Files\\java\\17/jmods/java.desktop.jmod(!.jar;!module-info.class)",
        "-keepattributes Signature,InnerClasses,*Annotation*",
        "-keep class com.lhstack.state.CallThisMethodState\$State** { *; }",
        "-keep class com.lhstack.PluginImpl { *; }",
        "-keep class com.lhstack.view.TabObj { *; }",
        "-keep class com.lhstack.view.TabObjData { *; }",
        "-keep class com.lhstack.view.PsiParameterItem { *; }",
        "-keep class com.lhstack.view.PsiReturnItem { *; }",
        "-keep class com.lhstack.extension.ExtensionKt { *; }",
        "-keep class com.lhstack.components.MultiLanguageTextField { *; }",
        "-keepclassmember class * extends com.intellij.openapi.progress.Task\$Backgroundable{" +
                "<fields>;\n" +
                "<init>(...);" +
                "public protected <methods>;" +
                "}",
        //不需要混淆类名,但是需要混淆里面的函数
//        "-keepnames class com.lhstack.tools.plugins.PluginManager",
        """
            -keepclassmember class com.lhstack.state.CallThisMethodState** {
                public *;
                protected *;
                 <fields>;
            }
            -keepclassmember class com.lhstack.view.CallThisMethodView** {
                public *;
                protected *;
                 <fields>;
            }
            
            -keepclassmember class com.lhstack.extension.StarterJavaProgramPatcher** {
                public *;
                protected *;
                 <fields>;
            }
            
            -keep class com.lhstack.api.Api {
                *;
            }
            
            -keepclassmember class ** {
                 <fields>;
            }
            
            -keepclassmember class * implements com.intellij.execution.ExecutionListener{
                <fields>;
                <init>(...);
                public protected <methods>;
            }
            
            -keepclassmember class * implements com.intellij.openapi.actionSystem.AnAction{
                <fields>;
                <init>(...);
                public protected <methods>;
            }
            
            -keep interface kotlin.jvm.functions.Function*
            
            -keep class kotlin.jvm.functions.Function*
            
        """.trimIndent(),
        "-ignorewarnings"
    )
}


tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    withType<JavaExec> {
        jvmArgs("-Dfile.encoding=UTF-8")
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=all")
    }

}
kotlin {
    jvmToolchain(17)
}