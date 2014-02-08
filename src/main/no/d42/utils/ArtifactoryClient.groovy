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

import java.util.regex.Pattern

import static groovyx.net.http.Method.DELETE

class ArtifactoryClient {
    private final Logger logger = LoggerFactory.getLogger(getClass())
    Sardine sf = SardineFactory.begin()

    long sizeOldArtifacts = 0
    final def baseUrl
    final def authheader
    Pattern exclusion
    ArtifactoryClient(String server, String port, String username, String password, String[] exclusion) {
        baseUrl = "http://$server:$port"
        authheader = "$username:$password".bytes.encodeBase64()
        if(exclusion != null) {
            String pattern = exclusion.join("|")
            this.exclusion = Pattern.compile(".*($pattern).*");
            println "Created pattern: ${this.exclusion}"
        }
    }

    Set<DavResource> getArtifacts(String path, boolean onlyVersionedDirectories) {
        HashSet<DavResource> allResources = new ArrayList<>()

        def url = "${baseUrl}/${path}"
        Set<DavResource> resources = new HashSet<>(sf.list(url, 1))

        resources.each { resource ->
            if(!("${resource.path}/" == path) && !(resource.path == path)) {
                if(exclusion != null && exclusion.matcher(resource.path).matches()) {
                    println "Ignoring excluded path $resource from path $path"
                } else {
                    if(isVersionedDirectory(resource) && onlyVersionedDirectories) {
                        println "Adding versioned dir $resource"
                        allResources.add(resource)
                    } else if(!resource.directory && !onlyVersionedDirectories) {
                        allResources.add(resource)
                    } else if(resource.directory) {
                        println "Finding children of $resource"
                        allResources.addAll(getArtifacts(resource.path, onlyVersionedDirectories))
                    }
                }
            }
        }

        allResources
    }

    List<ArtifactoryResource> getVersionsOlderThan(Date date, String path) {
        def versionedArtifacts = getArtifacts (path, true)

        println "Got versioned artifacts"
        def oldArtifacts = []
        versionedArtifacts.each{ resource ->
            long childSize = 0
            def children = getArtifacts(resource.path, false)
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

    private boolean isVersionedDirectory(DavResource resource) {
        def match = resource.name.trim().matches("^(?:(\\d+)\\.)?(?:(\\d+)\\.)?(\\*|\\d+)\$")
        resource.directory && match
    }

    void deleteArtifact(path) {
        def url = "${baseUrl}${path}"

        HTTPBuilder restClient = new HTTPBuilder(url)
        restClient.headers.put("Authorization", "Basic $authheader")
        restClient.request(DELETE, {
            path: path
        })
    }
}
