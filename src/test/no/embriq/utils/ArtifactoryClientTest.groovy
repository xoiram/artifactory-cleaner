package no.embriq.utils

class ArtifactoryClientTest extends GroovyTestCase
{
    void testStuff() {
        ArtifactoryClient artifactoryClient = new ArtifactoryClient()
        artifactoryClient.getArtifacts "no/embriq/ams/ams-persistence", 1
    }

    void testDelete() {
        ArtifactoryClient artifactoryClient = new ArtifactoryClient()
        artifactoryClient.deleteArtifact("no/embriq/ams/ams-persistence/1.0.57/")
    }
}
