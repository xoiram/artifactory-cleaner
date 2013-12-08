package no.embriq.utils

import com.github.sardine.DavResource

class ArtifactoryCleaner {
    private ArtifactoryClient artifactoryClient

    private ArtifactoryCleaner() {
        artifactoryClient = new ArtifactoryClient()
    }

    static main(args) {
        def cleaner = new ArtifactoryCleaner()
        cleaner.start args
    }

    private start(String... paths) {
        long t0 = System.currentTimeMillis()
        def months = 3
        def monthsAgo = getDateMonthsAgo months
        List<DavResource> oldArtifacts = new ArrayList<>()
        def base = "/artifactory/libs-release-local"
        paths.each { path ->
            println "Cleaning $path for artifacts older than $months months"
            oldArtifacts.addAll(artifactoryClient.getVersionsOlderThan(monthsAgo, "$base/$path"))
        }
        removeNewestArtifactOfEachId oldArtifacts
//        deleteArtifacts oldArtifacts

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
