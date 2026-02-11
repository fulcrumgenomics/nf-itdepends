# nf-itdepends

A Nextflow plugin that restores `@Grab` (Groovy Grape) dependency resolution for Groovy classes in the `lib/` directory.

## Summary

Starting with Nextflow 24.04.7-edge, [Apache Ivy was removed](https://github.com/nextflow-io/nextflow/issues/5234) from the Nextflow distribution. This broke the `@Grab` annotation, which relies on Ivy to resolve and download Maven dependencies at compile time.

This plugin brings `@Grab` back by bundling Ivy and injecting a working `GrapeEngine` into Groovy's `Grape` singleton before `lib/` files are compiled.

## Get Started

### Installation

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-itdepends@VERSION'
}
```

Replace `VERSION` with the [latest release](https://github.com/fulcrumgenomics/nf-itdepends/releases).

### Requirements

- Nextflow 24.04.0 or later
- Java 17 or later

### Configuration

By default, the plugin resolves dependencies from Maven Central. Additional resolvers can be configured using `@GrabResolver` in your Groovy code:

```groovy
@GrabResolver(name='sonatype', root='https://oss.sonatype.org/content/repositories/releases/')
@Grab('some.group:some-artifact:1.0')
import some.group.SomeClass
```

Dependencies are cached in `~/.groovy/grapes/` (the standard Grape cache directory).

## Examples

Use `@Grab` in your `lib/` Groovy files as before:

```groovy
// lib/MyHelper.groovy
@Grab('commons-lang:commons-lang:2.6')
import org.apache.commons.lang.StringUtils

class MyHelper {
    static String capitalize(String input) {
        return StringUtils.capitalize(input)
    }
}
```

```groovy
// main.nf
process EXAMPLE {
    output: stdout
    script:
    """
    echo "${MyHelper.capitalize('hello world')}"
    """
}

workflow {
    EXAMPLE() | view
}
```

## How It Works

The plugin exploits a timing window in the Nextflow session lifecycle:

1. `Session.init()` creates `TraceObserverFactory` instances, calling `ItDependsFactory.create(session)`
2. The factory creates an `ItDependsGrapeEngine` — a full `GrapeEngine` implementation backed by Apache Ivy, loaded from the plugin's own classloader (which bundles Ivy as a dependency)
3. It sets `Grape.instance` to this engine via reflection
4. Later, when `ScriptLoader.parse()` compiles `lib/` Groovy files, `@Grab` annotations fire and call our engine
5. Our engine resolves dependencies from Maven Central, downloads JARs to `~/.groovy/grapes`, and adds them to the `GroovyClassLoader`

## Development

```bash
# Build the plugin
make assemble

# Run tests
make test

# Install locally for testing
make install

# Clean build artifacts
make clean
```

## License

[MIT](LICENSE)

Copyright (c) 2026 Fulcrum Genomics LLC
