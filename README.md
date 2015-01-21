# PatchyGBA

This is a wrapper for HackMew's [thumb.dat](http://www.pokecommunity.com/attachment.php?attachmentid=50365&d=1255215450) that adds a few new directives/improvements to the compiler:

**Download:** [patchy.jar](https://github.com/TheGag96/PatchyGBA/raw/master/bin/patchy.jar)

* a ".org [address]" directive to allow you to patch your code immediately to a specific place in ROM after compiling.
* a ".rom [filename]" directive that specifies a ROM file to patch every time (overridden if a filename is given in the program arguments).
* an addition to the ".equ" directive that lets you specify at compile-time what the value of a variable by using "<>" as the value.
* ...and possibly more to come at request!

You can run it through a few ways:

* java -jar patchy.jar                          (will prompt for patch and ROM filenames)
* java -jar patchy.jar [.asm file]              (will prompt for ROM file if not specified with .rom in patch)
* java -jar patchy.jar [.asm file] [.gba file]  (will override .rom-specified filename)

Happy hacking!