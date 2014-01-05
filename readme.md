APDE
====

A Processing IDE for creating and running sketches on an Android device. What follows is a highly detailed description of APDE's concepts, features, basic use, and development.

Background
----------

The acronym APDE (for **A**ndroid **P**rocessing **D**evelopment **E**nvironment) may prove to be temporary. It is kept for lack of anything better.

APDE is based entirely on the Android mode for the PDE. Before you dive too far into coding, be sure to read through the [Android Processing wiki page](http://wiki.processing.org/w/Android)... except, skip the parts about installing the SDK. You should only look for the differences in the language between "normal" mode and Android mode because these carry over APDE.

APDE runs on Android versions 2.3 Ginegerbread to the latest version (currently 4.4 KitKat). Theoretically, I could have supported earlier versions of Android, but Processing only supports 2.3+. In 2.3, the app will appear slightly different when compared with the "newer" versions (Android 3.0+, which introduced the Action Bar design pattern). This should be expected by anyone still using such a (no offense) archaic version. If I were in that situation, I would probably root my device and upgrade to at least 4.2. But the support is still there.

I have tested the editor on my Samsung Galaxy S4 running 4.3 and my Asus Nexus 7 running 4.4. I have limited ability to test on devices beyond these two, as I (and my family) don't own all that many Android devices. I have successfully tested the app in the 2.3 emulator, but it ran in to problems of its own, due to it being an emulator.

The editor requires the following permissions:
 - *Modify and delete the contents of your USB storage* - needed to store sketches in the external storage. Consequently, your device needs an external storage (most devices should have one...).
 - *Control vibration* - vibration is used for haptic feedback in rare instances within the app. If you wish to disable it, you can do so from the Settings menu (devices without a vibrator do not have to worry about this).
 - *Test access to protected storage* - this also has to do with writing to the external storage. This permission isn't actually necessary yet, but it will be needed in future versions of Android.

The editor is not necessarily designed to be used on a phone, although it is possible (I have done this). If you plan to do so, do *not* plan on using landscape orientation. I recommend either getting a hardware / bluetooth keyboard or getting a more advanced keyboard (like [Hacker's Keyboard](https://play.google.com/store/apps/details?id=org.pocketworkstation.pckeyboard&hl=en), which is free). Some keyboard shortcuts are enabled. Tablets are obviously the device of choice.

As it stands, there appear to be two existing Java IDEs capable of building Android applications on a device; they are [AIDE](http://www.android-ide.com/) and [Java-IDE-Droid](http://code.google.com/p/java-ide-droid/). There are several other applications that claim to do the same thing, but each one of these (as far as I can tell) either use an internet-based compiler (I consider this "cheating") or depend on Java-IDE-Droid to perform the compilation. Java-IDE-Droid is open source and relatively unmaintained... while AIDE can be seen as the present industry leader of mobile development platforms, comparable to Eclipse for the desktop. However, an AIDE premium key costs 10 US dollars (although)there is a restricted free version available as well). I don't consider AIDE to be competition any more than Eclipse competes with the PDE; however, it has served as a reference point throughout the development of APDE. If it weren't for AIDE's existence, then I wouldn't have attempted to create APDE for belief that it was impossible.

Features
--------

APDE strives to be a fully-featured Processing editor, using the PDE as a model. It currently includes many of the features present in the PDE, including:

 - "Run" button compiles and launches the sketch (you must enable "Install from Unknown Sources", see below)
 - Multiple files (tabs)
 - Multiple sketches accessible from the "sketchbook"
 - Internal Android Manifest file configuration (sketch permissions, orientation lock, etc.)
 - Add files to sketch's "data" folder
 - Syntax highlighting
 - Automatic saving

These are a few of the key features, but you will find that there are more in the app. There are also a couple of features that aren't yet working:

 - OpenGL... I've tried to implement this, but can't quite figure out which binaries I need to add, etc.
 - Sketch runtime console / exception error output (this is more complicated than you think it is!)... but AIDE has done it, so it must be possible...

In addition to the above unimplemented features, I plan to add the following at some point in the future (some more distant than others!):

 - Support for contributed libraries, this includes a library manager and a pre-dexer for the library JARs
 - Tools (like the Color Selector), possibly even contributed tools
 - Examples
 - Internal Documentation
 - Building sketches for release (needs custom key signing)
 - Git integration, possibly even for synchronization across the PDE and APDE if a Git client for the PDE is created
 - Support for JavaScript mode (maybe others... but "standard" mode doesn't make any sense)

Key Changes from the PDE
------------------------

Before you begin to use APDE, it is important to understand some differences between the desktop PDE and APDE.

Firstly, the sketch's name is determined by the name of the sketch folder, but the main sketch file / tab does not need to have this name. Presently, there is no concept of a "main" tab; I think that this is a beneficial re-thinking of the sketch's structure. It makes more sense because this is the way because all of the tabs are combined anyway, without regard for any "main" tab, except that the "main" tab is added first. Consequently, the default name of the initial tab is just "sketch", and it will likely stay that way the majority of simple applications. I may end up changing this functionality to mimic the PDE, though, if it is necessary purely for compatibility if I implement Git integration, for example.

The default sketch name is "sketch", unlike the PDE, which creates a new sketch name based on the date / time for every new sketch. As such, the name "sketch" is also invalid, and this sketch cannot be saved. It must be renamed first. I may change this, as well, to make it easier to use...

APDE doesn't use ANT to build sketches - instead, it uses a custom build chain. The main difference between ANT and APDE's build sequence is that APDE uses the Eclipse Java Compiler (ECJ) instead of the JDK Java compiler (JAVAC). The bild process is described in more detail below.

Basic Use
---------

When you first open APDE, it is possible to start coding right away. However, before you can run it, you must save the sketch: Open Sketch Properties (Menu > Sketch Properties). Select the "pencil" icon in the top right corner (located in the menu on 2.3). Change your sketch name to something else. Return to the sketch with the "back" button. Save the sketch (Menu > Save Sketch). You can now run the sketch. You do not need to save the sketch again (it will save automatically), but you can always invoke the save command to manually save.

To access a list of all of sketches, either press the APDE icon in the top right corner, swipe in from the left side of the screen, or select "Load Sketch" from the menu. You can select a sketch to open it. The previously open sketch will be automatically saved (unless it has not been saved yet, when you will be prompted with a dialog).

Many of the menu items are duplicated in the Sketch Properties view, although there are some differences ("Delete Sketch" is only accessible from Sketch Properties, for example).

Renaming the sketch (from Sketch Properties) will also rename the sketch as it is saved in the Sketchbook folder. This differs from the PDE's "Save As".

To run the sketch, select the "Run" button (a symbol similar to that found in the PDE). The build process will occur. Lots of technical details will get spit out in the console, while general updates will appear in the message area. If there is a basic sketch error, the message area will likely inform you about this, while the caret will jump to the offending line (in a similar fashion to the PDE). If a more advanced error is detected by ECJ, then the the message area will say something like "Build failed, check the console". In this case, you must read through the console log. ECJ's output will tell you everything that is wrong all at once... it is a good idea to learn how to interpret it. Once all of the errors have been ironed out, the sketch should run smoothly, resulting in a pop-up window detailing the sketch as an application to be installed. You may need to enable "Installation from Unknown Sources" (location varies, typically something like Settings > Applications > Development). Once the sketch is installed, you can open it. You need not build the sketch every time you wish to run it, unless you have made changes. The sketch is installed on the device like any other app. Build times vary based on your device and the size of your sketch, and are typically around five to ten seconds for me. At any point in the build, you can press the "Stop" button (next to "Run") to halt the process.

To increase the size of the console, you can long-press the message area. After the device-specfied amount of time, the message area will appear selected (and there will be a vibration, if your device has a vibrator and vibrations are enabled). At this point, you can drag the message area, resizing the code area and the console accordingly.

Technical Information
---------------------

APDE uses a custom build sequence. These are the steps used to build sketches, as can be seen from the console output:
 - Processing Preprocessor (ANTLR), basically the same as the PDE
 - AAPT, Android SDK binary that creates R.java and bundles the resources
 - ECJ, Eclipse Java Compiler, compiles resulting source files, spits out errors
 - DEX, Android SDK JAR, converts compiled .class files to Android's .dex (Dalvik EXecutable) files
 - APKBuilder, creates an APK (Android Package) file from the resources and the DEX files
 - ZipSigner, Android library that zipaligns the APK and also signs it with a debug certificate

Upon completion of this build sequence, the sketch is launched. For those interested, the build process is located in the `build()` method of `com.calsignlabs.apde.build.Build.java`.

Development
-----------

I have been actively developing APDE for the past three months (at time of writing) and may be starting to burn out. Anyone that would like a new feature, or has a bug fix, is welcome to submit a pull request. Please bear with me, I'm relatively new to using Git.

If you wish to build APDE yourself, then there are several steps you must take to set up your Eclipse environment.

I use the ADT Eclipse bundled with the SDK Tools, revision 18. I have not tried to build APDE with revision 19, although I imagine it would be possible. It's going to have to happen eventually... To download ADT, please visit the [Android Developers website](http://developer.android.com/sdk/index.html). However, if you have done Android development in Processing before, chances are that you alredy have this installed.

On top of Eclipse, I use the EGit plugin to push commits to GitHub. This isn't necessary, as you can use Git from the command line (as many hardcore Git users would probably prefer). To install EGit, please visit the [Eclipse website](http://www.eclipse.org/egit/download/). You may need to install Git as well.

APDE currently uses several libraries, but two must be added to your workspace as library projects because they require resources. They are Android-Support-V7-Appcomat (for the ActionBar on 2.3) and aFileChooser (as a file selector dialog on devices that don't have a file management app). You can view the instructions for how to install the support libraries on the [Android Developers Website](http://developer.android.com/tools/support-library/setup.html#add-library). You can download and set up aFileChooser from its [GitHub page](https://github.com/iPaulPro/aFileChooser). Of course, you must add these projects as library projects from APDE's Project Properties.

That should be all of the outstanding steps necessary for building APDE. If I have missed something, then I can update the instructions.
