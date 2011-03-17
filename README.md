flickrdl
========

FlickrDL is a command line tool for downloading flickr images, including their corresponding metadata.


Why?
=====

Yes, there are other Flickr downloaders - even ones with a GUI. But first,
I'm a command line fanboy. Second, most of the time, the other download
tools did not download the images' metadata (author, license, source URL, etc.).
This is a nuisance if you are assembling a presentation (or some other
document which will go public) and you want to give proper credits to images
licensed in the Creative Commons.


Compilation
===========

For compilation, you need a working gradle environment (get more information here: http://gradle.org/).
In order to create the standalone jar file, run

    gradle dist


Usage
=====

Invocation
----------

In order to run the program, just call

    java -jar flickrdl.jar parameters


Flickr API key
--------------

The program requires you to configure your personal flickr API key. You can
create one on the flickr homepage: http://www.flickr.com/services/api/misc.api_keys.html

After that, configure flickrdl with it using the "-a" parameter.
You have to do this only once, it will be stored in your user configuration.


Image download
--------------

In order to download images, just append the images' numbers as parameters.
The image number is part of the flickr URL:

http://www.flickr.com/photos/username/imagenumber/

Use the "-s" parameter to create a corresponding sidecar file for each image
containing its metadata.


License
=======

Copyright (C) 2011 by Stefan Schlott

Published under the GNU Public License V2 (GPL-2)
