// Test 1: Basic @Grab with transitive dependencies
@Grab('commons-lang:commons-lang:2.6')
import org.apache.commons.lang.StringUtils

// Test 2: Multiple @Grab annotations via @Grapes
@Grapes([
    @Grab('com.google.guava:guava:33.0.0-jre'),
    @GrabExclude('com.google.code.findbugs:jsr305')
])
import com.google.common.collect.ImmutableList

class GrabTest {
    static String capitalize(String input) {
        return StringUtils.capitalize(input)
    }

    static List<String> immutableOf(String... items) {
        return ImmutableList.copyOf(items)
    }
}
