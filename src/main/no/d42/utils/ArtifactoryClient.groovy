package no.d42.utils

import com.github.sardine.DavResource
import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.2' )
import groovyx.net.http.AuthConfig
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.auth.DigestScheme

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import static groovyx.net.http.Method.DELETE

class ArtifactoryClient {
    private final Logger logger = LoggerFactory.getLogger(getClass())
    Sardine sf = SardineFactory.begin()

    def exclusion = ["embriq-parent"]
    long sizeOldArtifacts = 0
    final def baseUrl
    final def authheader

    ArtifactoryClient(server, port, username, password) {
        baseUrl = "http://$server:$port"
        authheader = "$username:$password".bytes.encodeBase64()
    }

    List<DavResource> getArtifacts(String path) {
        def url = "${baseUrl}/${path}"
        List<DavResource> allResources = new ArrayList<>()

        List<DavResource> resources = sf.list url, 1
        resources.each { resource ->
            if(resource.path != path) {
                allResources.add resource
                if(resource.directory)
                    allResources.addAll(getArtifacts(resource.path))
            }
        }
        allResources
    }

    List<ArtifactoryResource> getVersionsOlderThan(Date date, String path) {
        def artifacts = getArtifacts path
        def versionedArtifacts = artifacts.findAll { resource ->
                resource.directory &&
                resource.name.trim().matches("^(?:(\\d+)\\.)?(?:(\\d+)\\.)?(\\*|\\d+)\$")
        }

        def oldArtifacts = []
        versionedArtifacts.each{ resource ->
            long childSize = 0
            def children = getArtifacts(resource.path)
            def youngestChild = new Date(0)
            children.each { childResource ->
                if(childResource.creation.after(youngestChild)) {
                    youngestChild = childResource.creation
                }
                childSize += childResource.contentLength
            }
            if(youngestChild.before(date)) {
                oldArtifacts.add(new ArtifactoryResource(resource: resource, youngestChild: youngestChild))
                sizeOldArtifacts += childSize
            }
        }

        oldArtifacts
    }

    void deleteArtifact(String path) {
        def url = "${baseUrl}${path}"
        exclusion.each { ex ->
            if(url.contains(ex))
                return
        }

        HTTPBuilder restClient = new HTTPBuilder(url)
        restClient.headers.put("Authorization", "Basic $authheader")
        restClient.request(DELETE, {
            path: path
        })
    }
}
