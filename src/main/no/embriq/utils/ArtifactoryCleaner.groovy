package no.embriq.utils
@Grapes([
	@Grab(group='com.github.lookfirst',module='sardine',version='5.0.1'),
	@Grab( group='org.slf4j', module='slf4j-api', version='1.6.2' ),
	@Grab( group='org.slf4j', module='slf4j-simple', version='1.6.2' )
])
import com.github.sardine.DavResource
import groovy.util.CliBuilder

class ArtifactoryCleaner {
    private ArtifactoryClient artifactoryClient

    private ArtifactoryCleaner(server, port, username, password) {
        artifactoryClient = new ArtifactoryClient(server, port, username, password)
    }

    static main(args) {
        def cli = new CliBuilder(usage:'ArtifactCleaner', posix:false)
        cli.s(longOpt:'server','server to run against', required: true, args: 1)
        cli.p(longOpt:'paths','commaseparated paths to clean', required: true, args: 2)
        cli.m(longOpt:'months','all artifacts older than monts will be deleted, exception is newest of each major.minor', required: true, args: 3)
        cli.P(longOpt:'port','port on server', required: true, args: 4)
        cli.u(longOpt:'user','username', required: true, args: 5)
        cli.pw(longOpt:'password','password', required: true, args: 6)
        def options = cli.parse(args)
        if(options == null) {
            return
        }
        def paths = options['paths'].split(',')

        def server = options['server']
        def port = options['port']
        def months = Integer.parseInt options['months']
        def username = options['user']
        def password = options['password']

        println "server: $server:$port paths: $paths months: $months"
        println "$username:$password".bytes.encodeBase64()

        def cleaner = new ArtifactoryCleaner(server, port, username, password)
        cleaner.start(paths, months)
    }

    private start(String[] paths, int months) {
        long t0 = System.currentTimeMillis()
        def monthsAgo = getDateMonthsAgo months
        List<DavResource> oldArtifacts = new ArrayList<>()
        def base = "/artifactory/libs-release-local"
        paths.each { path ->
            println "Cleaning $path for artifacts older than $months months"
            oldArtifacts.addAll(artifactoryClient.getVersionsOlderThan(monthsAgo, "$base/$path"))
        }
        removeNewestArtifactOfEachId oldArtifacts
        deleteArtifacts oldArtifacts

        long t1 = System.currentTimeMillis()
        println "Deleted old artifacts. Took ${(t1 - t0) / 1000} seconds."
    }

    def deleteArtifacts(List<DavResource> artifacts) {
        artifacts.each { artifact ->
            artifactoryClient.deleteArtifact artifact.path
        }
    }

    void removeNewestArtifactOfEachId(List<DavResource> artifacts) {
        Map<String, DavResource> newestDateForArtifactId = new HashMap<>()
        artifacts.each { artifact ->
            String artifactId = getArtifactId(artifact)
            def newestArtifact = newestDateForArtifactId.get artifactId
            if(newestArtifact == null || newestArtifact.creation.before(artifact.creation))
                newestDateForArtifactId.put artifactId, artifact
        }
        println "Spared artifacts: "
        newestDateForArtifactId.values().sort { a,b -> getArtifactId(a).compareTo getArtifactId(b) }.each { newestArtifact ->
            println "${newestArtifact.path}"
        }
        println ""
        artifacts.removeAll newestDateForArtifactId.values()
    }

    private String getArtifactId(DavResource artifact) {
        def artifactParts = artifact.path.lastIndexOf "."
        def artifactId = artifact.path.subSequence 0, artifactParts
        artifactId
    }

    private long getSizeOfArtifacts(List<DavResource> oldArtifacts) {
        long totalSize = 0
        oldArtifacts.each { artifact ->
            def size = artifact.contentLength
            if(size > 0)
                totalSize += size
        }
        totalSize
    }

    private Date getDateMonthsAgo(int months) {
        Calendar calendar = Calendar.getInstance()
        calendar.add Calendar.MONTH, -months
        calendar.getTime()
    }
}
