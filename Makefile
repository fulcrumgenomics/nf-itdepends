config ?= compileClasspath

ifdef module
mm = :${module}:
else
mm =
endif

clean:
	rm -rf .nextflow*
	rm -rf work
	rm -rf build
	rm -rf plugins/*/build
	./gradlew clean

compile:
	./gradlew compileGroovy
	@echo "DONE `date`"

check:
	./gradlew check

deps:
	./gradlew -q ${mm}dependencies --configuration ${config}

test:
ifndef class
	./gradlew ${mm}test
else
	./gradlew ${mm}test --tests ${class}
endif

assemble:
	./gradlew assemble

buildPlugins:
	./gradlew copyPluginZip

upload:
	./gradlew upload

publish-index:
	./gradlew plugins:publishIndex
