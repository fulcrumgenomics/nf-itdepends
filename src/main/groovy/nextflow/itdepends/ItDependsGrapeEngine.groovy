package nextflow.itdepends

import groovy.grape.GrapeEngine
import groovy.util.logging.Slf4j
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.apache.ivy.util.DefaultMessageLogger
import org.apache.ivy.util.Message

/**
 * A GrapeEngine implementation backed by Apache Ivy loaded from the plugin's
 * classloader. This replaces Groovy's built-in GrapeIvy which can't work in
 * modern Nextflow because Ivy was stripped from the fat JAR.
 *
 * When set as {@code Grape.instance}, this engine handles @Grab annotations
 * during compilation of lib/ Groovy files.
 */
@Slf4j
class ItDependsGrapeEngine implements GrapeEngine {

    private final Ivy ivy
    private final ChainResolver chain
    private final File cacheDir
    private final Set<String> resolved = Collections.synchronizedSet(new HashSet<String>())

    ItDependsGrapeEngine() {
        this.cacheDir = new File(System.getProperty("user.home"), ".groovy/grapes")
        cacheDir.mkdirs()

        Message.setDefaultLogger(new DefaultMessageLogger(Message.MSG_ERR))

        def settings = new IvySettings()
        settings.setDefaultCache(cacheDir)

        // Build a chain resolver with Maven Central
        this.chain = new ChainResolver()
        chain.setName("default")

        def central = new IBiblioResolver()
        central.setM2compatible(true)
        central.setName("central")
        central.setRoot("https://repo1.maven.org/maven2/")
        chain.add(central)

        settings.addResolver(chain)
        settings.setDefaultResolver("default")

        // Register "ibiblio" resolver name — referenced by many published ivy.xml files
        def ibiblio = new IBiblioResolver()
        ibiblio.setM2compatible(true)
        ibiblio.setName("ibiblio")
        ibiblio.setRoot("https://repo1.maven.org/maven2/")
        settings.addResolver(ibiblio)

        this.ivy = Ivy.newInstance(settings)
        log.debug "nf-itdepends: Ivy engine initialized, cache=${cacheDir}"
    }

    @Override
    Object grab(String endorsedModule) {
        return grab([:], [group: 'groovy.endorsed', module: endorsedModule, version: ''])
    }

    @Override
    Object grab(Map args) {
        return grab(args, args)
    }

    @Override
    Object grab(Map args, Map... dependencies) {
        def classLoader = args.classLoader as ClassLoader
                ?: Thread.currentThread().getContextClassLoader()

        for (Map dep : dependencies) {
            def group = dep.group?.toString() ?: dep.groupId?.toString() ?: dep.organisation?.toString()
            def module = dep.module?.toString() ?: dep.artifactId?.toString()
            def version = dep.version?.toString() ?: dep.revision?.toString()

            if (!group || !module || !version) {
                continue
            }

            def key = "${group}:${module}:${version}"

            // Skip if already resolved in this session
            if (!resolved.add(key)) {
                log.debug "nf-itdepends: already resolved ${key}, skipping"
                continue
            }

            log.info "nf-itdepends: resolving ${key}"

            try {
                def uris = doResolve(group, module, version)
                for (URI uri : uris) {
                    addToClassLoader(classLoader, uri.toURL())
                }
            } catch (Exception e) {
                log.error "nf-itdepends: failed to resolve ${key}", e
            }
        }
        return null
    }

    @Override
    Map<String, Map<String, List<String>>> enumerateGrapes() {
        return Collections.emptyMap()
    }

    @Override
    URI[] resolve(Map args, Map... dependencies) {
        def result = [] as List<URI>
        for (Map dep : dependencies) {
            def group = dep.group?.toString() ?: dep.groupId?.toString()
            def module = dep.module?.toString() ?: dep.artifactId?.toString()
            def version = dep.version?.toString() ?: dep.revision?.toString()
            if (group && module && version) {
                result.addAll(doResolve(group, module, version))
            }
        }
        return result as URI[]
    }

