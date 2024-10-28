<div align="center">

<h1>Reverse Engineering Tools (RETools)</h1>

</div>

This project requires Java 21.

### Deobfuscator

An all-in-one program to aid decompiling, refactoring, and recompiling Java applets.

This tool is profile-driven, you should be creating a deob.toml tailored to an applet with any transformers you need for it enabled.  
Do not enable everything - techniques are different for every applet!

See real examples of profiles in [rs-deob](https://github.com/RuneWiki/rs-deob/tree/main/profiles).

#### Credits

- OpenRS2 (Graham), whose transformer design philosophy was used as a direct inspiration, and many transformers ported directly over from Kotlin.
- Zwyz, who worked on their own Java-based deobfuscator and allowed their code to be adapted here.
