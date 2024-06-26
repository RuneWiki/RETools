<div align="center">

<h1>Reverse Engineering Tools (RETools)</h1>

</div>

This project requires Java 21.

### Deobfuscator

An all-in-one program for decompiling and refactoring all sorts of Java applets.  
It isn't mean to be limited to any specific years and is intended to be comprehensive.

Options when deobfuscating are profile-driven, you should be creating a deob.toml tailored to that applet with any transformers you need for it enabled.  
Do not enable everything - techniques are different for every applet!

There's [an example toml here](./deob.toml.example) and many real uses in [rs-deob](https://github.com/RuneWiki/rs-deob/).

#### Credits

- OpenRS2 (Graham), which the transformer design was based off and many transformers ported directly over from Kotlin.
- Zwyz, who worked on their own Java-based deobfuscator and allowed that code to live on here.
