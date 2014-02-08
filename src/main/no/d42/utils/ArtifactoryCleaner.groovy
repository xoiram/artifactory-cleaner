package no.d42.utils
@Grapes([
	@Grab(group='com.github.lookfirst',module='sardine',version='5.0.1'),
	@Grab( group='org.slf4j', module='slf4j-api', version='1.6.2' ),
	@Grab( group='org.slf4j', module='slf4j-simple', version='1.6.2' )
])
import com.github.sardine.DavResource
import groovy.util.CliBuilder

class ArtifactoryCleaner {
    private ArtifactoryClient artifactoryClient
    boolean dryRun = false

    private ArtifactoryCleaner(server, port, username, password, dry, exclusion) {
        this.dryRun = dry
        artifactoryClient = new ArtifactoryClient(server, port, username, password, exclusion)
    }

    static main(args) {
        def cli = new CliBuilder(usage:'ArtifactCleaner', posix:false)
        cli.s(longOpt:'server','server to run against', required: true, args: 1)
        cli.p(longOpt:'paths','commaseparated paths to clean', required: true, args: 2)
        cli.m(longOpt:'months','all artifacts older than monts will be deleted, exception is newest of each major.minor', required: true, args: 3)
        cli.P(longOpt:'port','port on server', required: true, args: 4)
        cli.u(longOpt:'user','username', required: true, args: 5)
        cli.pw(longOpt:'password','password', required: true, args: 6)
        cli.d(longOpt:'dryrun','just do a dryrun', required: false)
        cli.e(longOpt:'exclusion','commaseparated strings to exclude from deletion', required: false, args: 7)

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
        def dry = options['dryrun']
        def exclusion = null
        if(options['exclusion']) {
            exclusion = options['exclusion'].split(',')
            println "Excluding artifacts matching: $exclusion"
        }

        println "server: $server:$port paths: $paths months: $months dryrun: $dry "

        def cleaner = new ArtifactoryCleaner(server, port, username, password, dry, exclusion)
        cleaner.start(paths, months)
    }

    private start(String[] paths, int months) {
        long t0 = System.currentTimeMillis()
        def monthsAgo = getDateMonthsAgo months
        println "Deleting artifacts older than: $monthsAgo"
        List<ArtifactoryResource> oldArtifacts = new ArrayList<>()
        def base = "/artifactory/libs-release-local"
        paths.each { path ->
            println "Cleaning $path for artifacts older than $months months"
            oldArtifacts.addAll(artifactoryClient.getVersionsOlderThan(monthsAgo, "$base/$path"))
        }

        removeNewestArtifactOfEachId oldArtifacts

        if(!dryRun) {
            deleteArtifacts oldArtifacts
        }

        long t1 = System.currentTimeMillis()
        long bytesDeleted = artifactoryClient.sizeOldArtifacts
        def mbDeleted = bytesDeleted / 1024 / 1024
        printf "Old artifacts deleted, took ${(t1 - t0) / 1000} seconds. Deleted: %1\$,.2f Mb.\n", mbDeleted
    }

    def deleteArtifacts(List<ArtifactoryResource> artifacts) {
        artifacts.each { artifact ->
            artifactoryClient.deleteArtifact artifact.resource.path
        }
    }

    void removeNewestArtifactOfEachId(List<ArtifactoryResource> artifacts) {
        Map<String, ArtifactoryResource> newestDateForArtifactId = new HashMap<>()
        artifacts.each { artifact ->
            String artifactId = getArtifactId(artifact.resource)
            def newestArtifact = newestDateForArtifactId.get artifactId
            if(newestArtifact == null || newestArtifact.youngestChild.before(artifact.youngestChild))
                newestDateForArtifactId.put artifactId, artifact
        }
        println "Spared artifacts that where older than specified age, but newest of their respective major.minor version: "
        newestDateForArtifactId.values().sort { a,b -> getArtifactId(a.resource).compareTo getArtifactId(b.resource) }.each { newestArtifact ->
            println "${newestArtifact}"
        }
        println ""
        artifacts.removeAll newestDateForArtifactId.values()
    }

    private String getArtifactId(DavResource artifact) {
        def artifactParts = artifact.path.lastIndexOf "."
        def artifactId = artifact.path.subSequence 0, artifactParts
        artifactId
    }

    private Date getDateMonthsAgo(int months) {
        Calendar calendar = Calendar.getInstance()
        calendar.add Calendar.MONTH, -months
        calendar.getTime()
    }
}
