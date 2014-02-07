package no.d42.utils

import com.github.sardine.DavResource

class ArtifactoryResource {
    DavResource resource
    Date youngestChild

    public String toString() {
        "$resource.path youngest child: $youngestChild"
    }
}
