.PHONY: assemble clean test install release

# Build the plugin
assemble:
	./gradlew assemble

clean:
	rm -rf .nextflow*
	rm -rf work
	rm -rf build
	./gradlew clean

# Run plugin unit tests
test:
	./gradlew test

# Install the plugin into local nextflow plugins dir
install:
	./gradlew installPlugin

# Publish the plugin to the registry
release:
	./gradlew releasePlugin
