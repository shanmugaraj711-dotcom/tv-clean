plugins { id("tvclean.jvm") }

dependencies {
    api(project(":core:model"))
    api(project(":core:ops"))
    testImplementation(project(":testing:fixtures"))
}
