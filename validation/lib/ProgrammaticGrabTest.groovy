// Test 3: Programmatic Grape.grab() usage
class ProgrammaticGrabTest {
    static Object grabAndUse() {
        // Programmatically grab a dependency at runtime
        groovy.grape.Grape.grab(
            group: 'commons-codec',
            module: 'commons-codec',
            version: '1.17.1',
            classLoader: Thread.currentThread().getContextClassLoader()
        )

        // Use it via reflection since we can't import at compile time
        def clazz = Class.forName('org.apache.commons.codec.digest.DigestUtils')
        def method = clazz.getMethod('md5Hex', String.class)
        return method.invoke(null, 'hello')
    }
}
