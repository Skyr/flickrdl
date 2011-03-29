package de.ploing.flickrdl

import groovy.xml.MarkupBuilder

import com.aetrion.flickr.REST
import com.aetrion.flickr.photos.Photo
import com.aetrion.flickr.photos.PhotosInterface
import com.aetrion.flickr.photos.Size
import com.aetrion.flickr.photos.licenses.License
import com.aetrion.flickr.photos.licenses.LicensesInterface
import com.aetrion.flickr.tags.Tag
import org.computoring.gop.Parser
import java.util.prefs.Preferences
import com.aetrion.flickr.FlickrException


class FlickrDL {
    static apiKey, apiSecret, licenses

    static storeApiKey(apiKey, apiSecret) {
        def prefs = Preferences.userNodeForPackage(this)
        prefs.put('apiKey', apiKey)
        prefs.put('apiSecret', apiSecret)
        prefs.flush()
    }

    static readConfig() {
        def prefs = Preferences.userNodeForPackage(this)
        apiKey = prefs.get('apiKey', null)
        apiSecret = prefs.get('apiSecret', null)
    }

    static getLicenseMap() {
        def result = [:]
        def licensesInterface = new LicensesInterface(apiKey, apiSecret, new REST())
        licensesInterface.getInfo().each { l ->
            result.put(l.getId(), l.getName())
        }
        return result
    }

    static File findInPath(executableName) {
        def systemPath = System.getenv("PATH")
        if (!systemPath) {
            return null
        }
        def result = null
        systemPath.split(File.pathSeparator).each { path ->
            def file = new File(path, executableName)
            if (file.isFile()) {
                result = file
            }
        }
        return result
    }

    static writeSidecar(photo, filename) {
        def outFile = new FileWriter(filename)
        def writer = new PrintWriter(outFile)
        writer.println('<?xml version="1.0" encoding="UTF-8"?>')
        def xml = new MarkupBuilder(writer)
        xml.image() {
            title(photo.getTitle())
            owner(id: photo.getOwner().getId(), username: photo.getOwner().getUsername(), photo.getOwner().getRealName())
            license(id: photo.getLicense(), licenses.get(photo.getLicense()))
            source(photo.getUrl())
            tags {
                photo.getTags().each { t ->
                    tag(t.getValue())
                }
            }
        }
        outFile.close()
    }

    static downloadImage(url, filename) {
        def file = new FileOutputStream(filename)
        def out = new BufferedOutputStream(file)
        out << new URL(url).openStream()
        out.close()
        file.close()
    }

    static addMetadataWithExiftool(photo, imageFilename, path) {
        def cmd = [ path,
                "-artist=${photo.getOwner().getRealName()?:photo.getOwner().getUsername()}",
                "-UserComment=License: ${licenses.get(photo.getLicense())} Source: ${photo.getUrl()}",
                "-By-line=${photo.getOwner().getRealName()?:photo.getOwner().getUsername()}",
                "-Contact=${photo.getUrl()}",
                "-CopyrightNotice=${licenses.get(photo.getLicense())}"
        ]
        photo.getTags().each { t ->
            cmd += [ "-Keywords+=${t.getValue()}" ]
        }
        cmd += [ imageFilename ]
        def proc = cmd.execute()
        proc.waitFor()
    }

    static addMetadataWithExiv2(photo, imageFilename, path) {
        println "Adding metadata to ${imageFilename} with exiv2 ${path}"

    }

    static main(args) {
        // Parse command line
        def parser = new Parser(description: 'Tool for downloading flickr images including the corresponding metadata')
        def params, images
        parser.with {
            flag 'h', [
                    longName: 'help',
                    description: 'Show this help'
            ]
            optional 'a', [
                    longName: 'apikey',
                    default: null,
                    description: 'Set flickr api key - must be in format "apikey,apisecret"',
                    validate: { key ->
                        if (key && key.tokenize(',').size()!=2) {
                            throw new IllegalArgumentException('Flickr api key must be in format "apikey,apisecret"')
                        }
                        key
                    }
            ]
            flag 's', [
                    longName: 'sidecars',
                    description: 'Output xml sidecar files with metadata'
            ]
            remainder { numbers ->
                if(!numbers) {
                    throw new IllegalArgumentException('No image numbers given')
                }
                images = numbers
            }
        }
        try {
            params = parser.parse(args)
        } catch (Exception e) {
            System.err << parser.usage
            System.exit(1)
        }
        // Show help?
        if (params.help) {
            System.err << parser.usage
            System.exit(0)
        }
        // Process api key option
        if (params.apikey) {
            def keyparts = params.apikey.tokenize(',');
            storeApiKey(keyparts[0], keyparts[1])
        }
        // Read config
        readConfig()
        if (!apiKey || !apiSecret) {
            System.err.println('Flickr API key not set, create one here: http://www.flickr.com/services/api/misc.api_keys.html and use -a once!')
            System.exit(1)
        }
        // Read license infos from flickr
        licenses = getLicenseMap()
        // Now, process photo ids
        def photosIntf = new PhotosInterface(apiKey, apiSecret, new REST())
        images.each { id ->
            println('Getting ' + id + '...')
            try {
                def photo = photosIntf.getPhoto(id)
                def basename = 'flickr-' + photo.getOwner().getId() + '-' + id
                // Download image
                def url = null
                try { // Try to get original url
                    url = photo.getOriginalUrl()
                } catch (FlickrException e) {
                    // If unsuccessful, fall back to largest possible image url
                    try {
                        url = photo.getSmallUrl();
                        url = photo.getMediumUrl();
                        url = photo.getLargeUrl()
                    } catch (FlickrException e2) { }
                }
                def imageFilename
                if (url) {
                    imageFilename = basename + '.' + url.tokenize('.')[-1]
                    downloadImage(url, imageFilename)
                } else {
                    println('Hm, got no URL from flickr, skipping.')
                    return
                }
                // Add metadata
                def path = findInPath('exiftool.exe')?:findInPath('exiftool')
                if (path) {
                    addMetadataWithExiftool(photo, imageFilename, path)
                } else {
                    path = findInPath('exiv2.exe')?:findInPath('exiv2')
                    if (path) {
                        addMetadataWithExiv2(photo, imageFilename, path)
                    }
                }
                // Output metadata
                if (params.sidecars) {
                    writeSidecar(photo, basename + '.xml')
                }
            } catch (Exception e) {
                println('Failed downloading image ' + id + ': ' + e)
            }
        }
        println('Done.')
    }
}
