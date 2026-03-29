# Publishing the Java SDK to Maven Central (Sonatype)

This document captures **maintainer instructions** and **background** for shipping **`sdk-java`** to [Maven Central](https://central.sonatype.com/) under the verified namespace **`com.agentvend`**.

For a shorter checklist, see **Publish to Maven Central** in [`sdk-java/README.md`](../sdk-java/README.md).

---

## Context

### Central Publisher Portal

Sonatype’s **Central Publisher Portal** ([central.sonatype.com](https://central.sonatype.com/)) is the current primary publishing surface. You sign in there, manage **namespaces**, generate **user tokens**, and complete **validation / release** of deployments.

Legacy **OSSRH** (e.g. `oss.sonatype.org` / `s01.oss.sonatype.org`) reached **end of life**; migrated namespaces appear in the Portal. New accounts and tokens are Portal-based.

### Namespace

This project publishes with **`groupId` `com.agentvend`**. That namespace must remain **verified** in the Portal ([Namespaces](https://central.sonatype.com/publishing/namespaces)).

### Gradle plugins

**You do not need** the community **gradle-maven-publish-plugin** (e.g. Vanniktech) to publish a plain **`java-library`**. Gradle’s built-in **`maven-publish`** plus **`signing`** is sufficient.

Sonatype notes there is **no official first-party Gradle plugin** for the native Portal Publisher API yet; they list [community Gradle options](https://central.sonatype.org/publish/publish-portal-gradle/). This repo instead uses the **Portal OSSRH Staging API** compatibility layer so **`maven-publish`** can upload file-by-file, then a **finalize** step promotes the deployment into the Portal UI.

### What Maven Central requires (summary)

Official reference: [Central Repository requirements](https://central.sonatype.org/publish/requirements/).

| Requirement | Notes |
|-------------|--------|
| **Coordinates** | Valid `groupId`, `artifactId`, `version`. Release versions **must not** end in `-SNAPSHOT`. |
| **JAR + sources + Javadoc** | For non-`pom` packaging: main JAR, **`-sources.jar`**, **`-javadoc.jar`**. |
| **Checksums** | For each real artifact (POM, JARs, etc.): **`.md5`** and **`.sha1`**. Gradle’s Maven publisher emits these on upload. **`.asc` signature files** do not need checksums or signatures. |
| **GPG / PGP** | Each published file needs a **detached ASCII signature** (`.asc`). [GPG guide](https://central.sonatype.org/publish/requirements/gpg/). |
| **POM metadata** | `name`, `description`, `url`, `licenses`, `developers`, `scm` (`connection`, `developerConnection`, `url`). |

---

## How this repository implements publishing

**Project path:** `sdk-java/`  
**Coordinates:** `com.agentvend:agent-sdk` (see `build.gradle`).

- **`maven-publish`**: `MavenPublication` `mavenJava`, POM with license, developer, SCM.
- **`signing`**: signs the publication. Signing runs **only** when publishing to Sonatype’s staging repo or when running **`finalizeSonatypeCentralUpload`**, so normal **`./gradlew build`** and **`publishToMavenLocal`** do not require GPG.
- **Repository `ossrhStagingApi`**: deploy URL  
  `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`  
  per [Publish OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).
- **Authentication:** [Central Portal user token](https://central.sonatype.org/publish/generate-portal-token/) (username + generated password), **not** legacy OSSRH tokens ([auth note](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/)).
- **Task `finalizeSonatypeCentralUpload`**: depends on **`publishMavenJavaPublicationToOssrhStagingApiRepository`**, then **POST**s to Sonatype’s **manual upload** endpoint so the deployment appears under [Publishing](https://central.sonatype.com/publishing). Gradle uses **`Authorization: Bearer`** where the token is **Base64(`username:password`)** for the user token, as described in Sonatype’s manual API documentation on the same page.

**Same IP rule:** For “Maven-like” plugins (`maven-publish`), Sonatype states that the finalize POST must be made from the **same public IP** as the artifact upload—typically **one CI job** or **one local session** running upload then finalize.

---

## One-time setup

### 1. GPG key

1. Install [GnuPG](https://gnupg.org/download/).
2. Generate a key pair and passphrase ([detailed steps](https://central.sonatype.org/publish/requirements/gpg/)).
3. Upload the **public** key to a keyserver Central supports (e.g. `keyserver.ubuntu.com`, `keys.openpgp.org`, `pgp.mit.edu`).

If you use a **signing subkey** in a way that breaks Maven verification, see Sonatype’s GPG page (primary key vs signing subkey).

### 2. Portal user token

In the Portal, generate a **user token** ([instructions](https://central.sonatype.org/publish/generate-portal-token/)). Store the token username and password securely.

### 3. Gradle / CI configuration (never commit secrets)

**File:** `%USERPROFILE%\.gradle\gradle.properties` (Windows) or `~/.gradle/gradle.properties`.

```properties
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>

mavenCentralDeveloperName=Your Name
mavenCentralDeveloperEmail=you@example.com
```

**Environment variables (e.g. CI):**

| Variable | Purpose |
|----------|---------|
| `MAVEN_CENTRAL_PUBLISH_USERNAME` | Token username |
| `MAVEN_CENTRAL_PUBLISH_PASSWORD` | Token password |
| `SIGNING_KEY` | Armored private key (in-memory signing) |
| `SIGNING_PASSWORD` | Key passphrase |

Alternatively, use Gradle properties `signingKey` / `signingPassword`, or configure **`signing.keyId`**, **`signing.password`**, and a secret key file per [Gradle Signing Plugin](https://docs.gradle.org/current/userguide/signing_plugin.html).

### 4. Release version

Before publishing to Central, set **`version`** in `sdk-java/build.gradle` to a **non-SNAPSHOT** release (e.g. `1.0.0`), commit, and tag as your release process requires.

---

## Publish command

From **`sdk-java/`**:

```bash
./gradlew finalizeSonatypeCentralUpload
```

This runs the Sonatype upload, then the finalize POST.

---

## After upload

1. Open [central.sonatype.com/publishing](https://central.sonatype.com/publishing).
2. Find the deployment, run **validation**, then **publish** to Maven Central (unless you used automated publishing via `publishing_type=automatic` on the manual endpoint—see Sonatype docs).

---

## Optional Gradle properties

| Property | Default / purpose |
|----------|-------------------|
| `mavenCentralNamespace` | `com.agentvend` — segment in the manual finalize path |
| `mavenCentralPublishingType` | `user_managed` — Portal behavior after finalize (`user_managed`, `automatic`, `portal_api`; see Sonatype) |
| `sonatype.stagingApiHost` | `https://ossrh-staging-api.central.sonatype.com` |
| `sonatype.manualUploadPath` | Override if Sonatype changes the manual endpoint path; confirm against their OpenAPI / docs |

---

## POM, license, and SCM

`build.gradle` declares **Apache License, Version 2.0** and **SCM** URLs for this Git repository. If your **actual** license or canonical repo differ:

1. Update the **`pom { licenses { ... } }`** and **`scm { ... }`** blocks in `sdk-java/build.gradle`.
2. Add or update a root **`LICENSE`** file so it matches the POM.

---

## References

- [Maven Central Portal](https://central.sonatype.com/)
- [Publish requirements](https://central.sonatype.org/publish/requirements/)
- [GPG / PGP](https://central.sonatype.org/publish/requirements/gpg/)
- [Generate Portal token](https://central.sonatype.org/publish/generate-portal-token/)
- [Portal OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/) (Gradle `maven-publish`, manual finalize)
- [Publish via Gradle (community plugins)](https://central.sonatype.org/publish/publish-portal-gradle/)
- [OSSRH sunset / Portal migration](https://central.sonatype.org/pages/ossrh-eol/)
