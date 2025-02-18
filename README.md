# prefixed-maven-plugin

[![Maven Central](https://img.shields.io/maven-central/v/ru.l3r8y/prefixed-maven-plugin)](https://central.sonatype.com/artifact/ru.l3r8y/prefixed-maven-plugin)
[![License](https://img.shields.io/badge/license-MIT-blue)](https://opensource.org/license/mit)

A Maven plugin to enforce prefix-based naming conventions for classes
implementing annotated interfaces.

## Overview

This plugin scans classes implementing interfaces annotated with
`@RequirePrefix(prefix = "YourPrefix")` and ensures that their names start with the
specified prefix. If a violation is detected, the build fails, promoting
consistent code style and adherence to naming contracts.

## Features

* **Annotation-driven validation**: Uses `@RequirePrefix` to define required
  prefixes for interface implementations.
* **Build enforcement**: Fails the build if classes violate the prefix rule.
* **Customizable checks**: Configure scanning phases and excluded packages.

## Installation

Add the plugin to your `pom.xml`:

```xml

<build>
  <plugins>
    <plugin>
      <groupId>ru.l3r8y</groupId>
      <artifactId>prefixed-maven-plugin</artifactId>
      <version>${latest}</version>
      <executions>
        <execution>
          <goals>
            <goal>enforce</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

## Usage

Annotate your interface with `@RequirePrefix`:

```java
import ru.l3r8y.prefixed.annotation.RequirePrefix;

@RequirePrefix(prefix = "Logger")
public interface Logger { ...
}
```

Implement the interface with a correctly prefixed class:

```java
public class LoggerFile implements Logger { ...
} // ✅ Valid

public class FileLogger implements Logger { ...
}  // ❌Fails build
```

Run the plugin:

```bash
mvn clean verify
```

## Configuration

Configure the plugin in your `pom.xml`:

```xml

<configuration>
  <!-- Fail build on violation (default: true) -->
  <failOnError>true</failOnError>
  <!-- Your group id by default   -->
  <basePackage>{groupId}</basePackage>
</configuration>
```

## Contributing

Pull requests are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

MIT © [Ivan Ivanchuk]
