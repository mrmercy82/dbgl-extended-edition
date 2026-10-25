@echo off
if exist jre\bin\java.exe goto jre
java -Djdk.util.zip.disableZip64ExtraFieldValidation=true -jar dbgl.jar
goto end

:jre
jre\bin\java -Djdk.util.zip.disableZip64ExtraFieldValidation=true -jar dbgl.jar

:end
