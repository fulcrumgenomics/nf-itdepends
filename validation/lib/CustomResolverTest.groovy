// Test 4: @GrabResolver with a custom repository
@GrabResolver(name='sonatype', root='https://oss.sonatype.org/content/repositories/releases/')
@Grab('com.opencsv:opencsv:5.9')
import com.opencsv.CSVReader

class CustomResolverTest {
    static List<String[]> parseCsv(String csv) {
        def reader = new CSVReader(new StringReader(csv))
        return reader.readAll()
    }
}
