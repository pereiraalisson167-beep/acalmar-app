# Regras extras do R8. As bibliotecas (Room, Compose, kotlinx.serialization) já trazem as suas.

# Mantém nomes de linha nos relatórios de erro.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Modelos gravados no banco e no arquivo de backup: mantidos como estão.
-keep class br.com.acalmar.agenda.data.** { *; }
