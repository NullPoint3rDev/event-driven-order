apply(plugin = "io.spring.dependency-management")
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
