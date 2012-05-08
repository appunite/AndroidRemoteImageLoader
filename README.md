# Run example

run this command:

    git clone <repository-url> AndroidRemoteImageLoader
    
in eclipse:
 * File -> New -> Android Project
 * Project Name: RemoteImageLoader
 * Create project form existin source
 * Location -> Browse: select "library" directory
 * Finish
 * File -> New -> Android Project
 * Project Name: RemoteImageLoaderExample
 * Create project form existin source
 * Location -> Browse: select "sample" directory
 * Finish
 * build and run RemoteImageLoaderExample

in ant:

   cd sample
   ant debug
   ant installd

# Embeding in your project

run this command:

   git submodule add <repository-url> AndroidRemoteImageLoader

in eclipse:
 * File -> New -> Android Project
 * Project Name: RemoteImageLoader
 * Create project form existin source
 * Location -> Browse: select "library" directory
 * Finish
 * Your project -> properties -> Android
 * Library -> Add..
 * select RemoteImageLoader and OK
 * do not forget to add "android.permission.INTERNET" permission to your project AndroidManifest.xml file

in ant:
 * add to your project project.properties file something like: "android.library.reference.4=../AndroidRemoteImageLoader/library"
 * ant debug
 * ant installd


