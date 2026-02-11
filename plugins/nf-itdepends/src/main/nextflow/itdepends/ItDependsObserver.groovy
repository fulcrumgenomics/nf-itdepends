package nextflow.itdepends

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver

/**
 * Trace observer for nf-itdepends. Logs confirmation that Ivy injection
 * was attempted during session initialization.
 */
@Slf4j
@CompileStatic
class ItDependsObserver implements TraceObserver {

    @Override
    void onFlowCreate(Session session) {
        log.debug "nf-itdepends: @Grab support is active"
    }

    @Override
    void onFlowComplete() {
        log.debug "nf-itdepends: pipeline complete"
    }
}