    @Override
    URI[] resolve(Map args, List depsInfo, Map... dependencies) {
        return resolve(args, dependencies)
    }

    @Override
    Map[] listDependencies(ClassLoader classLoader) {
        return new Map[0]
    }

    @Override
    void addResolver(Map<String, Object> args) {
        def name = args.name?.toString() ?: "custom-${System.currentTimeMillis()}"
        def root = args.root?.toString()
        if (root) {
            def resolver = new IBiblioResolver()
            resolver.setM2compatible(true)
            resolver.setName(name)
            resolver.setRoot(root)
            ivy.getSettings().addResolver(resolver)
            // Also add to the default chain so it's used during resolution
            chain.add(resolver)
            log.info "nf-itdepends: added resolver '${name}' -> ${root}"
        }
    }

    /**
     * Resolve a Maven coordinate using Ivy, returning URIs to resolved JARs only
     * (filtering out sources, javadocs, and test dependencies).
     */
    private List<URI> doResolve(String group, String module, String version) {
        def callerMrid = ModuleRevisionId.newInstance("caller", "caller", "working")
        def md = DefaultModuleDescriptor.newDefaultInstance(callerMrid)
        md.addConfiguration(new Configuration("default"))

        def depMrid = ModuleRevisionId.newInstance(group, module, version)
        def dd = new DefaultDependencyDescriptor(md, depMrid, false, false, true)
        dd.addDependencyConfiguration("default", "default")
        md.addDependency(dd)

        def resolveOptions = new ResolveOptions()
        resolveOptions.setConfs(["default"] as String[])
        resolveOptions.setTransitive(true)
        resolveOptions.setDownload(true)

        def report = ivy.resolve(md, resolveOptions)

        if (report.hasError()) {
            def problems = report.allProblemMessages
            log.warn "nf-itdepends: resolution problems for ${group}:${module}:${version}: ${problems}"
        }

        def result = [] as List<URI>
        for (def artifact : report.allArtifactsReports) {
            if (artifact.localFile == null) continue

            // Only include actual JARs (skip sources, javadocs)
            def artType = artifact.type?.toString() ?: ""
            if (artType == "source" || artType == "javadoc") continue

            def fileName = artifact.localFile.name
            if (fileName.endsWith("-sources.jar") || fileName.endsWith("-javadoc.jar")) continue

            log.debug "nf-itdepends: resolved ${artifact.localFile}"
            result.add(artifact.localFile.toURI())
        }
        return result
    }

    /**
     * Add a URL to a classloader. Walks the classloader hierarchy to find
     * a GroovyClassLoader or URLClassLoader that can accept new entries.
     */
    private static void addToClassLoader(ClassLoader cl, URL url) {
        // First walk the chain to find a suitable classloader
        def current = cl
        while (current != null) {
            if (current instanceof GroovyClassLoader) {
                def path = url.toExternalForm()
                // Convert file: URLs to paths for addClasspath
                if (path.startsWith("file:")) {
                    path = new File(url.toURI()).absolutePath
                }
                ((GroovyClassLoader) current).addClasspath(path)
                log.debug "nf-itdepends: added ${url} to GroovyClassLoader"
                return
            } else if (current instanceof URLClassLoader) {
                def method = URLClassLoader.getDeclaredMethod("addURL", URL.class)
                method.setAccessible(true)
                method.invoke(current, url)
                log.debug "nf-itdepends: added ${url} to URLClassLoader"
                return
            }
            current = current.getParent()
        }

        // If no suitable classloader found, log at debug level (not warn)
        // This happens on task threads where the classloader is PlatformClassLoader;
        // it's harmless because the classes are already loaded on the main thread.
        log.debug "nf-itdepends: no mutable classloader found in chain for ${url}"
    }
}
