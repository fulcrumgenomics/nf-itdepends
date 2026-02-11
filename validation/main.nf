#!/usr/bin/env nextflow

process TEST_GRAB_BASIC {
    output:
    stdout

    script:
    """
    echo "capitalize: ${GrabTest.capitalize('hello world')}"
    """
}

process TEST_GRAB_GUAVA {
    output:
    stdout

    script:
    """
    echo "immutableOf: ${GrabTest.immutableOf('a', 'b', 'c')}"
    """
}

process TEST_GRAB_PROGRAMMATIC {
    output:
    stdout

    script:
    """
    echo "md5: ${ProgrammaticGrabTest.grabAndUse()}"
    """
}

process TEST_GRAB_RESOLVER {
    output:
    stdout

    script:
    """
    echo "csv: ${CustomResolverTest.parseCsv('a,b,c')}"
    """
}

workflow {
    TEST_GRAB_BASIC() | view
    TEST_GRAB_GUAVA() | view
    TEST_GRAB_PROGRAMMATIC() | view
    TEST_GRAB_RESOLVER() | view
}
