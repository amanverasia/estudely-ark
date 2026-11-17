ESTUDELY ARK
============

A free collection of useful knowledge that works with no internet: an encyclopedia,
health and first-aid books, water and food guides, repair manuals, maps of India,
school science, programming manuals, and an optional AI assistant that runs on your
own computer. Nothing in this folder needs the internet, now or later.

This pack is free. Please copy it and give it to anyone.


1. START HERE
-------------

Windows:       double-click  Start-Library.bat
Mac:           double-click  Start-Library.command
Linux:         in Terminal, in this folder, run   sh Start-Library.sh

A black window opens (leave it open) and your web browser shows the START-HERE page.
Search it, or pick a section. Close the black window to stop the library.

If your browser does not open by itself, open this address in any browser:
    http://127.0.0.1:8471/START-HERE.html

Mac: the first time, macOS may say the program "cannot be opened". Right-click (or
Control-click) Start-Library.command, choose Open, then Open again.
If it still won't open (some torrent programs and USB drives drop the "can run" setting):
open Terminal, type  sh  and a space, drag Start-Library.sh into the Terminal window,
and press Enter. This always works. The same trick works for Start-AI.sh and Verify.sh.

Windows: if you see "Windows protected your PC", click "More info", then "Run anyway".
If Windows asks about network access, "Private networks" is enough.


2. SHARE THE LIBRARY WITH PHONES AND OTHER COMPUTERS ON THE SAME WI-FI
-----------------------------------------------------------------------

Start the library in sharing mode instead:

Windows:  open a Command Prompt in this folder and run   Start-Library.bat --share
Mac/Linux: in Terminal, in this folder, run               sh Start-Library.sh --share

The window shows an address like http://192.168.1.5:8471/START-HERE.html.
Anyone on the same Wi-Fi or phone hotspot can type it into their browser. No internet
is needed, only the shared Wi-Fi.


3. WHAT IS IN EACH FOLDER
-------------------------

Core/01-General-Knowledge            Simple English Wikipedia (with pictures), CIA World Factbook
Core/02-Health-and-Sanitation        Hesperian health books (Where There Is No Doctor and more),
                                     medical encyclopedia, water, medicine and disaster guides
Core/03-Food-and-Growing             Food preparation and gardening
Core/04-Repair-and-Practical-Skills  Appropedia, knots, repair guides
Core/05-Maps                         India street maps for computers (Desktop-Map) and for
                                     Android phones (Android-Organic-Maps)
Core/06-Education-and-Books          Science simulations, maths, physics, chemistry
Core/07-Computing-and-Documentation  Programming manuals and Linux documentation
Extras/                              Optional large extras (iFixit repair guides, Energypedia)
AI/                                  Optional AI assistant (see section 5)
Shared-Readers/                      The programs that open everything, for every computer
                                     and for Android phones

You do not need every folder. Each one works on its own, together with Shared-Readers
and the files at the top of this folder.

The .zim files are archives that need a reader. Start-Library is the easy way. You can
also install the Kiwix app from Shared-Readers/kiwix-app (computers) or
Shared-Readers/android (phones) and open the .zim files directly.


4. ANDROID PHONES
-----------------

Library: install Shared-Readers/android/org.kiwix.kiwixmobile.standalone-*.apk, then copy
the .zim files you want onto the phone and open them in the Kiwix app.
(Android asks you to allow installing apps from this source. That is expected.)

Maps with search and directions: install Shared-Readers/android/OrganicMaps-*.apk.
Open it once, then close it. Copy the folder
Core/05-Maps/Android-Organic-Maps/<number>/ onto the phone into
Android/data/app.organicmaps.web/files/ so that the <number> folder sits inside it.
Open Organic Maps again. India now works offline.
(On newer phones the phone's own file app may not be allowed into Android/data.
Connect the phone to a computer with a USB cable and copy the folder from the
computer instead.)


5. AI ASSISTANT (OPTIONAL)
--------------------------

Needs the AI/Runner-and-Instructions folder and one model folder (AI/Gemma-4-E4B is the
smaller one; AI/Gemma-4-12B is larger, slower, and needs a computer with more memory).

Windows:  double-click Start-AI.bat          (or "Start-AI.bat 12b" for the larger model,
                                              "Start-AI.bat e4b gpu" to try the graphics card)
Mac:      double-click Start-AI.command
Linux:    sh Start-AI.sh   (or sh Start-AI.sh 12b)

Wait until the window says the server is listening, then open http://127.0.0.1:8472
in your browser. On a computer with 8 GB of memory, close other programs first.
Answers come a few words per second on ordinary computers.

THE AI CAN BE WRONG. For health, medicine, water safety or anything dangerous, check the
books in Core/02-Health-and-Sanitation. Do not rely on the AI alone.


6. CHECK THAT YOUR COPY IS GENUINE AND UNDAMAGED
------------------------------------------------

Windows:  double-click Verify.bat
Mac:      double-click Verify.command, or in Terminal run   sh Verify.sh
Linux:    sh Verify.sh

It checks the signature on MANIFEST.tsv and then every file you have.
"Not downloaded" just means you skipped that folder, which is fine.

The signature only proves something if the key fingerprint it prints matches the one
published outside this pack (on the project page, in the message you got the pack from,
or on a printed card). Someone who changes the pack can also change the key inside it.


7. NOTES FOR TORRENT USERS
--------------------------

You can choose which folders to download. Always include the files at the top level and
Shared-Readers.

Some torrent programs (for example Transmission) also create a ".pad" folder full of
empty files. It is only padding. You can delete it after the download finishes, but your
program will then show the torrent as incomplete. Leaving it keeps you seeding.

Please keep seeding if you can. Every seeder makes the pack harder to lose.


8. CREDITS
----------

Everything here was made by other people and projects and is shared for free.
See CREDITS.txt for who made what, and their licences.
