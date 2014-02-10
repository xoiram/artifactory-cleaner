package no.d42.utils
@Grapes([
	@Grab(group='com.github.lookfirst',module='sardine',version='5.0.1'),
	@Grab( group='org.slf4j', module='slf4j-api', version='1.6.2' ),
	@Grab( group='org.slf4j', module='slf4j-simple', version='1.6.2' )
])
import com.github.sardine.DavResource
import groovy.util.CliBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArtifactoryCleaner {
    private ArtifactoryClient artifactoryClient
    boolean dryRun = false
    def server, username, port, exclusion
    final def repository = "/artifactory/libs-release-local"

    private ArtifactoryCleaner(server, port, username, dry, exclusion) {
        this.dryRun = dry
        this.server = server
        this.port = port
        this.username = username
        this.exclusion = exclusion
    }

    static main(args) {
        def cli = new CliBuilder(usage:'ArtifactCleaner', posix:false)
        cli.s(longOpt:'server','server to run against', required: true, args: 1)
        cli.p(longOpt:'paths','commaseparated paths to clean', required: true, args: 2)
        cli.m(longOpt:'months','all artifacts older than monts will be deleted, exception is newest of each major.minor', required: true, args: 3)
        cli.P(longOpt:'port','port on server', required: true, args: 4)
        cli.u(longOpt:'user','username', required: true, args: 5)
        cli.e(longOpt:'exclusion','commaseparated strings to exclude from deletion', required: false, args: 6)
        cli.d(longOpt:'dryrun','just do a dryrun', required: false)

        def options = cli.parse(args)
        if(options == null) {
            return
        }
        def paths = options['paths'].split(',')

        def server = options['server']
        def port = options['port']
        def months = Integer.parseInt options['months']
        def username = options['user']
        def dry = options['dryrun']
        def exclusion = null
        if(options['exclusion']) {
            exclusion = options['exclusion'].split(',')
            println "Excluding artifacts matching: $exclusion"
        }

        println "server: $server:$port paths: $paths months: $months dryrun: $dry "

        def cleaner = new ArtifactoryCleaner(server, port, username, dry, exclusion)
        cleaner.start(paths, months)
    }

    private start(String[] paths, int months) {
        long t0 = System.currentTimeMillis()
        def monthsAgo = getDateMonthsAgo months

        printf "Starting artifactory-cleaner\n"
        printf "Password:"
        def password = new String(System.console().readPassword())

        artifactoryClient = new ArtifactoryClient(server, port, username, password, exclusion)

        println "Deleting artifacts older than: $monthsAgo"
        List<ArtifactoryResource> oldArtifacts = new ArrayList<>()
        paths.each { path ->
            println "Cleaning $path for artifacts older than $months months"
            oldArtifacts.addAll(artifactoryClient.getVersionsOlderThan(monthsAgo, "$repository/$path"))
        }

        removeNewestArtifactOfEachId oldArtifacts
        logDeletedArtifacts oldArtifacts

        printSummary()

        if(!dryRun) {
            println "Continue with deletion? [yes,no]"
            def cont = System.console().readLine()
            if(cont == "yes") {
               deleteArtifacts oldArtifacts
            } else {
                println "Aborting.\n"
            }
        }

        long t1 = System.currentTimeMillis()
        printf "Done. Took ${(t1 - t0) / 1000} seconds.\n"
    }

    def logDeletedArtifacts(artifacts) {
        def filename = "deleted-artifacts.log"
        logArtifacts(artifacts, filename)
        println "Wrote artifacts that are about to be deleted to $filename"
    }

    def deleteArtifacts(List<ArtifactoryResource> artifacts) {
        artifacts.each { artifact ->
            artifactoryClient.deleteArtifact artifact.resource.path
        }
    }

    void printSummary() {
        long bytesDeleted = artifactoryClient.sizeOldArtifacts
        def mbDeleted = bytesDeleted / 1024 / 1024
        printf "Will delete approximately: %1\$,.2f Mb.\n", mbDeleted
    }

    void removeNewestArtifactOfEachId(List<ArtifactoryResource> artifacts) {
        printf "Removing newest artifact of each groupId:artifactId:major.minor version."
        Map<String, ArtifactoryResource> newestDateForArtifactId = new HashMap<>()
        artifacts.each { artifact ->
            String artifactId = getArtifactId(artifact.resource)
            def newestArtifact = newestDateForArtifactId.get artifactId
            if(newestArtifact == null || newestArtifact.youngestChild.before(artifact.youngestChild))
                newestDateForArtifactId.put artifactId, artifact
        }
        println "Spared artifacts that where older than specified age, but newest of their respective major.minor version."
        logArtifacts(newestDateForArtifactId.values(), "spared-artifacts.log")
        artifacts.removeAll newestDateForArtifactId.values()
    }

    void logArtifacts(artifacts, filename) {
        def file = new File(filename).newWriter(false)
        artifacts.sort { a,b -> a.youngestChild.compareTo b.youngestChild }.each { sortedArtifact ->
            def artifact = sortedArtifact.resource.path.replaceAll("$repository", "")
            def artifactDate = sortedArtifact.youngestChild.format("YYYY-MM-dd")
            file.writeLine("$artifactDate:$artifact")
        }
        file.close()
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
