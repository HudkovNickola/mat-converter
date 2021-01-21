## mat-converter
mat-converter

Converting mat dataset files to json files

**Build:

Build *mvn clean install -DskipTests*
*cd target*
Run *java -cp mat-to-json-1.0-SNAPSHOT.jar Main (path-to-configuration-file)*

**Dist:

Go to *dist/bin* and run *./start.sh*

All configuration on the file *dist/config.properties*

ftp://cs.stanford.edu/cs/cvgl/ObjectNet3D/ObjectNet3D_annotations.zip - 90,000+ annotations files 
*This converter processes 90,000 files in about 10 seconds*

TODO:
1. Add app.properties, with configuration +
2. Add Image and CAD copier
