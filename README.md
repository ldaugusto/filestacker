# filestacker
Really fast way to store and retrieve thousands of small files, greatly reducing I/O and fragmentation.

One can use FileStacker as a faster filesystem, a simple DB or even as key-value database.

## The past...
This lib was developed during my Master's (around '08), when I constantly had to retrieve lots of text files ranging from 1kb to 8kb, and got frustrated by retrieving speed and disk space usage due high [fragmentation](https://en.wikipedia.org/wiki/Fragmentation_(computing)#Internal_fragmentation).

So I projected the FileStacker to minimize IO operations to the minimum and with no data fragmentation, and allowing random file access. In 2008, FileStacker beated both flat and hierarchial filesystems, MySQL, SQLite and even Lucene (just to store files, course). I'm gonna look for my Master's numbers to compare the margins.

FileStacker performed so well, even for large files, that I started to store even binary data in it (like images, PDFs and even large serialized Java Objects - faster than rebuild the objects).

## ...and nowadays
Then recently I stepped in another project with millions of (usually) even smaller text files (200 bytes - 6 kb), and some (< 10%) bigger documents (~120kb) and get really frustrated by the retrieving rate. So I decided to search my old codes and... there it was :)

I done just a bit more than found the old code, extract from the larger Master's project, updated the dependencies to newer versions, ran the tests and... it worked like a spell. 

Then, I integrated FileStacker in my current project and it now runs almost twice faster, so I decided to released it here. 

## Example

	Stacker stacker = new Stacker("some/dir");
	int id = stacker.addFile(filename, fileBytes);
	byte[] data1 = stacker.searchFile(id);
	byte[] data2 = stacker.searchFile(filename);

## TODO

* Simplify: BinaryStack or TextStack (FastUTF, automatic compression and utility String methods)
* Extend the test coverage
* Write some benchmarks (vs. flat FS, tree FS, SQLite... ideas?) 
* Compression (It was made in another layer)
* Maybe a better namespace
* Some helper functions, like fill a Stacker with directory files, recursively
* Update Java 6 code to Java 8
* I saw some comment/debug strings with enconding error. Will fix.