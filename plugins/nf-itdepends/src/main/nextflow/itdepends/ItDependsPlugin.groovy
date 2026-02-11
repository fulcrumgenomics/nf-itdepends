package nextflow.itdepends

import groovy.transform.CompileStatic
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * nf-itdepends plugin entry point.
 *
 * Restores @Grab / Ivy dependency resolution for Groovy files in lib/
 * by injecting Apache Ivy into the classloader chain before lib/ compilation.
 */
@CompileStatic
class ItDependsPlugin extends BasePlugin {

    ItDependsPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }
}
