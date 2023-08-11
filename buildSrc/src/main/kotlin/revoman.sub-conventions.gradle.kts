/*******************************************************************************
 * Copyright (c) 2023, Salesforce, Inc.
 *  All rights reserved.
 *  SPDX-License-Identifier: BSD-3-Clause
 *  For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 ******************************************************************************/
plugins {
  java
  `maven-publish`
  signing
  id("org.jetbrains.kotlinx.kover")
}

repositories { mavenCentral() }

java {
  withSourcesJar()
  toolchain { languageVersion.set(JavaLanguageVersion.of(11)) }
}

tasks {
  withType<Jar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
  withType<PublishToMavenRepository>().configureEach {
    doLast {
      logger.lifecycle(
        "Successfully uploaded ${publication.groupId}:${publication.artifactId}:${publication.version} to ${repository.name}"
      )
    }
  }
  withType<PublishToMavenLocal>().configureEach {
    doLast {
      logger.lifecycle(
        "Successfully created ${publication.groupId}:${publication.artifactId}:${publication.version} in MavenLocal"
      )
    }
  }
}

publishing {
  publications.create<MavenPublication>("revoman") {
    val subprojectJarName = tasks.jar.get().archiveBaseName.get()
    artifactId =
      if (subprojectJarName == "revoman-root") "revoman" else "revoman-$subprojectJarName"
    from(components["java"])
    pom {
      name.set(artifactId)
      description.set(project.description)
      url.set("https://git.soma.salesforce.com/CCSPayments/ReVoman")
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
      developers {
        developer {
          id.set("gopala.akshintala@salesforce.com")
          name.set("Gopal S Akshintala")
          email.set("gopala.akshintala@salesforce.com")
        }
      }
      scm {
        connection.set("scm:git:https://git.soma.salesforce.com/ccspayments/ReVoman")
        developerConnection.set("scm:git:git@git.soma.salesforce.com:ccspayments/ReVoman.git")
        url.set("https://git.soma.salesforce.com/ccspayments/revoman")
      }
    }
  }
  repositories {
    maven {
      name = "Nexus"
      val releasesRepoUrl =
        uri("https://nexus.soma.salesforce.com/nexus/content/repositories/releases")
      val snapshotsRepoUrl =
        uri("https://nexus.soma.salesforce.com/nexus/content/repositories/snapshots")
      url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
      val nexusUsername: String by project
      val nexusPassword: String by project
      credentials {
        username = nexusUsername
        password = nexusPassword
      }
    }
  }
}
