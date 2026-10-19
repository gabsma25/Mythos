# Regras do R8 para o build release.
# Readium, Room, Compose e kotlinx.serialization já trazem as próprias regras nas bibliotecas.

# ReaderPreferences é salva como JSON no DataStore; os nomes dos campos precisam ser estáveis.
-keep class com.mythos.reader.model.** { *; }
