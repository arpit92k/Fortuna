# Release builds are shrunk and obfuscated by R8. The libraries Fortuna uses
# (kotlinx.serialization, Compose, Vico, cryptography-kotlin) ship their own
# rules, so the app adds only what its crash reports need.

# Keep line numbers, so a crash record's code locations can be read back
# with the mapping file that each release build keeps beside it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
