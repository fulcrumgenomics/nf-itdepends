package nextflow.itdepends

import groovy.grape.Grape
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import org.apache.ivy.Ivy

import java.lang.reflect.Field

/**
 * TraceObserverFactory that injects a working GrapeEngine into Groovy's Grape
 * singleton BEFORE the session classloader compiles lib/ Groovy files.
 *
 * Lifecycle timing:
 *   Session.init() -> createObserversV1() -> TraceObserverFactory.create(session)  <- WE RUN HERE
 *   Session.start() -> notifyFlowCreate()
 *   ScriptLoader.parse() -> session.getClassLoader() -> compiles lib/ -> @Grab fires  <- IVY NEEDED HERE
 *
 * By the time @Grab annotations are processed during lib/ compilation,
 * Grape.instance already points to our ItDependsGrapeEngine (which has Ivy
 * on its classloader), so dependency resolution works.
 */
@Slf4j
class ItDependsFactory implements TraceObserverFactory {

    @Override
    Collection<TraceObserver> create(Session session) {
        injectGrapeEngine()
        return [new ItDependsObserver()] as Collection<TraceObserver>
    }

    private void injectGrapeEngine() {
        try {
            log.debug "nf-itdepends: injecting Ivy-backed GrapeEngine"

            // Create our Ivy-backed engine (loaded by plugin CL, so Ivy is available)
            def engine = new ItDependsGrapeEngine()

            // Set Grape.instance via reflection to bypass the broken GrapeIvy loading
            Field instanceField = Grape.getDeclaredField('instance')
            instanceField.setAccessible(true)
            instanceField.set(null, engine)

            log.debug "nf-itdepends: @Grab support restored (Ivy ${getIvyVersion()})"

        } catch (Exception e) {
            log.error "nf-itdepends: failed to inject GrapeEngine — @Grab will not work", e
        }
    }

    private static String getIvyVersion() {
        try {
            return Ivy.class.package?.implementationVersion ?: Ivy.class.package?.specificationVersion ?: "2.5.x"
        } catch (Exception ignored) {
            return "2.5.x"
        }
    }
}
