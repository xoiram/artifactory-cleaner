package no.embriq.utils

import com.github.sardine.DavResource
import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
import groovyx.net.http.AuthConfig
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.auth.DigestScheme

import static groovyx.net.http.Method.DELETE
//import static groovyx.net.http.ContentType.TEXT
//@Grapes(
//        @Grab(group='com.github.lookfirst',module='sardine',version='5.0.1')
//)

class ArtifactoryClient {
    Sardine sf = SardineFactory.begin()

    def exclusion = ["embriq-parent"]

    def baseUrl = "http://artifactory.embriq.no:8080"

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

    List<DavResource> getVersionsOlderThan(Date date, String path) {
        def oldArtifacts = getArtifactsOlderThan date, path
        def matchingOldArtifacts = oldArtifacts.findAll { resource ->
                    resource.directory &&
                    resource.name.trim().matches("^(?:(\\d+)\\.)?(?:(\\d+)\\.)?(\\*|\\d+)\$")
        }
        matchingOldArtifacts
    }

    List<DavResource> getArtifactsOlderThan(Date date, String path) {
        def artifacts = getArtifacts path
        artifacts.findAll { resource ->
            resource.creation.before date
        }
    }

    void deleteArtifact(String path) {
        def url = "${baseUrl}${path}"
        exclusion.each { ex ->
            if(url.contains(ex))
                return
        }

        if(sf.exists(url)) {
            HTTPBuilder restClient = new HTTPBuilder(url)
            restClient.headers.put("Authorization", "Basic bWFyaXVzLmdyYXZkYWxAZW1icmlxLm5vOktqZW1wZUZpbnQxOTA2")
            println "Trying to delete $url"
            restClient.request(DELETE, {
                path: path
            })
        } else {
            println "file does not exist. ${url}"
        }
    }
}
